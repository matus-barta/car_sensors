import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';

import UserAvatar from './user-avatar.svelte';

describe('UserAvatar', () => {
	it('renders initials for a user without an image', async () => {
		render(UserAvatar, {
			name: 'Alex Morgan'
		});

		await expect.element(page.getByText('AM', { exact: true })).toBeInTheDocument();
	});

	it('uses only the first two name parts', async () => {
		render(UserAvatar, {
			name: 'Alex Jordan Morgan'
		});

		await expect.element(page.getByText('AJ', { exact: true })).toBeInTheDocument();
	});

	it('renders one initial for a single name', async () => {
		render(UserAvatar, {
			name: 'Taylor'
		});

		await expect.element(page.getByText('T', { exact: true })).toBeInTheDocument();
	});

	it('renders the fallback for an empty name', async () => {
		render(UserAvatar, {
			name: ''
		});

		await expect.element(page.getByText('U', { exact: true })).toBeInTheDocument();
	});

	it('renders the supplied avatar image', async () => {
		const image =
			'data:image/svg+xml,' +
			encodeURIComponent(`
				<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32">
					<rect width="32" height="32" fill="blue" />
				</svg>
			`);

		render(UserAvatar, {
			name: 'Alex Morgan',
			image
		});

		const avatarImage = page.getByRole('img', {
			name: 'Alex Morgan'
		});

		await expect.element(avatarImage).toBeInTheDocument();
		await expect.element(avatarImage).toHaveAttribute('src', image);
	});

	it('applies a custom size class', async () => {
		const rendered = render(UserAvatar, {
			name: 'Alex Morgan',
			class: 'size-12'
		});

		const avatarRoot = rendered.container.querySelector('[data-slot="avatar"]');

		expect(avatarRoot).not.toBeNull();
		expect(avatarRoot).toHaveClass('size-12');
	});
});
