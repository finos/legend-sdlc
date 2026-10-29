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
Prints the Maven module exclusions for the JDK-11 test leg of
.github/workflows/build-and-test.yml, derived from the reactor's poms.

Modules that compile to a release above 11 can't run on JDK 11, so that leg must exclude them.

Prints "-pl !mod-a,!mod-b" (sorted), or nothing if no module is above release 11, and exits 0.
Exits 2 with a clear message, and prints nothing on stdout, if any module's release can't be
resolved to a plain integer.

Usage: jdk11-leg-excludes.py [reactor-root]
"""
import importlib.util
import os
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

JDK11_LEG_MAX_RELEASE = 11


def _load_bytecode_boundary():
    spec = importlib.util.spec_from_file_location(
        "bytecode_boundary", os.path.join(_SCRIPT_DIR, "bytecode-boundary.py")
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class UnresolvedReleaseError(Exception):
    pass


def modules_above_jdk11(reactor_root, bytecode_boundary):
    """Raises UnresolvedReleaseError naming any module whose release is not a plain integer after
    property resolution."""
    modules = bytecode_boundary.load_reactor(reactor_root)
    above, unresolved = [], []
    for module in modules.values():
        try:
            release = int(module.release)
        except (TypeError, ValueError):
            unresolved.append(f"{module.artifact_id} ({module.release!r})")
            continue
        if release > JDK11_LEG_MAX_RELEASE:
            above.append(module.artifact_id)
    if unresolved:
        raise UnresolvedReleaseError(
            "cannot resolve maven.compiler.release for: " + ", ".join(sorted(unresolved))
        )
    return sorted(above)


def maven_excludes(artifact_ids):
    if not artifact_ids:
        return ""
    return "-pl " + ",".join("!" + artifact_id for artifact_id in sorted(artifact_ids))


def main():
    reactor_root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(_SCRIPT_DIR, "..", "..")
    reactor_root = os.path.abspath(reactor_root)

    try:
        modules = modules_above_jdk11(reactor_root, _load_bytecode_boundary())
    except UnresolvedReleaseError as error:
        print(f"jdk11-leg-excludes: {error}", file=sys.stderr)
        return 2

    print(maven_excludes(modules))
    return 0


if __name__ == "__main__":
    sys.exit(main())
