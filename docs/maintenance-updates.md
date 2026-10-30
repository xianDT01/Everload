# Maintenance and Updates

These are three separate operations:

- **Notify:** sends an announcement. It does not schedule maintenance or start an update.
- **Prepare:** enables maintenance, then creates a backup using the backup service configuration.
  It restores the prior maintenance state if backup creation fails. Concurrent preparations
  return HTTP 409. Without a legacy update script, success means `preparedOnly: true`,
  not that new application code has been installed.
- **Deploy:** the administrator builds/pulls and replaces the container from the host.
  Check container health and application behavior before manually disabling maintenance.

Maintenance survives restarts through `/app/data/maintenance.flag` in the persisted data
volume. A failed write/delete is reported rather than claiming success. An existing flag
that cannot be read keeps maintenance enabled. Administrators retain API access; ordinary
users receive HTTP 503 with `maintenance: true` and `Retry-After: 30`. Login, logout,
session refresh and the public maintenance status remain available; registration is blocked.
Requests already in progress and administrator requests are not drained by this mode.

The web checks live status every 15 seconds while visible and on reconnection. Status
requests bypass the service worker cache. User views remount after maintenance ends.

GitHub checks compare the installed revision with the configured source, not necessarily
a newer descendant. Different commits are labelled as different revisions. Unknown/dirty
builds cannot be certified up to date. Release checks do not offer numeric version downgrades.
The GitHub token is sent only to HTTPS `api.github.com` on its standard port.

`deploy.ps1` and `deploy.sh` embed `GIT_COMMIT` and `GIT_BRANCH`, mark uncommitted source
changes as dirty, and build/restart only Everload with a health check. A manual Docker build
must pass those build arguments too; `APP_UPDATE_CHECK_URL` can override the check source.

Legacy `app.update.script` commands are bounded to 120 seconds and should not replace
their own container. Recommended future distribution is versioned container images published
by GitHub Actions to GHCR, with host-controlled deployment and rollback. This workflow is
not automatically enabled by the system panel.
