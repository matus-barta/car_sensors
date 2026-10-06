<script lang="ts">
	import { env } from '$env/dynamic/public';

	import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent } from 'maplibre-gl';
	import type { FeatureCollection, Point } from 'geojson';

	import mapLibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';

	/*
	 * Imported here rather than from the global stylesheet so it is loaded with
	 * the one component that needs it. Only the path is referenced, so this
	 * does not pull the library itself into the initial bundle - that stays
	 * behind the dynamic import below.
	 */
	import 'maplibre-gl/dist/maplibre-gl.css';

	import LocateIcon from '@lucide/svelte/icons/locate';
	import LocateFixedIcon from '@lucide/svelte/icons/locate-fixed';

	import type { VehicleWithStatus } from '$lib/vehicles/vehicle';
	import { Button } from '$lib/components/ui/button';
	import { createOsmMapStyle } from '$lib/map/osm-map-style';
	import { getDefaultView, WORLD_VIEW } from '$lib/map/default-view';
	import { hasCoordinates, planCamera, VEHICLE_ZOOM } from '$lib/map/vehicle-camera';
	import { isUserCameraGesture, type CameraEventOrigin } from '$lib/map/camera-gesture';

	import { tick, untrack } from 'svelte';
	import type { Attachment } from 'svelte/attachments';

	interface Props {
		vehicles?: VehicleWithStatus[];
		selectedVehicleId?: string | null;

		// Goes up on every selection, even of what is already selected.
		selectionRequest?: number;

		onVehicleSelect?: (vehicleId: string) => void;
	}

	let {
		vehicles = [],
		selectedVehicleId = null,
		selectionRequest = 0,
		onVehicleSelect
	}: Props = $props();

	let map: MapLibreMap | null = null;
	let mapLoaded = $state(false);
	let mapError = $state<string | null>(null);

	/*
	 * Whether the camera is in the middle of a move, exposed as `data-camera`
	 * so a test can wait for an ease or a fit to finish rather than guess how
	 * long it takes.
	 */
	let cameraMoving = $state(false);

	/*
	 * Whether the camera is engaged: following the selected vehicle, or, with
	 * nothing selected, keeping every located vehicle in view. Selecting a
	 * vehicle - or going back to all of them - is a request to look at it, so
	 * this starts engaged and re-engages on every change of selection; moving
	 * the map by hand disengages it, which is what lets someone look somewhere
	 * else without the next position update dragging the view back. It is
	 * deliberately separate from selection: a disengaged follow still leaves
	 * the vehicle selected, its marker highlighted and its card up.
	 */
	let following = $state(true);

	/*
	 * The selection the follow state was last reconciled against. A plain
	 * variable rather than `$state`, so recording it does not re-run the effect
	 * that writes it.
	 */
	let followedVehicleId: string | null = null;
	let followedSelectionRequest = 0;

	/*
	 * Which located vehicles the fleet view was last framed on. A different
	 * set - a vehicle added, removed, or reporting its first position - frames
	 * the fleet again; `null` makes the next pass frame it whatever the set.
	 * A plain variable, like the one above, so recording it does not re-run
	 * the effect that reads it.
	 */
	let framedFleetKey: string | null = null;

	// Room left around the fleet when it is framed.
	const FLEET_PADDING = 80;

	/*
	 * How close to the edge of the view a vehicle may drive before the fleet is
	 * framed again. Smaller than the padding above, so a freshly framed fleet
	 * never counts as being at the edge.
	 */
	const FLEET_EDGE_MARGIN = 40;

	const NO_PADDING = { top: 0, right: 0, bottom: 0, left: 0 };

	/*
	 * The style comes from VersaTiles rather than from OpenStreetMap, whose
	 * `/styles/` path - sprites and fonts included - only answers CORS requests
	 * from its own sites and localhost. It works in development and fails on
	 * every real deployment. OpenStreetMap's tiles are open to any origin, so
	 * the tiles below still come from there.
	 */
	const styleUrl =
		env.PUBLIC_OSM_STYLE_URL || 'https://tiles.versatiles.org/assets/styles/colorful/style.json';

	const vectorTileUrl =
		env.PUBLIC_OSM_VECTOR_TILE_URL ||
		'https://vector.openstreetmap.org/shortbread_v1/{z}/{x}/{y}.mvt';

	const sourceId = 'vehicles';
	const markerLayerId = 'vehicle-markers';
	const selectedMarkerLayerId = 'selected-vehicle-marker';

	/*
	 * There is no point offering to follow something the camera cannot reach, so
	 * the toggle stays hidden until there is a position to point it at: the
	 * selected vehicle's, or, with nothing selected, any vehicle's.
	 */
	const canFollow = $derived(
		selectedVehicleId === null
			? vehicles.some(hasCoordinates)
			: vehicles.some((vehicle) => vehicle.id === selectedVehicleId && hasCoordinates(vehicle))
	);

	const followLabel = $derived(
		selectedVehicleId === null ? 'Keep all vehicles in view' : 'Follow the selected vehicle'
	);

	function createVehicleFeatureCollection(): FeatureCollection<Point> {
		return {
			type: 'FeatureCollection',
			features: vehicles.filter(hasCoordinates).map((vehicle) => ({
				type: 'Feature',
				id: vehicle.id,
				geometry: {
					type: 'Point',
					coordinates: [vehicle.longitude, vehicle.latitude]
				},
				properties: {
					id: vehicle.id,
					name: vehicle.name,
					status: vehicle.status,
					selected: vehicle.id === selectedVehicleId
				}
			}))
		};
	}

	function addVehicleLayers(): void {
		if (!map || map.getSource(sourceId)) {
			return;
		}

		map.addSource(sourceId, {
			type: 'geojson',
			data: createVehicleFeatureCollection(),
			promoteId: 'id'
		});

		/*
		 * Render the selected-vehicle ring first so the normal status marker
		 * remains visible above it.
		 */
		map.addLayer({
			id: selectedMarkerLayerId,
			type: 'circle',
			source: sourceId,
			filter: ['==', ['get', 'selected'], true],
			paint: {
				'circle-radius': ['interpolate', ['linear'], ['zoom'], 5, 10, 12, 15, 18, 19],
				'circle-color': 'rgba(255, 255, 255, 0.9)',
				'circle-stroke-color': '#4f46e5',
				'circle-stroke-width': 4,
				'circle-blur': 0.05
			}
		});

		map.addLayer({
			id: markerLayerId,
			type: 'circle',
			source: sourceId,
			paint: {
				'circle-radius': ['interpolate', ['linear'], ['zoom'], 5, 5, 12, 8, 18, 11],
				'circle-color': [
					'match',
					['get', 'status'],
					'online',
					'#10b981',
					'stale',
					'#f59e0b',
					'offline',
					'#94a3b8',
					'#94a3b8'
				],
				'circle-stroke-color': '#ffffff',
				'circle-stroke-width': 2
			}
		});

		map.on('mouseenter', markerLayerId, handleMarkerMouseEnter);
		map.on('mouseleave', markerLayerId, handleMarkerMouseLeave);
		map.on('click', markerLayerId, handleMarkerClick);
	}

	function updateVehicleSource(): void {
		if (!mapLoaded || !map) {
			return;
		}

		const source = map.getSource(sourceId);

		if (!source) {
			addVehicleLayers();
			return;
		}

		(source as GeoJSONSource).setData(createVehicleFeatureCollection());
	}

	function isNearViewEdge(vehicle: { latitude: number; longitude: number }): boolean {
		if (!map) {
			return false;
		}

		const { x, y } = map.project([vehicle.longitude, vehicle.latitude]);
		const { clientWidth: width, clientHeight: height } = map.getContainer();

		return (
			x < FLEET_EDGE_MARGIN ||
			y < FLEET_EDGE_MARGIN ||
			x > width - FLEET_EDGE_MARGIN ||
			y > height - FLEET_EDGE_MARGIN
		);
	}

	/*
	 * Following a vehicle pads the camera on the left, so the vehicle sits in
	 * the part of the map the info card does not cover. MapLibre keeps that
	 * padding for every later move - `fitBounds()` even counts it into the fit
	 * - so the fleet view, which has no card, would stay pushed to the side.
	 * It is dropped first, keeping whatever is in the middle of the screen
	 * where it is, so the move that follows starts without a jump.
	 */
	function clearCameraPadding(): void {
		if (!map) {
			return;
		}

		const { top, right, bottom, left } = map.getPadding();

		if (!top && !right && !bottom && !left) {
			return;
		}

		const { clientWidth: width, clientHeight: height } = map.getContainer();

		map.jumpTo({ center: map.unproject([width / 2, height / 2]), padding: NO_PADDING });
	}

	// Carries out what `planCamera()` decides; the decision itself is tested on its own.
	function applyCamera(): void {
		if (!mapLoaded || !map) {
			return;
		}

		const plan = planCamera({
			vehicles,
			selectedVehicleId,
			following,
			framedFleetKey,
			isNearViewEdge
		});

		switch (plan.kind) {
			case 'stay':
				return;

			case 'follow':
				map.easeTo({
					center: plan.center,
					zoom: Math.max(map.getZoom(), VEHICLE_ZOOM),
					padding: {
						top: 80,
						right: 40,
						bottom: 40,
						left: 320
					},
					duration: 700,
					essential: true
				});

				return;

			case 'frame':
				framedFleetKey = plan.fleetKey;

				clearCameraPadding();

				if (plan.view.kind === 'center') {
					map.easeTo({
						center: plan.view.center,
						zoom: plan.view.zoom,
						duration: 700,
						essential: true
					});
				} else {
					map.fitBounds(plan.view.bounds, {
						padding: FLEET_PADDING,
						maxZoom: plan.view.maxZoom,
						duration: 700,
						essential: true
					});
				}

				return;
		}
	}

	function handleMarkerMouseEnter(): void {
		if (map) {
			map.getCanvas().style.cursor = 'pointer';
		}
	}

	function handleMarkerMouseLeave(): void {
		if (map) {
			map.getCanvas().style.cursor = '';
		}
	}

	function handleMarkerClick(event: MapLayerMouseEvent): void {
		const feature = event.features?.[0];
		const vehicleId = feature?.properties?.id;

		if (typeof vehicleId !== 'string') {
			return;
		}

		onVehicleSelect?.(vehicleId);
	}

	function handleCameraMoveStart(event: CameraEventOrigin): void {
		if (!isUserCameraGesture(event)) {
			return;
		}

		following = false;
	}

	function toggleFollow(): void {
		following = !following;

		// Re-engaging is a request to see the fleet now, not at its next change.
		framedFleetKey = null;
	}

	$effect(() => {
		updateVehicleSource();
	});

	/*
	 * Re-engage on every selection, so that choosing a vehicle - or all of
	 * them - always brings the camera to it however the last one was left.
	 * That includes choosing what is already selected, which changes no
	 * selection and is exactly how someone asks to be taken back after
	 * panning away; the request count is what tells it apart.
	 */
	$effect(() => {
		if (selectedVehicleId === followedVehicleId && selectionRequest === followedSelectionRequest) {
			return;
		}

		followedVehicleId = selectedVehicleId;
		followedSelectionRequest = selectionRequest;
		following = true;
		framedFleetKey = null;

		/*
		 * Directly as well: when the camera was already engaged nothing the
		 * camera effect reads has changed, so it would not run on its own.
		 * Untracked, so this effect does not start re-running on every vehicle
		 * update through what the camera reads.
		 */
		untrack(applyCamera);
	});

	$effect(() => {
		applyCamera();
	});

	/*
	 * An attachment rather than onMount: it receives the element directly, and
	 * its teardown is tied to that element leaving the DOM. Declared as a
	 * non-reactive const and reading no props synchronously, so a vehicle
	 * update never tears the map down and rebuilds it.
	 */
	const attachMap: Attachment<HTMLDivElement> = (container) => {
		let destroyed = false;
		let resizeObserver: ResizeObserver | null = null;

		async function initializeMap(): Promise<void> {
			try {
				const maplibre = await import('maplibre-gl');

				if (destroyed) {
					return;
				}

				maplibre.setWorkerUrl(mapLibreWorkerUrl);

				/*
				 * Only a starting point: once the map has loaded, the camera moves
				 * onto the vehicles - all of them, or the selected one - so the
				 * browser-derived view is worked out only when there is nothing
				 * located to move to.
				 */
				const [style, initialView] = await Promise.all([
					createOsmMapStyle(styleUrl, vectorTileUrl),
					vehicles.some(hasCoordinates) ? WORLD_VIEW : getDefaultView()
				]);

				if (destroyed) {
					return;
				}

				map = new maplibre.Map({
					container,
					style,
					center: initialView.center,
					zoom: initialView.zoom,
					minZoom: 2,
					maxZoom: 20,
					attributionControl: {
						compact: true
					}
				});

				resizeObserver = new ResizeObserver((entries) => {
					const entry = entries[0];

					if (!entry || entry.contentRect.width === 0 || entry.contentRect.height === 0) {
						return;
					}

					map?.resize();
				});

				resizeObserver.observe(container);

				map.addControl(
					new maplibre.NavigationControl({
						showCompass: true,
						showZoom: true,
						visualizePitch: true
					}),
					'top-right'
				);

				map.addControl(
					new maplibre.ScaleControl({
						maxWidth: 120,
						unit: 'metric'
					}),
					'bottom-left'
				);

				map.on('error', (event) => {
					console.error('MapLibre resource error:', {
						error: event.error,
						sourceId: 'sourceId' in event ? event.sourceId : undefined
					});
				});

				/*
				 * `movestart` is the one that catches keyboard panning, which moves the
				 * camera without ever dragging. The four specific events catch a gesture
				 * that begins while a programmatic ease is still running, when the map is
				 * already moving and `movestart` will not fire a second time. Each only
				 * ever clears the same flag, so the overlap between them costs nothing.
				 */
				map.on('movestart', handleCameraMoveStart);
				map.on('dragstart', handleCameraMoveStart);
				map.on('zoomstart', handleCameraMoveStart);
				map.on('rotatestart', handleCameraMoveStart);
				map.on('pitchstart', handleCameraMoveStart);

				// Every move, programmatic or by hand, for `data-camera`.
				map.on('movestart', () => (cameraMoving = true));
				map.on('moveend', () => (cameraMoving = false));

				async function markMapReady(): Promise<void> {
					if (!map || destroyed || mapLoaded) {
						return;
					}

					addVehicleLayers();

					mapLoaded = true;
					mapError = null;

					await tick();

					if (!map || destroyed) {
						return;
					}

					map.resize();
					map.triggerRepaint();

					/*
					 * Again now the map has its real size: the effects above may
					 * already have framed against the one it had before.
					 */
					framedFleetKey = null;
					applyCamera();
				}

				map.once('style.load', () => {
					void markMapReady();
				});
			} catch (error) {
				console.error('Failed to initialize vehicle map:', error);

				mapError = error instanceof Error ? error.message : 'The vehicle map could not be loaded.';
			}
		}

		void initializeMap();

		return () => {
			destroyed = true;
			mapLoaded = false;
			framedFleetKey = null;

			resizeObserver?.disconnect();
			resizeObserver = null;

			if (!map) {
				return;
			}

			if (map.getLayer(markerLayerId)) {
				map.off('mouseenter', markerLayerId, handleMarkerMouseEnter);
				map.off('mouseleave', markerLayerId, handleMarkerMouseLeave);
				map.off('click', markerLayerId, handleMarkerClick);
			}

			map.remove();
			map = null;
		};
	};
