# Local docs preview image: Material for MkDocs + the swagger-ui-tag plugin
# (the stock squidfunk image does not bundle third-party plugins).
#
# Build & serve from the REPO ROOT so api-contracts/ is visible to the OpenAPI hook:
#   podman build -t jobhub-docs docs/
#   podman run --rm -p 8000:8000 -v ${PWD}:/docs jobhub-docs   # → http://localhost:8000
FROM squidfunk/mkdocs-material:9
RUN pip install --no-cache-dir mkdocs-swagger-ui-tag
