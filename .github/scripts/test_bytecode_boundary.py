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
Regression tests for bytecode-boundary.py's check_boundary():

  - An unresolvable maven.compiler.release value (such as an undefined property) is reported
    as unresolved rather than crashing with a ValueError, matching how run_audit() handles it.

  - A packaging=pom module, which has no bytecode of its own, is not flagged for reaching a
    higher-release reactor dependency (as legend-sdlc-test-reports does, since it aggregates
    every module's test reports), matching run_audit()'s packaging=pom skip.

Run directly: python3 test_bytecode_boundary.py
"""
import importlib.util
import os
import unittest

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_SPEC = importlib.util.spec_from_file_location(
    "bytecode_boundary", os.path.join(_SCRIPT_DIR, "bytecode-boundary.py")
)
_bytecode_boundary = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_bytecode_boundary)

Module = _bytecode_boundary.Module
check_boundary = _bytecode_boundary.check_boundary


class TestCheckBoundaryUnresolvedRelease(unittest.TestCase):
    """An unresolvable release value must not crash the script."""

    def test_non_numeric_release_does_not_raise(self):
        modules = {
            "leaf": Module("leaf", "leaf", "${some.undefined.property}", [], "jar"),
        }
        try:
            check_boundary(modules)
        except ValueError:
            self.fail(
                "check_boundary() raised ValueError on an unresolved release value; it must "
                "report the module as unresolved instead, matching run_audit()'s handling"
            )

    def test_non_numeric_release_is_reported_unresolved(self):
        modules = {
            "leaf": Module("leaf", "leaf", "${some.undefined.property}", [], "jar"),
        }
        result = check_boundary(modules)
        self.assertIsInstance(result, tuple, "check_boundary() must return (violations, unresolved)")
        _violations, unresolved = result
        self.assertEqual(
            len(unresolved), 1,
            "the module with an unresolvable release must be reported as unresolved",
        )
        self.assertEqual(unresolved[0][0], "leaf")

    def test_dependent_module_with_unresolved_dependency_release_does_not_raise(self):
        """The unresolved release might be reached transitively through a dependency, not just
        on the module being checked directly -- effective_release_reachable() recurses into
        dependencies' own int(module.release) calls too."""
        modules = {
            "leaf": Module("leaf", "leaf", "${some.undefined.property}", [], "jar"),
            "consumer": Module("consumer", "consumer", "11", ["leaf"], "jar"),
        }
        try:
            check_boundary(modules)
        except ValueError:
            self.fail(
                "check_boundary() raised ValueError resolving a dependency's unresolved release"
            )


class TestCheckBoundarySkipsPomPackaging(unittest.TestCase):
    """A packaging=pom module (no bytecode of its own) must not be flagged for reaching a
    higher-release reactor dependency -- it emits nothing a boundary violation could even
    describe. Mirrors run_audit()'s packaging=pom skip."""

    def test_pom_packaging_module_reaching_higher_release_dependency_is_not_a_violation(self):
        modules = {
            "java17-module": Module("java17-module", "java17-module", "17", [], "jar"),
            "aggregator": Module(
                "aggregator", "aggregator", "11", ["java17-module"], "pom"
            ),
        }
        violations, _unresolved = check_boundary(modules)
        violating_names = {name for (name, _own, _too_high) in violations}
        self.assertNotIn(
            "aggregator", violating_names,
            "a packaging=pom aggregator with no compiled bytecode of its own must be skipped, "
            "exactly like run_audit() already skips it",
        )

    def test_jar_packaging_module_reaching_higher_release_dependency_is_still_a_violation(self):
        """The skip must be specific to packaging=pom -- a real jar module with the same shape
        must still be caught, or the fix would be a blanket bypass rather than a targeted one."""
        modules = {
            "java17-module": Module("java17-module", "java17-module", "17", [], "jar"),
            "jar-consumer": Module(
                "jar-consumer", "jar-consumer", "11", ["java17-module"], "jar"
            ),
        }
        violations, _unresolved = check_boundary(modules)
        violating_names = {name for (name, _own, _too_high) in violations}
        self.assertIn(
            "jar-consumer", violating_names,
            "a real jar module reaching a higher-release dependency must still be flagged",
        )


if __name__ == "__main__":
    unittest.main()
