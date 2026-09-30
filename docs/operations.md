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

## Configuration

| Variable                       | Default          | Purpose                                    |
| ------------------------------ | ---------------- | ------------------------------------------ |
| `POSTGRES_USER`                | `gym`            | PostgreSQL account and database owner      |
| `POSTGRES_PASSWORD`            | Required         | PostgreSQL password                        |
| `APP_ADMIN_USERNAME`           | `admin`          | Initial administrator username             |
| `APP_ADMIN_PASSWORD`           | Required         | Initial administrator password             |
| `APP_PORT`                     | `8080`           | Host port for the web app                  |
| `APP_EXERCISE_SYNC_CRON`       | `0 0 4 * * SUN`  | Exercise catalog and image update schedule |
| `APP_EXERCISE_SYNC_ZONE`       | `UTC`            | Time zone used by the schedule             |
| `APP_EXERCISE_SYNC_ON_STARTUP` | `true`           | Whether to check upstream data at startup  |
| `APP_EXERCISE_SYNC_URL`        | Upstream ZIP URL | Catalog and image archive source           |

`APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` seed an administrator only if that username does not already exist. Changing these values does not reset an existing account's password.

## Exercise data and images

The app seeds a bundled exercise catalog if the database is empty, then synchronizes the configured upstream ZIP archive. It sends the saved ETag on later checks and downloads the archive only when the upstream changes. Exercise images are stored and served locally from the `exercise_images` volume; viewing an exercise does not fetch an image from upstream. A failed sync is logged while the current catalog and local images remain available.

## Backups and upgrades

Both named volumes contain persistent application data. Back up PostgreSQL and the `exercise_images` volume before moving or replacing a deployment. `docker compose down` stops the services while preserving volumes. Do not use `docker compose down -v` for routine upgrades or debugging; it deletes persistent data.

For a release, manually dispatch the Docker workflow from `main` with the current project version prefixed by `v` (for example, `v0.1.0`). The workflow verifies the app, publishes and signs the versioned image plus `latest`, creates a GitHub release with generated notes, and opens a signed draft PR for the next minor project version. Review and merge that version PR before the next release. After reviewing a release, pull the new image and recreate the app container. The database migration runs during application startup. Review the release notes and migration files before deployment, and keep a database backup for schema changes. See [Data and migrations](data-and-migrations.md) for the schema policy.
