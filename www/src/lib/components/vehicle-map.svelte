<script lang="ts">
	import { PUBLIC_OSM_STYLE_URL, PUBLIC_OSM_VECTOR_TILE_URL } from '$env/static/public';

	import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent } from 'maplibre-gl';
	import type { FeatureCollection, Point } from 'geojson';

	import mapLibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';

	import LocateIcon from '@lucide/svelte/icons/locate';
	import LocateFixedIcon from '@lucide/svelte/icons/locate-fixed';

	import type { VehicleWithStatus } from '$lib/vehicles/vehicle';
	import { Button } from '$lib/components/ui/button';
	import { createOsmMapStyle } from '$lib/map/osm-map-style';
	import { isUserCameraGesture, type CameraEventOrigin } from '$lib/map/camera-gesture';

	import { tick } from 'svelte';
	import type { Attachment } from 'svelte/attachments';

	interface Props {
		vehicles?: VehicleWithStatus[];
		selectedVehicleId?: string | null;
		onVehicleSelect?: (vehicleId: string) => void;
	}

	let { vehicles = [], selectedVehicleId = null, onVehicleSelect }: Props = $props();

	let map: MapLibreMap | null = null;
	let mapLoaded = $state(false);
	let mapError = $state<string | null>(null);

	/*
	 * Whether the camera is locked to the selected vehicle. Selecting a vehicle
	 * is a request to look at it, so this starts engaged and re-engages on every
	 * new selection; moving the map by hand disengages it, which is what lets
	 * someone look somewhere else without the next position update dragging the
	 * view back. It is deliberately separate from selection: a disengaged follow
	 * still leaves the vehicle selected, its marker highlighted and its card up.
	 */
	let following = $state(true);

	/*
	 * The selection the follow state was last reconciled against. A plain
	 * variable rather than `$state`, so recording it does not re-run the effect
	 * that writes it.
	 */
	let followedVehicleId: string | null = null;

	const styleUrl =
		PUBLIC_OSM_STYLE_URL || 'https://vector.openstreetmap.org/styles/shortbread/colorful.json';

	const vectorTileUrl =
		PUBLIC_OSM_VECTOR_TILE_URL || 'https://vector.openstreetmap.org/shortbread_v1/{z}/{x}/{y}.mvt';

	const sourceId = 'vehicles';
	const markerLayerId = 'vehicle-markers';
	const selectedMarkerLayerId = 'selected-vehicle-marker';

	function hasCoordinates(vehicle: VehicleWithStatus): vehicle is VehicleWithStatus & {
		latitude: number;
		longitude: number;
	} {
		return (
			typeof vehicle.latitude === 'number' &&
			Number.isFinite(vehicle.latitude) &&
			vehicle.latitude >= -90 &&
			vehicle.latitude <= 90 &&
			typeof vehicle.longitude === 'number' &&
			Number.isFinite(vehicle.longitude) &&
			vehicle.longitude >= -180 &&
			vehicle.longitude <= 180
		);
	}

	/*
	 * There is no point offering to follow something the camera cannot reach, so
	 * the toggle stays hidden until the selected vehicle actually has a position.
	 */
	const canFollowSelectedVehicle = $derived(
		selectedVehicleId !== null &&
			vehicles.some((vehicle) => vehicle.id === selectedVehicleId && hasCoordinates(vehicle))
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

	function focusSelectedVehicle(): void {
		if (!mapLoaded || !map || !selectedVehicleId || !following) {
			return;
		}

		const selectedVehicle = vehicles.find(
			(vehicle) => vehicle.id === selectedVehicleId && hasCoordinates(vehicle)
		);

		if (!selectedVehicle || !hasCoordinates(selectedVehicle)) {
			return;
		}

		map.easeTo({
			center: [selectedVehicle.longitude, selectedVehicle.latitude],
			zoom: Math.max(map.getZoom(), 14),
			padding: {
				top: 80,
				right: 40,
				bottom: 40,
				left: 320
			},
			duration: 700,
			essential: true
		});
	}

	function fitVehicles(): void {
		if (!map) {
			return;
		}

		const locatedVehicles = vehicles.filter(hasCoordinates);

		if (locatedVehicles.length === 0) {
			return;
		}

		if (locatedVehicles.length === 1) {
			const [vehicle] = locatedVehicles;

			if (!vehicle) {
				return;
			}

			map.jumpTo({
				center: [vehicle.longitude, vehicle.latitude],
				zoom: 14
			});

			return;
		}

		const longitudes = locatedVehicles.map((vehicle) => vehicle.longitude);
		const latitudes = locatedVehicles.map((vehicle) => vehicle.latitude);

		map.fitBounds(
			[
				[Math.min(...longitudes), Math.min(...latitudes)],
				[Math.max(...longitudes), Math.max(...latitudes)]
			],
			{
				padding: 80,
				maxZoom: 14,
				duration: 0
			}
		);
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

		/*
		 * Clicking the vehicle that is already selected changes no prop, so the
		 * selection effect never sees it. Re-engage here, or a marker click would
		 * be the one obvious way back to a vehicle that does nothing.
		 */
		if (vehicleId === selectedVehicleId) {
			following = true;
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
	}

	$effect(() => {
		updateVehicleSource();
	});

	/*
	 * Re-engage follow whenever the selection changes, so that choosing a vehicle
	 * always brings the camera to it however the last one was left.
	 */
	$effect(() => {
		if (selectedVehicleId === followedVehicleId) {
			return;
		}

		followedVehicleId = selectedVehicleId;
		following = true;
	});

	$effect(() => {
		focusSelectedVehicle();
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

				const style = await createOsmMapStyle(styleUrl, vectorTileUrl);

				if (destroyed) {
					return;
				}

				map = new maplibre.Map({
					container,
					style,
					center: [17.1077, 48.1486],
					zoom: 12,
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

				async function markMapReady(): Promise<void> {
					if (!map || destroyed || mapLoaded) {
						return;
					}

					addVehicleLayers();
					fitVehicles();

					mapLoaded = true;
					mapError = null;

					await tick();

					if (!map || destroyed) {
						return;
					}

					map.resize();
					map.triggerRepaint();
					focusSelectedVehicle();
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

<div class="relative isolate size-full min-h-0 overflow-hidden bg-muted">
	<div
		{@attach attachMap}
		class="absolute inset-0 z-0 size-full"
		data-testid="vehicle-map"
		data-map-state={mapError ? 'error' : mapLoaded ? 'ready' : 'loading'}
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

	{#if mapLoaded && canFollowSelectedVehicle}
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
				aria-label="Follow the selected vehicle"
				title="Follow the selected vehicle"
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
