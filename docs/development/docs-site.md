# Documentation site

These docs are built with **MkDocs Material**, plus a hook that pulls the live OpenAPI specs
from `api-contracts/` so the [API reference](../api/auth.md) is always generated from the
contracts, never hand-copied.

## Build & serve locally

Build the image once, then serve — **from the repo root** so the OpenAPI hook can see
`api-contracts/`:

```bash
podman build -t jobhub-docs docs/
podman run --rm -p 8000:8000 -v ${PWD}:/docs jobhub-docs   # → http://localhost:8000
```

Edits to `docs/**` or to the contracts in `api-contracts/` show up on rebuild.
