# EverLoad Docker beta

Beta for testing only. Do not attach production volumes or your only copy of a
music library. Initial platform: **linux/amd64** (Intel/AMD 64-bit), not ARM.
The image must pass the tests and security review below before publication.

## Preparation status (2026-11-18)

The local amd64 image builds successfully. Its isolated smoke test passed:
non-root execution, first administrator with email, invalid/reused installation
codes, read-only library access, multimedia tools and persistence after container
recreation. Temporary test containers and volumes were removed.

The first Trivy scan reported 33 HIGH and 11 CRITICAL dependency findings.
Spring Boot, Tomcat, Jackson and springdoc were subsequently updated and retested.
Two Spring MVC findings have narrowly scoped, expiring not-applicable assessments
for rendering features absent from EverLoad; see [the security review](security-beta.md).
These are not upstream fixes and must not be treated as a clean, unfiltered scan.
All remaining fixable HIGH/CRITICAL findings block publication.

Verification with the updated dependencies: 497 backend tests (one platform-specific
skip), 378 frontend tests, production image build and isolated Docker smoke test.
The final candidate from source revision `a35d71317af7d93cf6807dc306fd49240197c9bf`
passed the repeated Docker smoke test and Trivy scan: zero unhandled fixable
HIGH/CRITICAL findings and the two reviewed Spring MVC findings shown as suppressed.
The scan covers known vulnerabilities, not every possible application defect.

## Publishing

1. In the Docker Hub account `xiandt`, create a public repository named `everload`.
2. In GitHub repository Settings > Secrets and variables > Actions, add:
   - Secret `DOCKERHUB_TOKEN`: a Docker Hub access token with read/write access.
   - Optional secret `DOCKERHUB_USERNAME` and variable `DOCKERHUB_IMAGE` override
     the defaults `xiandt` and `xiandt/everload` when publishing elsewhere.
   Never paste the token into chat, source files or a terminal command. Revoke
   it if exposed. Creating the account alone does not grant the workflow access.
3. Tag the reviewed commit `v1.0.0-beta.1` and push that tag. Alternatively, run
   Actions > Docker Hub beta with version `1.0.0-beta.1` once the workflow is on
   the default branch. Do not merge unrelated changes just to publish a beta.

Publication runs backend/frontend tests, builds an amd64 candidate, tests a fresh
installation and container recreation, then scans fixable HIGH/CRITICAL findings.
Any failed check blocks publication. It pushes the exact tested local image,
never a rebuild, and does not update `latest` or overwrite existing beta tags.
Dependency findings may require an additional update before the first release.

A maintainer already signed in with `docker login` may publish locally after the
same tests, smoke test and Trivy scan, including `.trivyignore.yaml` and suppressed
findings. Tag and push that exact tested image, not a rebuild. Local Docker login
does not configure GitHub Actions: `DOCKERHUB_TOKEN` is still required there.
Do not trigger the publishing workflow for a version already uploaded locally.

## Installing the published beta

Download `compose.beta.yml` from the source revision accompanying the image.
Create `.env.beta` alongside it:

```dotenv
EVERLOAD_IMAGE=xiandt/everload:1.0.0-beta.1
JWT_SECRET=replace-with-a-unique-random-secret
EVERLOAD_PORT=8080
```

Generate the secret with `openssl rand -base64 48`. Never reuse a real server's
configuration, installation code, API keys or database for beta testing.

```sh
docker compose --env-file .env.beta -p everload-beta -f compose.beta.yml pull
docker compose --env-file .env.beta -p everload-beta -f compose.beta.yml up -d
docker compose --env-file .env.beta -p everload-beta -f compose.beta.yml exec everload cat /app/data/setup-code
```

Open `http://localhost:8080`, create the administrator and skip the optional steps
if you do not have a NAS or integration keys. The default binding is loopback.
For LAN access, set `EVERLOAD_BIND_ADDRESS` to the host's LAN address and adjust
`CORS_ALLOWED_ORIGINS` to the URL you use. Use HTTPS through a reverse proxy before
exposing the application to the Internet; do not publish the beta directly.

## Optional library

The initial volume is empty and read-only. To test with existing music, replace
`beta-library:/app/nas_storage:ro` with a prepared host directory, for example:

```yaml
- /srv/test-music:/app/nas_storage:ro
```

The process runs as UID/GID `10001:10001`. Host bind mounts must grant that user
read/traverse permissions; writable data/backups require write permission too.
Do not grant broad permissions to an existing production folder for this beta.
Writing a host path in the web assistant does not mount it into Docker.

## Updating and reverting

Keep the same project name, Compose file and volumes. Stop the beta and preserve
a backup of its volumes before updating `EVERLOAD_IMAGE` to a new version. Then
run `pull` and `up -d` again. Do not use `down -v`: that deletes the beta's data.
Reverting an image alone is not guaranteed after database changes; restore its
matching backup as well. The beta does not automatically update itself.

Report the image tag, browser, reproduction steps and redacted error message in
GitHub Issues. Never attach a database, API key, password or installation code.

## Local verification

```sh
docker build --platform linux/amd64 --build-arg APP_VERSION=1.0.0-beta.1 -t everload:beta-test .
python3 scripts/docker-beta-smoke.py everload:beta-test
```

The smoke test creates uniquely named disposable containers and volumes and
removes only those resources. It does not contact external media providers.
