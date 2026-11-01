# Operations

## Docker Compose

`compose.yaml` runs the app and PostgreSQL. It publishes the web app on port 8080 by default and uses named volumes for the database (`postgres_data`) and downloaded exercise images (`exercise_images`). `APP_PORT` changes the host port.

Create `.env` from `.env.example`, set strong database and admin passwords, then start the stack:

```sh
docker compose pull app
docker compose up -d
```

To build the current source locally instead of using the published image:

```sh
docker compose up --build -d
```

The GHCR package is public. The app applies pending Flyway migrations at startup. Check service state and logs with `docker compose ps` and `docker compose logs -f app`.

The AI routine builder supports OpenAI and self-hosted Ollama. Set `APP_AI_PROVIDER=openai` (the default), `APP_AI_API_KEY`, and optionally `APP_AI_MODEL` (defaults to `gpt-4.1-mini`) and `APP_AI_BASE_URL` for OpenAI. For Ollama, set `APP_AI_PROVIDER=ollama`, `APP_AI_BASE_URL` to the Ollama server root (default `http://localhost:11434` when running the app directly), and `APP_AI_MODEL` to a vision-capable model installed in Ollama (defaults to `qwen3-vl:4b-instruct`); an API key is optional for deployments behind an authenticated proxy. The instruct variant is used for schema-constrained routine output. Its model download is about 3.3 GB and it needs Ollama 0.30.0 or newer. It can run on CPU, but generation will be slower; allow additional memory beyond the model file for inference and image processing.

To run Ollama with this Compose stack, set these values in `.env`:

```dotenv
APP_AI_PROVIDER=ollama
APP_AI_BASE_URL=http://ollama:11434
APP_AI_MODEL=qwen3-vl:4b-instruct
```

Then start the optional Ollama profile and download the model:

```sh
docker compose --profile ollama up -d
docker compose exec ollama ollama pull qwen3-vl:4b-instruct
```

The model files persist in the `ollama_data` volume. The Ollama API is available to the app over the Compose network and is not published on a host port. If running Ollama outside this Compose stack, set `APP_AI_BASE_URL` to an address reachable from the app container. The app sends the user's preferences and processed document contents to the selected provider. PDFs are converted to text and page images locally, and uploads are not retained. Configure the provider's own retention and data controls according to your deployment's privacy needs.

The default Compose file runs Ollama without GPU access, so it uses CPU. On a host configured with the NVIDIA Container Toolkit, add the optional GPU override when starting the stack:

```sh
docker compose -f compose.yaml -f compose.ollama-gpu.yaml --profile ollama up -d
```

This reserves the host's NVIDIA GPUs for Ollama. The override is separate so the regular Compose setup continues to work on CPU-only hosts. See [Docker's Compose GPU setup](https://docs.docker.com/compose/how-tos/gpu-support/) for host prerequisites.

On a Linux host with an AMD GPU supported by ROCm, use the AMD override instead:

```sh
docker compose -f compose.yaml -f compose.ollama-amd.yaml --profile ollama up -d
```

The AMD override selects Ollama's ROCm image and passes `/dev/kfd` and `/dev/dri` into the container. GPU acceleration depends on a compatible AMD GPU and host driver setup; check Ollama's [AMD GPU support list](https://github.com/ollama/ollama/blob/main/docs/gpu.mdx) and [Docker instructions](https://github.com/ollama/ollama/blob/main/docs/docker.mdx) before deployment.

## Configuration

| Variable                       | Default          | Purpose                                          |
| ------------------------------ | ---------------- | ------------------------------------------------ |
| `POSTGRES_USER`                | `gym`            | PostgreSQL account and database owner            |
| `POSTGRES_PASSWORD`            | Required         | PostgreSQL password                              |
| `APP_ADMIN_USERNAME`           | `admin`          | Initial administrator username                   |
| `APP_ADMIN_PASSWORD`           | Required         | Initial administrator password                   |
| `APP_PORT`                     | `8080`           | Host port for the web app                        |
| `APP_EXERCISE_SYNC_CRON`       | `0 0 4 * * SUN`  | Exercise catalog and image update schedule       |
| `APP_EXERCISE_SYNC_ZONE`       | `UTC`            | Time zone used by the schedule                   |
| `APP_EXERCISE_SYNC_ON_STARTUP` | `true`           | Whether to check upstream data at startup        |
| `APP_EXERCISE_SYNC_URL`        | Upstream ZIP URL | Catalog and image archive source                 |
| `APP_AI_PROVIDER`              | `openai`         | `openai` or `ollama` routine generation backend  |
| `APP_AI_API_KEY`               | Empty            | API key for hosted or authenticated AI providers |
| `APP_AI_BASE_URL`              | Provider default | OpenAI API base URL or Ollama server root        |
| `APP_AI_MODEL`                 | Provider default | Model name to use with the selected provider     |

`APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` seed an administrator only if that username does not already exist. Changing these values does not reset an existing account's password.

## Exercise data and images

The app seeds a bundled exercise catalog if the database is empty, then synchronizes the configured upstream ZIP archive. It sends the saved ETag on later checks and downloads the archive only when the upstream changes. Exercise images are stored and served locally from the `exercise_images` volume; viewing an exercise does not fetch an image from upstream. A failed sync is logged while the current catalog and local images remain available.

## Backups and upgrades

Named volumes contain persistent application data. Back up PostgreSQL and the `exercise_images` volume before moving or replacing a deployment. If you use the Compose Ollama service and want to preserve downloaded models, back up `ollama_data` as well. `docker compose down` stops the services while preserving volumes. Do not use `docker compose down -v` for routine upgrades or debugging; it deletes persistent data.

For a release, manually dispatch the Docker workflow from `main` with the current project version prefixed by `v` (for example, `v0.1.0`). The workflow verifies the app, publishes and signs the versioned image plus `latest`, creates a GitHub release with generated notes, and opens a signed draft PR for the next minor project version. Review and merge that version PR before the next release. After reviewing a release, pull the new image and recreate the app container. The database migration runs during application startup. Review the release notes and migration files before deployment, and keep a database backup for schema changes. See [Data and migrations](data-and-migrations.md) for the schema policy.
