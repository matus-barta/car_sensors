import { readFile, rm } from 'node:fs/promises';

const relationsPath = new URL(
    '../../www/src/lib/server/db/generated/relations.ts',
    import.meta.url
);

try {
    const content = await readFile(relationsPath, 'utf8');

    const hasEmptySchemaImport =
        /import\s*\{\s*\}\s*from\s*['"]\.\/schema['"]\s*;?/.test(content);

    const hasRelationExports = /\bexport\s+const\b/.test(content);

    if (hasEmptySchemaImport && !hasRelationExports) {
        await rm(relationsPath);
        console.info('Removed empty generated relations.ts');
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