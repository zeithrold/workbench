from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import ephemeral_infra as infra


class EphemeralInfraTest(unittest.TestCase):
    def test_profiles_select_minimum_required_services(self) -> None:
        self.assertEqual(
            infra.services_for_profile("compact"),
            ("postgres", "valkey", "elasticsearch", "minio"),
        )
        self.assertEqual(
            infra.services_for_profile("distributed"),
            (
                "postgres",
                "valkey",
                "elasticsearch",
                "minio",
                "redpanda",
                "debezium",
            ),
        )
        with self.assertRaisesRegex(infra.InfraError, "Unsupported Infra profile"):
            infra.services_for_profile("everything")

    def test_ttl_accepts_documented_range(self) -> None:
        self.assertEqual(infra.parse_ttl("5m"), infra.MIN_TTL_SECONDS)
        self.assertEqual(infra.parse_ttl("2h"), 2 * 60 * 60)
        self.assertEqual(infra.parse_ttl("8h"), infra.MAX_TTL_SECONDS)
        for invalid in ("4m", "9h"):
            with self.assertRaisesRegex(infra.InfraError, "between 5m and 8h"):
                infra.parse_ttl(invalid)
        with self.assertRaisesRegex(infra.InfraError, "whole minutes or hours"):
            infra.parse_ttl("300s")

    def test_only_local_docker_endpoints_are_autonomous(self) -> None:
        self.assertTrue(infra.is_local_docker_endpoint("unix:///var/run/docker.sock"))
        self.assertTrue(infra.is_local_docker_endpoint("npipe:////./pipe/docker_engine"))
        self.assertFalse(infra.is_local_docker_endpoint("ssh://build.example"))
        self.assertFalse(infra.is_local_docker_endpoint("tcp://10.0.0.2:2375"))
        infra.assert_docker_endpoint_allowed("unix:///var/run/docker.sock")
        with self.assertRaisesRegex(infra.InfraError, "Refusing non-local"):
            infra.assert_docker_endpoint_allowed("ssh://build.example")
        infra.assert_docker_endpoint_allowed("ssh://build.example", True)
        self.assertEqual(
            infra.docker_connection_host("unix:///var/run/docker.sock"),
            "127.0.0.1",
        )
        self.assertEqual(
            infra.docker_connection_host("ssh://agent@build.example"),
            "build.example",
        )
        self.assertEqual(infra.docker_connection_host("tcp://10.0.0.2:2375"), "10.0.0.2")

    def test_lease_environment_uses_isolated_ports(self) -> None:
        ports = {
            "postgres": 15432,
            "valkey": 16379,
            "elasticsearch": 19200,
            "minio": 19000,
            "minioConsole": 19001,
            "web": 18080,
            "frontend": 14173,
            "redpandaKafka": 19093,
            "redpandaAdmin": 19645,
            "debezium": 18083,
        }
        compact = infra.build_lease_environment("abcdef123456", "compact", ports)
        self.assertEqual(
            compact["SPRING_DATASOURCE_URL"],
            "jdbc:postgresql://127.0.0.1:15432/workbench",
        )
        self.assertEqual(compact["WORKBENCH_MESSAGING_TRANSPORT"], "postgres")
        self.assertEqual(compact["OPENAPI_INPUT"], "http://127.0.0.1:18080/api/openapi")
        self.assertNotIn("SPRING_KAFKA_BOOTSTRAP_SERVERS", compact)

        distributed = infra.build_lease_environment("abcdef123456", "distributed", ports)
        self.assertEqual(distributed["WORKBENCH_MESSAGING_TRANSPORT"], "kafka")
        self.assertEqual(
            distributed["SPRING_KAFKA_BOOTSTRAP_SERVERS"],
            "127.0.0.1:19093",
        )
        self.assertEqual(distributed["DEBEZIUM_CONNECT_URL"], "http://127.0.0.1:18083")
        remote = infra.build_lease_environment("abcdef123456", "compact", ports, "build.example")
        self.assertEqual(
            remote["SPRING_DATASOURCE_URL"],
            "jdbc:postgresql://build.example:15432/workbench",
        )

    def test_manifest_validation_refuses_unowned_projects(self) -> None:
        valid = {
            "leaseId": "abcdef123456",
            "projectName": "workbench-agent-abcdef123456",
            "profile": "compact",
            "composeFile": str(infra.COMPOSE_FILE),
        }
        infra.validate_manifest(valid)
        with self.assertRaisesRegex(infra.InfraError, "Agent-prefixed"):
            infra.validate_manifest({**valid, "projectName": "workbench"})
        with self.assertRaisesRegex(infra.InfraError, "unexpected Compose file"):
            infra.validate_manifest({**valid, "composeFile": "/tmp/compose.yaml"})

    def test_session_requires_reaper_before_docker_work(self) -> None:
        with self.assertRaisesRegex(infra.InfraError, "reaper script path"):
            infra.create_lease(start_reaper=True)

    def test_remote_docker_is_rejected_before_docker_commands(self) -> None:
        with (
            patch.dict(os.environ, {"DOCKER_HOST": "ssh://build.example"}),
            patch.object(infra, "run_captured") as run_captured,
            self.assertRaisesRegex(infra.InfraError, "Refusing non-local"),
        ):
            infra.create_lease()
        run_captured.assert_not_called()


if __name__ == "__main__":
    unittest.main()
