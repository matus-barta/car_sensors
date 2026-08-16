#!/usr/bin/env bash

set -euo pipefail

ROOT_DIRECTORY="${1:-.}"
OUTPUT_FILE="${2:-output.txt}"

# Filenames or shell patterns to exclude.
EXCLUDED_FILENAMES=(
    "output.txt"
    "$(basename "$OUTPUT_FILE")"
    ".DS_Store"
    "*.log"
    "*.tmp"
    "*.lock"
    "LICENSE"
    "pnpm-lock.yaml"
    "*.svg"
    "src/lib/server/db/generated/*.sql"
)

# Folder names or relative folder paths to exclude.
EXCLUDED_FOLDERS=(
    ".git"
    ".vscode"
    ".svelte-kit"
    "node_modules"
    "vendor"
    "dist"
    "build"
    "__pycache__"
    "target"
    "android"
    "tools"
    "ui"
    "test-results"
    "src/lib/server/db/generated/meta"
)

is_excluded_file() {
    local relative_path="$1"
    local filename="${relative_path##*/}"
    local pattern

    for pattern in "${EXCLUDED_FILENAMES[@]}"; do
        # The right-hand side is intentionally unquoted to support patterns.
        if [[ "$filename" == $pattern || "$relative_path" == $pattern ]]; then
            return 0
        fi
    done

    return 1
}

is_excluded_folder() {
    local relative_path="$1"
    local folder

    for folder in "${EXCLUDED_FOLDERS[@]}"; do
        # Match either an exact relative folder path or a folder name
        # appearing anywhere in the path.
        if [[ "/$relative_path/" == *"/$folder/"* ]]; then
            return 0
        fi
    done

    return 1
}

is_binary_file() {
    local file="$1"

    # Empty files are valid text files.
    if [[ ! -s "$file" ]]; then
        return 1
    fi

    # grep -I treats binary files as non-matching.
    # An empty regex matches any text file, including files containing
    # only whitespace or newline characters.
    if LC_ALL=C grep -Iq '' "$file"; then
        return 1  # Text file
    else
        return 0  # Binary file
    fi
}

# Convert paths to absolute paths so the output file can be reliably excluded.
ROOT_DIRECTORY="$(cd "$ROOT_DIRECTORY" && pwd)"
OUTPUT_DIRECTORY="$(dirname "$OUTPUT_FILE")"
OUTPUT_BASENAME="$(basename "$OUTPUT_FILE")"
mkdir -p "$OUTPUT_DIRECTORY"
OUTPUT_FILE="$(cd "$OUTPUT_DIRECTORY" && pwd)/$OUTPUT_BASENAME"

TEMP_OUTPUT="$(mktemp)"
trap 'rm -f "$TEMP_OUTPUT"' EXIT

file_count=0
skipped_binary_count=0

{
    printf '<repository>\n'
    printf '  <root>%s</root>\n' "$ROOT_DIRECTORY"
    printf '  <description>\n'
    printf '    This document contains text files from the repository.\n'
    printf '    Each file is enclosed in BEGIN_FILE and END_FILE markers.\n'
    printf '    Paths are relative to the repository root.\n'
    printf '  </description>\n\n'
} > "$TEMP_OUTPUT"

while IFS= read -r -d '' file; do
    relative_path="${file#"$ROOT_DIRECTORY"/}"

    if [[ "$file" == "$OUTPUT_FILE" ]]; then
        continue
    fi

    if is_excluded_file "$relative_path"; then
        continue
    fi

    if is_excluded_folder "$relative_path"; then
        continue
    fi

    if is_binary_file "$file"; then
        ((skipped_binary_count += 1))
        continue
    fi

    file_size="$(wc -c < "$file" | tr -d ' ')"
    line_count="$(wc -l < "$file" | tr -d ' ')"

    {
        printf '===== BEGIN_FILE =====\n'
        printf 'PATH: %s\n' "$relative_path"
        printf 'SIZE_BYTES: %s\n' "$file_size"
        printf 'LINE_COUNT: %s\n' "$line_count"
        printf '%s\n' '----- CONTENT -----\n'
        cat -- "$file"

        # Ensure the end marker starts on a new line.
        if [[ -s "$file" ]] && [[ "$(tail -c 1 "$file" | wc -l)" -eq 0 ]]; then
            printf '\n'
        fi

        printf '===== END_FILE: %s =====\n\n' "$relative_path"
    } >> "$TEMP_OUTPUT"

    ((file_count += 1))
done < <(find "$ROOT_DIRECTORY" -type f -print0 | sort -z)

{
    printf '<summary>\n'
    printf '  <included_files>%d</included_files>\n' "$file_count"
    printf '  <skipped_binary_files>%d</skipped_binary_files>\n'
    printf '</summary>\n' "$skipped_binary_count"
    printf '</repository>\n'
} >> "$TEMP_OUTPUT"

mv -- "$TEMP_OUTPUT" "$OUTPUT_FILE"
trap - EXIT

printf 'Created LLM-readable output: %s\n' "$OUTPUT_FILE"
printf 'Included files: %d\n' "$file_count"
printf 'Skipped binary files: %d\n' "$skipped_binary_count"