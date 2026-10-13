#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
revision=1b70350c92563449576bcbbf552994b1d1ed847d
source_dir="$root/.deps/llama.cpp"
if [[ -d "$source_dir/.git" ]]; then
    [[ "$(git -C "$source_dir" rev-parse HEAD)" == "$revision" ]] || { echo 'Unexpected llama.cpp revision; preserve the checkout and investigate.' >&2; exit 1; }
    git -C "$source_dir" diff --exit-code
    git -C "$source_dir" diff --cached --exit-code
else
    [[ ! -e "$source_dir" ]] || { echo 'Dependency directory already exists without Git metadata.' >&2; exit 1; }
    mkdir -p "$root/.deps"
    git init "$source_dir"
    git -C "$source_dir" remote add origin https://github.com/ggml-org/llama.cpp.git
    git -C "$source_dir" fetch --depth 1 origin "$revision"
    git -C "$source_dir" checkout --detach FETCH_HEAD
fi
echo "llama.cpp verified at $revision"
