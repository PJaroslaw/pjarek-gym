# Repkeeper Gym

A self hosted gym log built with Kotlin, Spring Boot, Thymeleaf, HTMX, and PostgreSQL. It supports user-owned workout routines, guided and ad hoc training sessions, set logging, exercise instructions, session history, and an admin area.

## Run with Docker Compose

Requirements: Docker Engine and the Docker Compose plugin.

```sh
cp .env.example .env
```

Edit `.env` and replace the database and administrator passwords before starting. Then run:

```sh
docker compose up --build -d
```

Open `http://localhost:8080` and sign in with `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`. The first start imports the bundled Free Exercise DB catalog into PostgreSQL and extracts its bundled images into the persistent `exercise_images` volume. Startup does not require upstream access.

PostgreSQL data and cached images are stored in named volumes. Back them up before moving or replacing the deployment:

```sh
docker compose down
docker volume ls
```

Set `APP_PORT` to change the exposed web port. To create users, sign in as an admin and open **User administration**. New accounts are invite-only. Admins can manage accounts and review or manage users' routines and workouts. Personal settings, including weight units and light, dark, or system appearance, are available under **Settings**; system mode follows the device setting.

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

Run `./gradlew test` with Java 25 and Docker available. Unit tests run locally; integration tests start an isolated PostgreSQL database with Testcontainers.

## Exercise data

The catalog and images come from [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db), distributed under the Unlicense. The source license is included at `src/main/resources/static/exercise-db/LICENSE.md`. The imported fields include exercise name, level, force, mechanic, equipment, primary and secondary muscles, instructions, and image paths. The bundled dataset is pinned to commit `a859101d633a01c4a1a920d6a8ce41dabba0705f`; refresh `exercises.json` and `exercise-images.tar.gz` together when updating it.

The image archive is extracted once at startup into the Docker `exercise_images` volume. The archive is about 94 MB compressed.
