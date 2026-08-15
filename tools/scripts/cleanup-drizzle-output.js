import { readFile, rm } from 'node:fs/promises';

const relationsPath = new URL(
    '../src/lib/server/db/generated/relations.ts',
    import.meta.url
);

try {
    const content = await readFile(relationsPath, 'utf8');

    const hasNoRelations =
        content.includes("import {} from './schema'") &&
        !content.includes('export const');

    if (hasNoRelations) {
        await rm(relationsPath);
        console.log('Removed empty generated relations.ts');
    }
} catch (error) {
    if (
        typeof error !== 'object' ||
        error === null ||
        !('code' in error) ||
        error.code !== 'ENOENT'
    ) {
        throw error;
    }
}