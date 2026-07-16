#!/usr/bin/env python3

from __future__ import annotations

import argparse
import calendar
import json
import os
import re
import secrets
import signal
import socket
import subprocess
import sys
import time
from collections.abc import Mapping
from contextlib import suppress
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[2]
STATE_ROOT = REPO_ROOT / ".gradle" / "agent-infra"
COMPOSE_FILE = REPO_ROOT / "docker-compose.yaml"
PROJECT_PREFIX = "workbench-agent-"
DEFAULT_TTL_SECONDS = 2 * 60 * 60
MIN_TTL_SECONDS = 5 * 60
MAX_TTL_SECONDS = 8 * 60 * 60

COMPACT_SERVICES = ("postgres", "valkey", "elasticsearch", "minio")
DISTRIBUTED_SERVICES = (*COMPACT_SERVICES, "redpanda", "debezium")
BASE_PORT_NAMES = (
    "postgres",
    "valkey",
    "elasticsearch",
    "minio",
    "minioConsole",
    "web",
    "frontend",
)
DISTRIBUTED_PORT_NAMES = ("redpandaKafka", "redpandaAdmin", "debezium")


class InfraError(RuntimeError):
    """Raised when a lease cannot be operated safely."""


def services_for_profile(profile: str) -> tuple[str, ...]:
    if profile == "compact":
        return COMPACT_SERVICES
    if profile == "distributed":
        return DISTRIBUTED_SERVICES
    raise InfraError(f"Unsupported Infra profile: {profile}")


def parse_ttl(value: str = "2h") -> int:
    match = re.fullmatch(r"(\d+)(m|h)", value)
    if not match:
        raise InfraError("TTL must use whole minutes or hours, for example 30m or 2h")
    amount = int(match.group(1))
    seconds = amount * (60 * 60 if match.group(2) == "h" else 60)
    if not MIN_TTL_SECONDS <= seconds <= MAX_TTL_SECONDS:
        raise InfraError("TTL must be between 5m and 8h")
    return seconds


def is_local_docker_endpoint(endpoint: str) -> bool:
    return endpoint.startswith(("unix://", "npipe://"))


def assert_docker_endpoint_allowed(endpoint: str, allow_remote: bool = False) -> None:
    if not endpoint:
        raise InfraError("Docker endpoint could not be determined")
    if not is_local_docker_endpoint(endpoint) and not allow_remote:
        raise InfraError(
            f"Refusing non-local Docker endpoint {endpoint}. Obtain user approval, "
            "then pass --allow-remote explicitly."
        )


def docker_connection_host(endpoint: str) -> str:
    if is_local_docker_endpoint(endpoint):
        return "127.0.0.1"
    hostname = urlparse(endpoint).hostname
    if not hostname:
        raise InfraError(f"Docker endpoint {endpoint} has no reachable hostname")
    return hostname


