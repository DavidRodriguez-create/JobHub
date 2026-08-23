"""MkDocs hook: make the api-contracts OpenAPI specs available to the docs build.

The source of truth for the specs is api-contracts/src/main/resources/openapi/*.yaml.
Instead of hand-copying them into docs/ (which drifts), this hook injects them into
the build as generated files at api/<name>.yaml, so the Swagger UI pages always render
the current contracts. Edit a contract, rebuild, and the docs update automatically.

Referenced from mkdocs.yml via:  hooks: [docs/hooks/copy_openapi.py]
Paths are resolved relative to the directory MkDocs is run from (the repo root).
"""

import os

from mkdocs.structure.files import File

OPENAPI_SRC = os.path.join("api-contracts", "src", "main", "resources", "openapi")
DEST_DIR = "api"  # specs land next to the api/*.md pages → src="<name>.yaml"


def on_files(files, config):
    src_dir = os.path.abspath(OPENAPI_SRC)
    if not os.path.isdir(src_dir):
        # Don't fail the build if contracts move; the API pages will just show no spec.
        print(f"WARNING [copy_openapi]: {src_dir} not found — skipping OpenAPI injection.")
        return files

    for name in sorted(os.listdir(src_dir)):
        if not name.endswith((".yaml", ".yml")):
            continue
        files.append(
            File.generated(
                config,
                f"{DEST_DIR}/{name}",
                abs_src_path=os.path.join(src_dir, name),
            )
        )
        print(f"INFO [copy_openapi]: injected {DEST_DIR}/{name}")
    return files
