from __future__ import annotations

import os
import subprocess
import sys
import time
import unittest
from pathlib import Path
from urllib.request import urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))

import ephemeral_infra as infra


def containers_for_project(project_name: str) -> list[str]:
    output = infra.run_captured(
        [
            "docker",
            "ps",
            "--all",
            "--quiet",
            "--filter",
            f"label=com.docker.compose.project={project_name}",
        ]
    )
    return sorted(output.split())


def wait_for_web(process: subprocess.Popen[str], url: str) -> None:
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if process.poll() is not None:
            output = process.stdout.read() if process.stdout else ""
            raise AssertionError(f"Workbench Web exited before health check:\n{output}")
        try:
            with urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(2)
    process.terminate()
    output = process.stdout.read() if process.stdout else ""
    raise AssertionError(f"Timed out waiting for {url}:\n{output}")


def stop_process(process: subprocess.Popen[str] | None) -> None:
    if process is None:
        return
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    if process.stdout:
        process.stdout.close()


class EphemeralInfraSmokeTest(unittest.TestCase):
    def test_concurrent_compact_leases_are_migrated_and_removed(self) -> None:
        user_containers_before = containers_for_project("workbench")
        leases: list[dict[str, object]] = []
        web_process: subprocess.Popen[str] | None = None
        try:
            leases.append(infra.create_lease(profile="compact"))
            leases.append(infra.create_lease(profile="compact"))
            for lease in leases:
                status = infra.compose_status(lease, as_json=False)
                for service in infra.COMPACT_SERVICES:
                    self.assertIn(service, status)
                self.assertTrue(infra.is_local_docker_endpoint(str(lease["dockerEndpoint"])))
                self.assertEqual(len(containers_for_project(str(lease["projectName"]))), 4)

            first_ports = set(leases[0]["ports"].values())
            self.assertTrue(first_ports.isdisjoint(leases[1]["ports"].values()))

            primary = leases[0]
            environment = {**os.environ, **primary["environment"]}
            web_process = subprocess.Popen(
                [
                    "java",
                    "-jar",
                    str(infra.REPO_ROOT / "workbench-web/build/libs/workbench-web.jar"),
                    "--spring.profiles.active=local",
                ],
                cwd=infra.REPO_ROOT,
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            wait_for_web(
                web_process,
                f"{primary['endpoints']['web']}/api/actuator/health",
            )
        finally:
            stop_process(web_process)
            cleanup_errors: list[Exception] = []
            for lease in reversed(leases):
                try:
                    infra.destroy_lease(lease)
                except Exception as error:
                    cleanup_errors.append(error)
            if cleanup_errors:
                raise ExceptionGroup("Failed to clean every smoke-test lease", cleanup_errors)

        for lease in leases:
            self.assertEqual(containers_for_project(str(lease["projectName"])), [])
        self.assertEqual(containers_for_project("workbench"), user_containers_before)


if __name__ == "__main__":
    unittest.main()
