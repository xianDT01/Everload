"""Isolated image smoke test; never uses existing containers or application volumes."""
import json
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request


def docker(*args, check=True):
    return subprocess.run(["docker", *args], check=check, capture_output=True, text=True).stdout.strip()


def main(image):
    name = "everload-smoke-" + secrets.token_hex(6)
    volumes = [name + "-data", name + "-library"]
    password = secrets.token_urlsafe(24)
    environment = {
        "JWT_SECRET": secrets.token_urlsafe(48),
        "SPRING_DATASOURCE_URL": "jdbc:h2:file:/app/data/everload-db;DB_CLOSE_ON_EXIT=FALSE",
        "APP_CONFIG_PATH": "/app/data/config.json",
        "APP_SETUP_CODE_PATH": "/app/data/setup-code",
        "APP_BACKUP_PATH": "/app/data/backups",
    }

    def start():
        args = ["run", "-d", "--name", name, "-p", "127.0.0.1::8080",
                "-v", volumes[0] + ":/app/data", "-v", volumes[1] + ":/app/nas_storage:ro"]
        for key, value in environment.items():
            args.extend(["-e", key + "=" + value])
        docker(*args, image)
        port = docker("port", name, "8080/tcp").rsplit(":", 1)[1]
        return "http://127.0.0.1:" + port

    def request(path, body=None, token=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request(base + path, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
        try:
            response = urllib.request.urlopen(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            assert response.code == expected, f"{path}: expected {expected}, got {response.code}"
            return json.loads(response.read())

    def wait():
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                if request("/actuator/health").get("status") == "UP":
                    return
            except (OSError, AssertionError):
                pass
            time.sleep(2)
        raise RuntimeError("Image did not become healthy within 180 seconds")

    try:
        base = start()
        wait()
        with urllib.request.urlopen(base + "/setup", timeout=10) as response:
            assert response.code == 200 and b"<app-root" in response.read(), "Setup frontend missing from image"
        assert docker("exec", name, "id", "-u") == "10001", "Runtime must not be root"
        assert request("/api/setup/status")["required"] is True
        code = docker("exec", name, "cat", "/app/data/setup-code")
        admin = {"code": code, "username": "betatester", "email": "beta@example.com",
                 "password": password, "confirmation": password}
        invalid = dict(admin, code="incorrect-installation-code")
        request("/api/setup/admin", invalid, expected=403)
        token = request("/api/setup/admin", admin)["token"]
        assert request("/api/setup/status")["required"] is False
        assert request("/api/user/profile", token=token)["email"] == admin["email"]
        docker("exec", name, "test", "!", "-e", "/app/data/setup-code")
        request("/api/setup/admin", admin, expected=409)
        storage = request("/api/setup/storage", token=token)
        assert storage["readable"] is True and storage["writable"] is False
        request("/api/setup/library", {"name": "Beta library", "relativePath": ""}, token)
        assert request("/api/setup/configuration", token=token)["libraries"]
        for binary in ["yt-dlp", "ffmpeg", "ffprobe", "fpcalc"]:
            docker("exec", name, "sh", "-c", 'command -v "$1" >/dev/null', "sh", binary)
        docker("exec", name, "yt-dlp", "--version")
        docker("stop", "--time", "30", name)
        docker("rm", name)
        base = start()
        wait()
        assert request("/api/setup/status")["required"] is False
        token = request("/api/auth/login", {"username": "betatester", "password": password})["token"]
        assert request("/api/user/profile", token=token)["email"] == admin["email"]
        assert request("/api/setup/configuration", token=token)["libraries"]
        request("/api/setup/admin", admin, expected=409)
        print("PASS: non-root, setup, invalid/reused code, read-only library, tools, account and library persistence")
    finally:
        docker("rm", "-f", name, check=False)
        for volume in volumes:
            docker("volume", "rm", volume, check=False)


if __name__ == "__main__":
    try:
        main(sys.argv[1] if len(sys.argv) > 1 else "everload:beta-test")
    except Exception as error:
        # Never dump subprocess arguments or request bodies: they contain ephemeral credentials.
        print("FAIL:", type(error).__name__, "-", str(error) if not isinstance(error, subprocess.CalledProcessError) else "Docker command failed", file=sys.stderr)
        sys.exit(1)
