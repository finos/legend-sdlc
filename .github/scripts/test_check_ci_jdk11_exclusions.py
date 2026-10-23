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

"""
Tests for check-ci-jdk11-exclusions.py, the drift detector for the JDK-11 test leg's
hardcoded module exclusion list.

The first group proves the detector actually catches drift: a deliberately mismatched
hardcoded/actual pair must fail, not pass silently.
The second group is an integration check against the real workflow file and real reactor pom.xml
files, which doubles as proof the detector is correctly wired against the current repo state.

Run directly: python3 test_check_ci_jdk11_exclusions.py
"""
import importlib.util
import os
import unittest

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def _load(module_name, filename):
    spec = importlib.util.spec_from_file_location(module_name, os.path.join(_SCRIPT_DIR, filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_checker = _load("check_ci_jdk11_exclusions", "check-ci-jdk11-exclusions.py")
_bytecode_boundary = _load("bytecode_boundary", "bytecode-boundary.py")

REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))


class TestParseHardcodedExcludes(unittest.TestCase):
    def test_single_module(self):
        text = 'EXCLUDES="-pl !legend-sdlc-server"'
        self.assertEqual(_checker.parse_hardcoded_excludes(text), ["legend-sdlc-server"])

    def test_multiple_modules_sorted(self):
        text = 'EXCLUDES="-pl !legend-sdlc-server-shared,!legend-sdlc-server"'
        self.assertEqual(
            _checker.parse_hardcoded_excludes(text),
            ["legend-sdlc-server", "legend-sdlc-server-shared"],
        )

    def test_embedded_in_larger_yaml(self):
        text = (
            "          EXCLUDES=\"\"\n"
            "          if [ \"${{ matrix.jdk }}\" = \"11\" ]; then\n"
            "            EXCLUDES=\"-pl !legend-sdlc-server,!legend-sdlc-server-shared\"\n"
            "          fi\n"
        )
        self.assertEqual(
            _checker.parse_hardcoded_excludes(text),
            ["legend-sdlc-server", "legend-sdlc-server-shared"],
        )

    def test_raises_when_no_excludes_line_present(self):
        with self.assertRaises(ValueError):
            _checker.parse_hardcoded_excludes("no excludes here at all")


class TestCheckCatchesDrift(unittest.TestCase):
    """The detector's core claim: a mismatch must be caught, not silently pass."""

    def test_matching_lists_pass(self):
        hardcoded = ["legend-sdlc-server", "legend-sdlc-server-shared"]
        actual = ["legend-sdlc-server", "legend-sdlc-server-shared"]
        self.assertTrue(_checker.check(hardcoded, actual))

    def test_mismatched_lists_fail(self):
        # The drift this detector exists for: a module moved to release 17 (or back out)
        # and the hand-typed YAML list wasn't updated to match.
        hardcoded = ["legend-sdlc-server", "legend-sdlc-server-shared"]
        actual = ["legend-sdlc-server", "legend-sdlc-server-shared", "legend-sdlc-test-reports"]
        self.assertFalse(_checker.check(hardcoded, actual))

    def test_stale_extra_entry_is_also_caught(self):
        # The opposite drift direction: the YAML still excludes a module that moved back down
        # to release 11.
        hardcoded = ["legend-sdlc-server", "legend-sdlc-server-shared", "legend-sdlc-test-reports"]
        actual = ["legend-sdlc-server", "legend-sdlc-server-shared"]
        self.assertFalse(_checker.check(hardcoded, actual))


class TestAgainstRealRepo(unittest.TestCase):
    """Integration check: the real workflow file and the real reactor must agree today."""

    def test_hardcoded_matches_actual_in_this_repo(self):
        workflow_path = os.path.join(REPO_ROOT, ".github", "workflows", "build-and-test.yml")
        with open(workflow_path) as handle:
            workflow_text = handle.read()
        hardcoded = _checker.parse_hardcoded_excludes(workflow_text)
        actual = _checker.actual_release_17_modules(REPO_ROOT, _bytecode_boundary)
        self.assertEqual(
            hardcoded, actual,
            "the real workflow's EXCLUDES list has drifted from the real reactor's release-17 "
            "tier -- update .github/workflows/build-and-test.yml",
        )


if __name__ == "__main__":
    unittest.main()