def run_captured(
    command: list[str],
    *,
    env: dict[str, str] | None = None,
) -> str:
    result = subprocess.run(
        command,
        cwd=REPO_ROOT,
        env=env or os.environ.copy(),
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise InfraError(f"{' '.join(command)} failed ({result.returncode}): {detail}")
    return result.stdout


def run_inherited(
    command: list[str],
    *,
    env: dict[str, str] | None = None,
) -> int:
    result = subprocess.run(
        command,
        cwd=REPO_ROOT,
        env=env or os.environ.copy(),
        check=False,
    )
    return result.returncode


def current_docker_endpoint(environment: Mapping[str, str] = os.environ) -> str:
    docker_host = environment.get("DOCKER_HOST")
    if docker_host:
        return docker_host
    output = run_captured(
        [
            "docker",
            "context",
            "inspect",
            "--format",
            "{{json .Endpoints.docker.Host}}",
        ],
        env=dict(environment),
    )
    return json.loads(output.strip())


def assert_docker_daemon_available() -> None:
    try:
        run_captured(["docker", "info", "--format", "{{json .ServerVersion}}"])
    except InfraError as error:
        raise InfraError(
            f"Docker daemon is unavailable for the active local context: {error}"
        ) from error


class PortReservations:
    def __init__(self, profile: str) -> None:
        names = BASE_PORT_NAMES
        if profile == "distributed":
            names = (*names, *DISTRIBUTED_PORT_NAMES)
        self._sockets: list[socket.socket] = []
        self.ports: dict[str, int] = {}
        try:
            for name in names:
                reservation = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                reservation.bind(("127.0.0.1", 0))
                reservation.listen(1)
                self._sockets.append(reservation)
                self.ports[name] = reservation.getsockname()[1]
        except Exception:
            self.release()
            raise

    def release(self) -> None:
        for reservation in self._sockets:
            reservation.close()
        self._sockets.clear()


def build_lease_environment(
    lease_id: str,
    profile: str,
    ports: dict[str, int],
    host: str = "127.0.0.1",
) -> dict[str, str]:
    backend_origin = f"http://{host}:{ports['web']}"
    environment = {
        "WORKBENCH_AGENT_INFRA_ID": lease_id,
        "WORKBENCH_AGENT_INFRA_PROFILE": profile,
        "SPRING_PROFILES_ACTIVE": "local",
        "SPRING_DATASOURCE_URL": (f"jdbc:postgresql://{host}:{ports['postgres']}/workbench"),
        "SPRING_DATASOURCE_USERNAME": "workbench",
        "SPRING_DATASOURCE_PASSWORD": "workbench",
        "SPRING_DATA_REDIS_HOST": host,
        "SPRING_DATA_REDIS_PORT": str(ports["valkey"]),
        "WORKBENCH_ELASTICSEARCH_URL": f"http://{host}:{ports['elasticsearch']}",
        "WORKBENCH_STORAGE_ENDPOINT": f"http://{host}:{ports['minio']}",
        "WORKBENCH_STORAGE_ACCESS_KEY": "workbench",
        "WORKBENCH_STORAGE_SECRET_KEY": "workbench",
        "WORKBENCH_STORAGE_BUCKET": "workbench-attachments",
        "WORKBENCH_STORAGE_REGION": "us-east-1",
        "WORKBENCH_STORAGE_AUTO_CREATE_BUCKET": "true",
        "WORKBENCH_STORAGE_PATH_STYLE_ACCESS": "true",
        "WORKBENCH_MESSAGING_TRANSPORT": ("kafka" if profile == "distributed" else "postgres"),
        "SERVER_PORT": str(ports["web"]),
        "WORKBENCH_FRONTEND_PORT": str(ports["frontend"]),
        "WORKBENCH_BACKEND_ORIGIN": backend_origin,
        "OPENAPI_INPUT": f"{backend_origin}/api/openapi",
        "E2E_POSTGRES_HOST": host,
        "E2E_POSTGRES_PORT": str(ports["postgres"]),
        "E2E_VALKEY_HOST": host,
        "E2E_VALKEY_PORT": str(ports["valkey"]),
        "E2E_ELASTICSEARCH_URL": f"http://{host}:{ports['elasticsearch']}",
        "E2E_MINIO_ENDPOINT": f"http://{host}:{ports['minio']}",
    }
    if profile == "distributed":
        environment.update(
            {
                "SPRING_KAFKA_BOOTSTRAP_SERVERS": (f"{host}:{ports['redpandaKafka']}"),
                "E2E_KAFKA_BOOTSTRAP": f"{host}:{ports['redpandaKafka']}",
                "DEBEZIUM_CONNECT_URL": f"http://{host}:{ports['debezium']}",
            }
        )
    return environment


def build_compose_environment(
    profile: str,
    ports: dict[str, int],
    bind_address: str,
    advertised_host: str,
) -> dict[str, str]:
    environment = {
        "INFRA_BIND_ADDRESS": bind_address,
        "POSTGRES_PORT": str(ports["postgres"]),
        "VALKEY_PORT": str(ports["valkey"]),
        "ELASTICSEARCH_PORT": str(ports["elasticsearch"]),
        "MINIO_PORT": str(ports["minio"]),
        "MINIO_CONSOLE_PORT": str(ports["minioConsole"]),
    }
    if profile == "distributed":
        environment.update(
            {
                "REDPANDA_ADVERTISED_HOST": advertised_host,
                "REDPANDA_KAFKA_PORT": str(ports["redpandaKafka"]),
                "REDPANDA_ADMIN_PORT": str(ports["redpandaAdmin"]),
                "DEBEZIUM_PORT": str(ports["debezium"]),
            }
        )
    return environment


def compose_arguments(manifest: dict[str, Any], arguments: list[str]) -> list[str]:
    profile_arguments = ["--profile", "distributed"] if manifest["profile"] == "distributed" else []
    return [
        "docker",
        "compose",
        "--project-name",
        manifest["projectName"],
        "--file",
        str(COMPOSE_FILE),
        *profile_arguments,
        *arguments,
    ]


def lease_directory(lease_id: str) -> Path:
    return STATE_ROOT / lease_id


def manifest_path(lease_id: str) -> Path:
    return lease_directory(lease_id) / "manifest.json"


def persist_manifest(manifest: dict[str, Any]) -> None:
    directory = lease_directory(manifest["leaseId"])
    directory.mkdir(parents=True, exist_ok=True)
    target = manifest_path(manifest["leaseId"])
    target.write_text(f"{json.dumps(manifest, indent=2)}\n", encoding="utf-8")
    target.chmod(0o600)


def validate_manifest(manifest: dict[str, Any], expected_lease_id: str | None = None) -> None:
    lease_id = expected_lease_id or manifest.get("leaseId")
    if manifest.get("leaseId") != lease_id:
        raise InfraError("Lease manifest id does not match its state directory")
    if manifest.get("projectName") != f"{PROJECT_PREFIX}{lease_id}":
        raise InfraError("Lease manifest does not own an Agent-prefixed Compose project")
    if manifest.get("composeFile") != str(COMPOSE_FILE):
        raise InfraError("Lease manifest references an unexpected Compose file")
    services_for_profile(manifest.get("profile", ""))


def load_lease(lease_id: str) -> dict[str, Any]:
    if not re.fullmatch(r"[a-f0-9]{12}", lease_id):
        raise InfraError(f"Invalid lease id: {lease_id}")
    try:
        manifest = json.loads(manifest_path(lease_id).read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise InfraError(f"Unknown Infra lease: {lease_id}") from error
    validate_manifest(manifest, lease_id)
    return manifest


def compose_environment(manifest: dict[str, Any]) -> dict[str, str]:
    return {**os.environ, **manifest["composeEnvironment"]}


def start_compose(manifest: dict[str, Any]) -> None:
    run_captured(
        compose_arguments(
            manifest,
            [
                "up",
                "--detach",
                "--wait",
                "--wait-timeout",
                "180",
                *manifest["services"],
            ],
        ),
        env=compose_environment(manifest),
    )
    if manifest["profile"] == "distributed":
        run_captured(
            compose_arguments(
                manifest,
                ["run", "--rm", "--no-deps", "debezium-init"],
            ),
            env=compose_environment(manifest),
        )


def public_lease(manifest: dict[str, Any]) -> dict[str, Any]:
    return {
        key: manifest[key]
        for key in (
            "leaseId",
            "projectName",
            "profile",
            "createdAt",
            "expiresAt",
            "ports",
            "endpoints",
            "environment",
        )
    }


def create_lease(
    *,
    profile: str = "compact",
    ttl_seconds: int = DEFAULT_TTL_SECONDS,
    allow_remote: bool = False,
    start_reaper: bool = False,
    reaper_script: Path | None = None,
) -> dict[str, Any]:
    services = services_for_profile(profile)
    if not MIN_TTL_SECONDS <= ttl_seconds <= MAX_TTL_SECONDS:
        raise InfraError("TTL must be between 5m and 8h")
    if start_reaper and reaper_script is None:
        raise InfraError("A reaper script path is required for a session lease")

    docker_endpoint = current_docker_endpoint()
    assert_docker_endpoint_allowed(docker_endpoint, allow_remote)
    assert_docker_daemon_available()
    connection_host = docker_connection_host(docker_endpoint)
    bind_address = "127.0.0.1" if is_local_docker_endpoint(docker_endpoint) else "0.0.0.0"

    lease_id = secrets.token_hex(6)
    reservations = PortReservations(profile)
    now = time.time()
    try:
        environment = build_lease_environment(
            lease_id, profile, reservations.ports, connection_host
        )
        endpoints = {
            "postgres": f"{connection_host}:{reservations.ports['postgres']}",
            "valkey": f"{connection_host}:{reservations.ports['valkey']}",
            "elasticsearch": (f"http://{connection_host}:{reservations.ports['elasticsearch']}"),
            "minio": f"http://{connection_host}:{reservations.ports['minio']}",
            "web": f"http://{connection_host}:{reservations.ports['web']}",
            "frontend": f"http://{connection_host}:{reservations.ports['frontend']}",
        }
        if profile == "distributed":
            endpoints.update(
                {
                    "kafka": f"{connection_host}:{reservations.ports['redpandaKafka']}",
                    "debezium": (f"http://{connection_host}:{reservations.ports['debezium']}"),
                }
            )
        manifest = {
            "version": 1,
            "leaseId": lease_id,
            "projectName": f"{PROJECT_PREFIX}{lease_id}",
            "profile": profile,
            "createdAt": iso_timestamp(now),
            "expiresAt": iso_timestamp(now + ttl_seconds),
            "dockerEndpoint": docker_endpoint,
            "allowRemote": allow_remote,
            "composeFile": str(COMPOSE_FILE),
            "services": list(services),
            "ports": reservations.ports,
            "endpoints": endpoints,
            "environment": environment,
            "composeEnvironment": build_compose_environment(
                profile,
                reservations.ports,
                bind_address,
                connection_host,
            ),
            "status": "starting",
        }
        persist_manifest(manifest)
    finally:
        reservations.release()

    try:
        start_compose(manifest)
        manifest["status"] = "active"
        persist_manifest(manifest)
    except Exception:
        destroy_lease(manifest, allow_remote=allow_remote, suppress_errors=True)
        raise

    if start_reaper:
        reaper = subprocess.Popen(
            [sys.executable, str(reaper_script), "__reap", lease_id],
            cwd=REPO_ROOT,
            env=os.environ.copy(),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        manifest["reaperPid"] = reaper.pid
        persist_manifest(manifest)
    return manifest


def iso_timestamp(value: float) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(value))


def timestamp_value(value: str) -> float:
    return calendar.timegm(time.strptime(value, "%Y-%m-%dT%H:%M:%SZ"))


def verify_compose_ownership(manifest: dict[str, Any]) -> None:
    validate_manifest(manifest)
    project_name = manifest["projectName"]
    resource_queries = (
        (
            "container",
            ["docker", "ps", "--all", "--quiet"],
            ["docker", "inspect"],
            ".Config.Labels",
        ),
        (
            "network",
            ["docker", "network", "ls", "--quiet"],
            ["docker", "network", "inspect"],
            ".Labels",
        ),
        (
            "volume",
            ["docker", "volume", "ls", "--quiet"],
            ["docker", "volume", "inspect"],
            ".Labels",
        ),
    )
    for resource_kind, list_command, inspect_command, labels_path in resource_queries:
        output = run_captured(
            [
                *list_command,
                "--filter",
                f"label=com.docker.compose.project={project_name}",
            ]
        )
        for resource_id in output.split():
            owned_project = run_captured(
                [
                    *inspect_command,
                    "--format",
                    f'{{{{index {labels_path} "com.docker.compose.project"}}}}',
                    resource_id,
                ]
            ).strip()
            if owned_project != project_name:
                raise InfraError(
                    f"Docker {resource_kind} {resource_id} is not owned by lease "
                    f"{manifest['leaseId']}"
                )


def destroy_lease(
    manifest_or_id: dict[str, Any] | str,
    *,
    allow_remote: bool = False,
    suppress_errors: bool = False,
) -> None:
    manifest = load_lease(manifest_or_id) if isinstance(manifest_or_id, str) else manifest_or_id
    try:
        validate_manifest(manifest)
        endpoint = current_docker_endpoint()
        assert_docker_endpoint_allowed(endpoint, allow_remote or bool(manifest.get("allowRemote")))
        if endpoint != manifest["dockerEndpoint"]:
            raise InfraError(
                f"Current Docker endpoint {endpoint} differs from lease endpoint "
                f"{manifest['dockerEndpoint']}; switch back before cleanup."
            )
        verify_compose_ownership(manifest)
        run_captured(
            compose_arguments(
                manifest,
                ["down", "--volumes", "--remove-orphans", "--timeout", "10"],
            ),
            env=compose_environment(manifest),
        )
        reaper_pid = manifest.get("reaperPid")
        if reaper_pid and reaper_pid != os.getpid():
            with suppress(ProcessLookupError):
                os.kill(reaper_pid, signal.SIGTERM)
        remove_tree(lease_directory(manifest["leaseId"]))
    except Exception:
        if not suppress_errors:
            raise


def remove_tree(directory: Path) -> None:
    if not directory.exists():
        return
    for child in directory.iterdir():
        if child.is_dir():
            remove_tree(child)
        else:
            child.unlink()
    directory.rmdir()


def execute_in_lease(
    manifest_or_id: dict[str, Any] | str,
    command: list[str],
    *,
    forward_signals: bool = False,
) -> int:
    manifest = load_lease(manifest_or_id) if isinstance(manifest_or_id, str) else manifest_or_id
    if not command:
        raise InfraError("A command is required after --")
    environment = {**os.environ, **manifest["environment"]}
    process = subprocess.Popen(command, cwd=REPO_ROOT, env=environment)
    if not forward_signals:
        return process.wait()

    previous_handlers: dict[int, Any] = {}
    force_kill_deadline: float | None = None

    def forward(signum: int, _frame: Any) -> None:
        nonlocal force_kill_deadline
        if process.poll() is None:
            process.send_signal(signum)
            force_kill_deadline = time.monotonic() + 10

    for signum in (signal.SIGINT, signal.SIGTERM):
        previous_handlers[signum] = signal.getsignal(signum)
        signal.signal(signum, forward)
    try:
        while process.poll() is None:
            if force_kill_deadline and time.monotonic() >= force_kill_deadline:
                process.kill()
            time.sleep(0.1)
        return process.returncode
    finally:
        for signum, handler in previous_handlers.items():
            signal.signal(signum, handler)


def compose_status(manifest_or_id: dict[str, Any] | str, *, as_json: bool) -> str:
    manifest = load_lease(manifest_or_id) if isinstance(manifest_or_id, str) else manifest_or_id
    arguments = ["ps", *(["--format", "json"] if as_json else [])]
    return run_captured(compose_arguments(manifest, arguments), env=compose_environment(manifest))


def compose_logs(manifest_or_id: dict[str, Any] | str, service: str | None) -> int:
    manifest = load_lease(manifest_or_id) if isinstance(manifest_or_id, str) else manifest_or_id
    arguments = ["logs", "--no-color", *([service] if service else [])]
    return run_inherited(compose_arguments(manifest, arguments), env=compose_environment(manifest))


def garbage_collect(
    *,
    quiet: bool = False,
    allow_remote: bool = False,
    excluded_lease_ids: frozenset[str] = frozenset(),
) -> int:
    STATE_ROOT.mkdir(parents=True, exist_ok=True)
    removed = 0
    now = time.time()
    for entry in STATE_ROOT.iterdir():
        if not entry.is_dir() or entry.name in excluded_lease_ids:
            continue
        try:
            manifest = load_lease(entry.name)
            if timestamp_value(manifest["expiresAt"]) <= now:
                destroy_lease(manifest, allow_remote=allow_remote)
                removed += 1
        except Exception as error:
            if not quiet:
                print(f"Skipping lease state {entry.name}: {error}", file=sys.stderr)
    return removed


def wait_until_expired(lease_id: str) -> None:
    try:
        manifest = load_lease(lease_id)
    except FileNotFoundError:
        return
    time.sleep(max(0, timestamp_value(manifest["expiresAt"]) - time.time()))
    try:
        manifest = load_lease(lease_id)
        if timestamp_value(manifest["expiresAt"]) <= time.time():
            destroy_lease(manifest, allow_remote=bool(manifest.get("allowRemote")))
    except FileNotFoundError:
        return


def normalized_command(command: list[str]) -> list[str]:
    return command[1:] if command and command[0] == "--" else command


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="ephemeral-infra")
    subparsers = parser.add_subparsers(dest="action", required=True)

    def creation_options(subparser: argparse.ArgumentParser) -> None:
        subparser.add_argument("--profile", choices=("compact", "distributed"), default="compact")
        subparser.add_argument("--ttl", default="2h")
        subparser.add_argument("--allow-remote", action="store_true")

    run_parser = subparsers.add_parser("run")
    creation_options(run_parser)
    run_parser.add_argument("command", nargs=argparse.REMAINDER)

    up_parser = subparsers.add_parser("up")
    creation_options(up_parser)
    up_parser.add_argument("--json", action="store_true")

    exec_parser = subparsers.add_parser("exec")
    exec_parser.add_argument("lease_id")
    exec_parser.add_argument("command", nargs=argparse.REMAINDER)

    status_parser = subparsers.add_parser("status")
    status_parser.add_argument("lease_id")
    status_parser.add_argument("--json", action="store_true")

    logs_parser = subparsers.add_parser("logs")
    logs_parser.add_argument("lease_id")
    logs_parser.add_argument("service", nargs="?")

    down_parser = subparsers.add_parser("down")
    down_parser.add_argument("lease_id")
    down_parser.add_argument("--allow-remote", action="store_true")

    gc_parser = subparsers.add_parser("gc")
    gc_parser.add_argument("--allow-remote", action="store_true")

    return parser


def main(arguments: list[str] | None = None) -> int:
    command_arguments = sys.argv[1:] if arguments is None else arguments
    if command_arguments[:1] == ["__reap"]:
        if len(command_arguments) != 2:
            raise InfraError("__reap requires exactly one lease id")
        wait_until_expired(command_arguments[1])
        return 0

    args = build_parser().parse_args(command_arguments)
    if args.action != "gc":
        lease_id = getattr(args, "lease_id", None)
        garbage_collect(
            quiet=True,
            allow_remote=getattr(args, "allow_remote", False),
            excluded_lease_ids=frozenset({lease_id}) if lease_id else frozenset(),
        )
    if args.action == "run":
        command = normalized_command(args.command)
        if not command:
            raise InfraError("run requires a command after --")
        ttl_seconds = parse_ttl(args.ttl)
        manifest = create_lease(
            profile=args.profile,
            ttl_seconds=ttl_seconds,
            allow_remote=args.allow_remote,
            start_reaper=True,
            reaper_script=SCRIPT_PATH,
        )
        try:
            return execute_in_lease(manifest, command, forward_signals=True)
        finally:
            destroy_lease(manifest, allow_remote=args.allow_remote)
    if args.action == "up":
        ttl_seconds = parse_ttl(args.ttl)
        manifest = create_lease(
            profile=args.profile,
            ttl_seconds=ttl_seconds,
            allow_remote=args.allow_remote,
            start_reaper=True,
            reaper_script=SCRIPT_PATH,
        )
        lease = public_lease(manifest)
        if args.json:
            print(json.dumps(lease, separators=(",", ":")))
        else:
            print(f"Lease: {lease['leaseId']}")
            print(f"Profile: {lease['profile']}")
            print(f"Expires: {lease['expiresAt']}")
            print(f"Backend: {lease['endpoints']['web']}")
            print(f"Frontend: {lease['endpoints']['frontend']}")
        return 0
    if args.action == "exec":
        command = normalized_command(args.command)
        if not command:
            raise InfraError("exec requires: <lease-id> -- <command>")
        return execute_in_lease(args.lease_id, command)
    if args.action == "status":
        manifest = load_lease(args.lease_id)
        output = compose_status(manifest, as_json=args.json)
        if args.json:
            try:
                parsed = json.loads(output)
            except json.JSONDecodeError:
                parsed = [json.loads(line) for line in output.splitlines() if line]
            print(
                json.dumps(
                    {
                        "leaseId": args.lease_id,
                        "profile": manifest["profile"],
                        "expiresAt": manifest["expiresAt"],
                        "compose": parsed if isinstance(parsed, list) else [parsed],
                    },
                    separators=(",", ":"),
                )
            )
        else:
            print(output, end="")
        return 0
    if args.action == "logs":
        return compose_logs(args.lease_id, args.service)
    if args.action == "down":
        destroy_lease(args.lease_id, allow_remote=args.allow_remote)
        return 0
    if args.action == "gc":
        print(f"Removed {garbage_collect(allow_remote=args.allow_remote)} expired lease(s)")
        return 0
    raise InfraError(f"Unknown action: {args.action}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except InfraError as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