</script>

<div class="vehicle-map relative isolate size-full min-h-0 overflow-hidden bg-muted">
	<div
		{@attach attachMap}
		class="absolute inset-0 z-0 size-full"
		data-testid="vehicle-map"
		data-map-state={mapError ? 'error' : mapLoaded ? 'ready' : 'loading'}
		data-camera={cameraMoving ? 'moving' : 'still'}
		aria-label="Vehicle map"
	></div>

	<div
		class={[
			'pointer-events-none absolute inset-x-0 top-4 z-20 flex justify-center transition-opacity',
			mapLoaded || mapError !== null ? 'invisible opacity-0' : 'visible opacity-100'
		]}
		aria-live="polite"
		aria-hidden={mapLoaded || mapError !== null}
		data-testid="vehicle-map-loading"
	>
		<div
			class="rounded-lg border bg-background/95 px-4 py-3 text-sm text-muted-foreground shadow-sm backdrop-blur-sm"
		>
			Loading map…
		</div>
	</div>

	{#if mapError}
		<div
			class="absolute inset-0 z-30 flex items-center justify-center bg-muted p-6"
			role="alert"
			data-testid="vehicle-map-error"
		>
			<div class="max-w-md rounded-lg border bg-background p-4 text-center shadow-sm">
				<p class="text-sm font-medium">The map could not be loaded.</p>
				<p class="mt-1 text-sm text-muted-foreground">{mapError}</p>
			</div>
		</div>
	{/if}

	{#if mapLoaded && canFollow}
		<!--
			Bottom right, above the compact attribution: the navigation control
			already owns the top right, and this is where a recenter control sits in
			most map applications.
		-->
		<div class="absolute right-2.5 bottom-12 z-20">
			<Button
				variant="outline"
				size="icon"
				class="rounded-full bg-background/95 shadow-lg backdrop-blur-sm"
				aria-pressed={following}
				aria-label={followLabel}
				title={followLabel}
				data-testid="vehicle-map-follow-toggle"
				onclick={toggleFollow}
			>
				{#if following}
					<LocateFixedIcon class="text-primary" />
				{:else}
					<LocateIcon />
				{/if}
			</Button>
		</div>
	{/if}

	{#if vehicles.filter(hasCoordinates).length === 0 && mapLoaded}
		<div
			class="pointer-events-none absolute bottom-5 left-1/2 z-20 -translate-x-1/2 rounded-lg border bg-background/95 px-4 py-3 text-sm text-muted-foreground shadow-sm backdrop-blur"
		>
			No vehicle locations are available.
		</div>
	{/if}
</div>

<style>
	/*
	 * MapLibre builds its controls imperatively, so none of that DOM carries
	 * Svelte's scoping attribute and no ordinary scoped selector can reach it.
	 * Anchoring a `:global` block under this component's own class buys
	 * containment instead: the rules cannot escape the map, and they travel
	 * with the component rather than sitting in the layout every route loads.
	 *
	 * Every value here is a theme token, so the controls follow the palette
	 * rather than carrying one of their own.
	 */
	.vehicle-map :global {
		.maplibregl-map {
			position: relative;
			width: 100%;
			height: 100%;
			overflow: hidden;
			font-family: var(--font-sans);
			color: var(--foreground);
		}

		/* Navigation control group */
		.maplibregl-ctrl-group {
			overflow: hidden;
			border: 1px solid var(--border);
			border-radius: var(--radius);
			background-color: var(--popover);
			color: var(--popover-foreground);
			box-shadow:
				0 1px 2px rgb(0 0 0 / 5%),
				0 4px 12px rgb(0 0 0 / 8%);
		}

		/* Zoom and compass buttons */
		.maplibregl-ctrl-group button {
			border: 0;
			background-color: transparent;
			color: var(--popover-foreground);
		}

		.maplibregl-ctrl-group button:hover {
			background-color: var(--accent);
			color: var(--accent-foreground);
		}

		.maplibregl-ctrl-group button:focus-visible {
			position: relative;
			z-index: 1;
			outline: 2px solid var(--ring);
			outline-offset: -2px;
		}

		.maplibregl-ctrl-group button + button {
			border-top: 1px solid var(--border);
		}

		/*
		 * MapLibre draws these icons as background images, so colour alone does
		 * not affect them. The originals suit a light theme and are inverted
		 * for a dark one below.
		 */
		.maplibregl-ctrl-icon {
			opacity: 0.8;
		}

		/* Scale */
		.maplibregl-ctrl-scale {
			border-color: var(--foreground);
			border-top: 0;
			background-color: color-mix(in oklch, var(--popover) 90%, transparent);
			color: var(--foreground);
			backdrop-filter: blur(8px);
		}

		/* Attribution panel */
		.maplibregl-ctrl-attrib {
			border: 1px solid var(--border);
			border-radius: calc(var(--radius) * 0.8);
			background-color: color-mix(in oklch, var(--popover) 94%, transparent);
			color: var(--muted-foreground);
			box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
			backdrop-filter: blur(8px);
		}

		.maplibregl-ctrl-attrib a {
			color: var(--popover-foreground);
			text-decoration: underline;
			text-underline-offset: 2px;
		}

		.maplibregl-ctrl-attrib a:hover {
			color: var(--primary);
		}

		/* Compact attribution toggle */
		.maplibregl-ctrl-attrib-button {
			background-color: var(--popover);
		}

		.maplibregl-ctrl-attrib-button:hover {
			background-color: var(--accent);
		}
	}

	/*
	 * `.dark` sits on the document element, outside this component, so these
	 * cannot live in the block above.
	 */
	:global(.dark) .vehicle-map :global {
		.maplibregl-ctrl-icon {
			filter: invert(1);
			opacity: 0.9;
		}

		.maplibregl-ctrl-attrib-button {
			filter: invert(1);
		}
	}
</style>
