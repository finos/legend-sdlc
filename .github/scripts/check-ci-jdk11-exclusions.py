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
Drift detector for the JDK-11 test leg's hardcoded module exclusion list in
.github/workflows/build-and-test.yml. The modules that compile to release 17 can't run on JDK
11, so that leg excludes them with a hand-maintained EXCLUDES="-pl ..." list. This script fails
CI if that list disagrees with the reactor's actual release-17 tier, as bytecode-boundary.py
reads it from the poms, so a module moving between tiers can't silently go untested on JDK 11
or break the leg.

Exits 1 (with a diff of the two lists) if the hardcoded EXCLUDES line in the workflow no longer
matches the reactor's actual release-17 modules; exits 0 otherwise.

Usage: check-ci-jdk11-exclusions.py [reactor-root]
"""
import importlib.util
import os
import re
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def _load_bytecode_boundary():
    spec = importlib.util.spec_from_file_location(
        "bytecode_boundary", os.path.join(_SCRIPT_DIR, "bytecode-boundary.py")
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


EXCLUDES_PATTERN = re.compile(r'EXCLUDES="-pl ((?:![\w.-]+,?)+)"')


def parse_hardcoded_excludes(workflow_text):
    """Extracts the module names from a line like EXCLUDES="-pl !mod-a,!mod-b" in the workflow
    YAML. Returns a sorted list; raises ValueError if no such line is found, since a workflow
    that no longer has this pattern at all is itself a sign this check needs attention, not a
    silent pass."""
    match = EXCLUDES_PATTERN.search(workflow_text)
    if match is None:
        raise ValueError("no EXCLUDES=\"-pl !...\" line found in the workflow file")
    names = [name for name in match.group(1).split(",") if name]
    return sorted(name.lstrip("!") for name in names)


def actual_release_17_modules(reactor_root, bytecode_boundary):
    modules = bytecode_boundary.load_reactor(reactor_root)
    return sorted(
        module.artifact_id for module in modules.values() if int(module.release) == 17
    )


def check(hardcoded, actual):
    """Pure comparison, split out from main() so it's testable without a real reactor or
    workflow file on disk."""
    return hardcoded == actual


def main():
    reactor_root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(_SCRIPT_DIR, "..", "..")
    reactor_root = os.path.abspath(reactor_root)
    workflow_path = os.path.join(reactor_root, ".github", "workflows", "build-and-test.yml")

    with open(workflow_path) as handle:
        workflow_text = handle.read()
    hardcoded = parse_hardcoded_excludes(workflow_text)

    bytecode_boundary = _load_bytecode_boundary()
    actual = actual_release_17_modules(reactor_root, bytecode_boundary)

    if not check(hardcoded, actual):
        print(
            "JDK-11 exclusion list in build-and-test.yml has drifted from "
            "bytecode-boundary.py's actual release-17 tier.",
            file=sys.stderr,
        )
        print(f"  hardcoded (workflow): {','.join(hardcoded)}", file=sys.stderr)
        print(f"  actual (reactor):     {','.join(actual)}", file=sys.stderr)
        print(
            "Update the EXCLUDES=\"-pl ...\" line in .github/workflows/build-and-test.yml to "
            "match.",
            file=sys.stderr,
        )
        return 1

    print(f"JDK-11 exclusion list matches the release-17 tier: {','.join(actual)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
