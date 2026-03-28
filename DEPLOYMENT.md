# Deployment

## What is covered

This repository now includes two explicit runtime contours:

- local development bootstrap through `docker-compose.yml`
- server deployment through `docker-compose.deploy.yml`

The mobile frontend is still an Expo app. It is not deployed as a container. Instead, it consumes backend URLs through environment variables from `frontend/.env.example`.

## Local bootstrap

1. Copy `.env.local.example` to `.env.local`.
2. Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\dev-up.ps1
```

3. Start Expo from `frontend/package.json`:

```powershell
cd frontend
npm install
npm start
```

4. If needed, copy `frontend/.env.example` to `frontend/.env.local`.

## Server deploy

1. Validate the backend image locally when needed:

```bash
./infra/scripts/validate-backend-image.sh
```

Windows host:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\validate-backend-image.ps1
```

2. Build and publish the backend image through `.github/workflows/backend-image.yml` or build it manually.
3. Copy `.env.deploy.example` to `.env.deploy` on the target host and replace every secret placeholder.
4. Run:

```bash
./infra/scripts/deploy.sh .env.deploy
```

Windows host:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\deploy.ps1 -EnvFile .env.deploy
```

5. The deploy scripts now wait for `ALEX_BACKEND_HEALTHCHECK_URL` and fail the rollout if the backend never becomes healthy.

CI now also includes a dedicated backend image smoke stage in `.github/workflows/ci.yml`. It builds `backend/Dockerfile` and verifies that `/app/app.jar` exists inside the resulting image, so jar packaging regressions fail before deploy.

The deploy contour now includes a Caddy edge proxy from `infra/caddy/Caddyfile`. In production, public traffic should hit Caddy on ports `80/443`, while the backend itself is bound to `127.0.0.1:${ALEX_BACKEND_PORT}` on the host.

Monitoring is also bootstrapped in the deploy contour:

- Prometheus scrapes `backend:8080/actuator/prometheus`
- Alertmanager runs locally on the host and receives Prometheus alerts
- Grafana is provisioned automatically with a Prometheus datasource
- a starter dashboard lives in `infra/grafana/dashboards/alex-backend-overview.json`
- starter alert rules live in `infra/prometheus/alerts/backend.yml`

By default, Prometheus, Alertmanager and Grafana are bound only to localhost on the host machine.

## Remote GitHub Action deploy

There is now a manual remote rollout workflow in `.github/workflows/deploy-backend.yml`.

Required repository secrets:

- `DEPLOY_HOST`
- `DEPLOY_PORT` optional, defaults to `22`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DEPLOY_KNOWN_HOSTS`
- `DEPLOY_REGISTRY_USERNAME` optional
- `DEPLOY_REGISTRY_TOKEN` optional

Expected remote host state:

- Docker with Compose plugin is installed
- target directory exists or can be created, for example `/opt/alex`
- `.env.deploy` already exists in that directory
- host can pull the backend image from GHCR
- DNS for `ALEX_PUBLIC_HOST` points to the deployment host if you want automatic HTTPS from Caddy

## Backup and restore

There is now a volume-level backup contour for the deploy stack:

- `infra/scripts/backup.sh`
- `infra/scripts/backup.ps1`
- `infra/scripts/restore.sh`
- `infra/scripts/restore.ps1`

These scripts snapshot the named Docker volumes for:

- Postgres
- Cassandra
- MinIO
- Caddy state
- Prometheus
- Alertmanager
- Grafana

Linux host:

```bash
./infra/scripts/backup.sh .env.deploy ./backups
./infra/scripts/restore.sh .env.deploy ./backups/<timestamp>
```

Windows host:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\backup.ps1 -EnvFile .env.deploy -BackupRoot .\backups
powershell -ExecutionPolicy Bypass -File .\infra\scripts\restore.ps1 -EnvFile .env.deploy -BackupDir .\backups\<timestamp>
```

The backup path is intentionally conservative: it stops the deploy stack before snapshotting volumes and starts it again afterward. That means backup and restore operations are downtime operations.

There is also a manual remote backup workflow in `.github/workflows/backup-backend-data.yml`.

## Notes

- Local Docker runs the backend with the `local` Spring profile.
- Server Docker runs the backend with the `docker` Spring profile from `backend/src/main/resources/application-docker.yml`.
- MinIO bucket bootstrap is automatic through the `minio-init` service.
- Compose env files are intentionally not committed; keep `.env.local` and `.env.deploy` private.
- `infra/alertmanager/alertmanager.yml` defaults to a `null` receiver. Wire a real receiver there before relying on external notifications.
