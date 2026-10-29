#!/usr/bin/env python3
# Copyright 2026 Goldman Sachs
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import unittest

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_SCRIPT = os.path.join(_SCRIPT_DIR, "jdk11-leg-excludes.py")


def _load(module_name, filename):
    spec = importlib.util.spec_from_file_location(module_name, os.path.join(_SCRIPT_DIR, filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_excludes = _load("jdk11_leg_excludes", "jdk11-leg-excludes.py")
_bytecode_boundary = _load("bytecode_boundary", "bytecode-boundary.py")

REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))


class TestMavenExcludes(unittest.TestCase):
    def test_no_modules_means_no_argument(self):
        self.assertEqual(_excludes.maven_excludes([]), "")

    def test_single_module(self):
        self.assertEqual(_excludes.maven_excludes(["legend-sdlc-server"]), "-pl !legend-sdlc-server")

    def test_multiple_modules_sorted(self):
        self.assertEqual(
            _excludes.maven_excludes(["legend-sdlc-server-shared", "legend-sdlc-server"]),
            "-pl !legend-sdlc-server,!legend-sdlc-server-shared",
        )


_ROOT_POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <artifactId>root</artifactId>
  <packaging>pom</packaging>
  <properties>
    <maven.compiler.release>11</maven.compiler.release>
  </properties>
  <modules>
    <module>mod-low</module>
    <module>mod-high</module>
  </modules>
</project>
"""

_MODULE_POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <artifactId>{artifact_id}</artifactId>
  <properties>
{properties}
  </properties>
</project>
"""


def _write_reactor(root, high_properties):
    with open(os.path.join(root, "pom.xml"), "w") as handle:
        handle.write(_ROOT_POM)
    for artifact_id, properties in (("mod-low", ""), ("mod-high", high_properties)):
        os.makedirs(os.path.join(root, artifact_id))
        with open(os.path.join(root, artifact_id, "pom.xml"), "w") as handle:
            handle.write(_MODULE_POM.format(artifact_id=artifact_id, properties=properties))


class TestModulesAboveJdk11(unittest.TestCase):
    def _run(self, high_properties):
        with tempfile.TemporaryDirectory() as root:
            _write_reactor(root, high_properties)
            return subprocess.run([sys.executable, "-I", _SCRIPT, root], capture_output=True, text=True)

    def test_release_17_module_is_excluded(self):
        result = self._run("<maven.compiler.release>17</maven.compiler.release>")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "-pl !mod-high")

    def test_release_21_module_is_excluded(self):
        result = self._run("<maven.compiler.release>21</maven.compiler.release>")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "-pl !mod-high")

    def test_release_11_module_is_not_excluded(self):
        result = self._run("<maven.compiler.release>11</maven.compiler.release>")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "")

    def test_release_from_module_local_property_is_resolved(self):
        result = self._run(
            "<server.release>17</server.release>\n"
            "<maven.compiler.release>${server.release}</maven.compiler.release>"
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "-pl !mod-high")

    def test_unresolvable_release_fails_clearly_without_traceback(self):
        result = self._run("<maven.compiler.release>${nowhere}</maven.compiler.release>")
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("mod-high", result.stderr)


class TestAgainstRealRepo(unittest.TestCase):
    def test_real_reactor_excludes_exactly_the_modules_above_release_11(self):
        modules = _bytecode_boundary.load_reactor(REPO_ROOT)
        expected = sorted(m.artifact_id for m in modules.values() if int(m.release) > 11)
        self.assertIn("legend-sdlc-server", expected)
        self.assertEqual(_excludes.modules_above_jdk11(REPO_ROOT, _bytecode_boundary), expected)

    def test_workflow_uses_the_derived_list(self):
        with open(os.path.join(REPO_ROOT, ".github", "workflows", "build-and-test.yml")) as handle:
            workflow = handle.read()
        self.assertIn('EXCLUDES="$(python3 .github/scripts/jdk11-leg-excludes.py)"', workflow)
        self.assertIsNone(re.search(r'EXCLUDES="-pl !', workflow), "hand-maintained EXCLUDES list in the workflow")


if __name__ == "__main__":
    unittest.main()
