# PJarek Gym Log

A self hosted gym log built with Kotlin, Spring Boot, Thymeleaf, HTMX, and PostgreSQL. It supports user-owned workout routines with rep ranges, guided and ad hoc training sessions, set logging, workout and rest timers, exercise instructions, session history, and an admin area.

## Run with Docker Compose

Requirements: Docker Engine and the Docker Compose plugin. The included [`compose.yaml`](compose.yaml) starts the application and PostgreSQL with persistent database and exercise-image volumes.

```sh
cp .env.example .env
```

Edit `.env` and replace the database and administrator passwords before starting. To run the published GHCR image:

```sh
docker compose pull app
docker compose up -d
```

To build and run the application from source instead:

```sh
docker compose up --build -d
```

The published GHCR package is public, so pulling the image does not require GHCR authentication.

Open `http://localhost:8080` and sign in with `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`. On first start, the app imports its small bundled catalog so the exercise library is available immediately, then downloads the upstream catalog and images into the persistent `exercise_images` volume. Later checks send GitHub's ETag and download the archive only when it has changed. Exercise images are served from that local volume; viewing an exercise never fetches an image from upstream. The app also checks for updates on the configured schedule.

PostgreSQL data and cached images are stored in named volumes. Back them up before moving or replacing the deployment:

```sh
docker compose down
docker volume ls
```

Set `APP_PORT` to change the exposed web port. `APP_EXERCISE_SYNC_CRON` sets the Spring cron schedule (default: Sunday at 04:00), and `APP_EXERCISE_SYNC_ZONE` sets its time zone (default: UTC). Set `APP_EXERCISE_SYNC_ON_STARTUP=false` to skip the initial upstream check; the scheduled checks continue. `APP_EXERCISE_SYNC_URL` can point to a compatible upstream ZIP archive. To create users, sign in as an admin and open **User administration**. New accounts are invite-only. Admins can manage accounts and review or manage users' routines and workouts. Personal settings, including weight units and light, dark, or system appearance, are available under **Settings**; system mode follows the device setting.

## Local development

Requirements: Java 25 and a PostgreSQL 18 database. Start PostgreSQL, then set:

```sh
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gym
export SPRING_DATASOURCE_USERNAME=gym
export SPRING_DATASOURCE_PASSWORD=gym
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='replace-with-a-long-password'
./gradlew bootRun
```

The database schema is managed by Flyway. The application uses Spring Boot 4.1.1's BOM for runtime dependency versions; Kotlin Gradle plugins and Java toolchain are pinned separately. HTMX 4.0.0 is bundled locally under its Zero-Clause BSD license, so pages do not load it from a third-party CDN.

### Formatting

Formatting tools require Node.js 22.22.1 or newer and npm. Install the development dependencies from the repository root:

```sh
npm install
```

This installs the pre-commit hook. It formats staged HTML, JSON, JavaScript, Markdown, SQL, and YAML files while keeping unstaged edits out of the commit. Format HTML templates manually with `npm run format`, or format all supported files with `npm run format:all`.

## Tests

Run unit tests with `./gradlew test`. Run PostgreSQL-backed integration tests separately with `./gradlew integrationTest`; they start an isolated database through Testcontainers and require Docker. `./gradlew check` runs both test tasks.

## Continuous integration and releases

GitHub Actions builds the application and runs unit and integration tests as separate steps for pull requests. Pushes to `main` run the same verification before publishing a signed `linux/amd64` image to `ghcr.io/pjaroslaw/pjarek-gym` with `latest`, `main`, and short commit SHA tags. Push a semantic version tag such as `v1.0.0` to publish a signed versioned image and create a GitHub release with generated notes; stable releases also receive the `latest` tag, while prereleases do not. The weekly GHCR cleanup retains two untagged image versions. Docker Compose builds locally for the host architecture.

## Exercise data

The catalog and images come from [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db), distributed under the Unlicense. The source license is included at `src/main/resources/static/exercise-db/LICENSE.md`. The imported fields include exercise name, level, force, mechanic, equipment, primary and secondary muscles, instructions, and image paths. A compact catalog ships with the app for first-start availability; the larger upstream image archive is downloaded only when its ETag changes and is stored in the Docker `exercise_images` volume. Exercises removed upstream are hidden from new searches and plans while remaining available to existing routines and workout history.
