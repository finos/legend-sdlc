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
Checks the two-tier Java bytecode boundary this reactor enforces: every module declares a
``maven.compiler.release`` (inherited from root unless overridden), and the modules that
override it to a higher release emit higher-major class files than the rest of the reactor.

Two independent checks:

  boundary  Walks each module's own reactor-internal (org.finos.legend.sdlc) dependencies and
            fails if a lower-release module depends, directly or transitively, on a
            higher-release one. That edge would make the lower-release module's own declared
            ceiling a lie: anything loading it at that ceiling would also have to load the
            higher-release dependency, which it cannot.

  audit     Reads the actual class-file major version out of each module's compiled
            target/classes (requires a prior ``mvn package`` or ``install``) and compares it
            against what the module's declared release should produce. A release whose major
            cannot be computed, or a module with no compiled classes at all, goes to an
            ``unresolved`` bucket that FAILS the run -- an unresolved module is one this script
            did not check, and reporting PASS on a module it never checked is the failure mode
            this script exists to avoid repeating.

Usage: bytecode-boundary.py {boundary|audit|both} [reactor-root]
"""
import os
import re
import struct
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def strip_ns(tag):
    return tag.split("}", 1)[-1] if "}" in tag else tag


def local_find(elem, name):
    for child in elem:
        if strip_ns(child.tag) == name:
            return child
    return None


def local_findall(elem, name):
    return [child for child in elem if strip_ns(child.tag) == name]


def text_of(elem, name, default=None):
    child = local_find(elem, name) if elem is not None else None
    return child.text.strip() if (child is not None and child.text) else default


def major_for_release(release):
    """Class-file major version for a javac --release value: 11 -> 55, 17 -> 61."""
    return int(release) + 44


def resolve_property(value, properties):
    """Resolve a single ${property} reference, one level -- these poms do not chain them."""
    if value is None:
        return None
    match = re.fullmatch(r"\$\{([^}]+)}", value.strip())
    if match:
        return properties.get(match.group(1), value)
    return value


class Module:
    def __init__(self, artifact_id, path, release, reactor_deps, packaging):
        self.artifact_id = artifact_id
        self.path = path
        self.release = release
        self.reactor_deps = reactor_deps
        self.packaging = packaging


def load_root(reactor_root):
    tree = ET.parse(os.path.join(reactor_root, "pom.xml"))
    root = tree.getroot()
    properties_elem = local_find(root, "properties")
    properties = {}
    if properties_elem is not None:
        for child in properties_elem:
            if child.text:
                properties[strip_ns(child.tag)] = child.text.strip()
    default_release = resolve_property(properties.get("maven.compiler.release"), properties)
    modules_elem = local_find(root, "modules")
    module_dirs = [m.text.strip() for m in local_findall(modules_elem, "module")] if modules_elem is not None else []
    return default_release, module_dirs, properties


def load_module(reactor_root, module_dir, default_release, root_properties):
    pom_path = os.path.join(reactor_root, module_dir, "pom.xml")
    tree = ET.parse(pom_path)
    root = tree.getroot()
    artifact_id = text_of(root, "artifactId")
    packaging = text_of(root, "packaging", "jar")

    properties_elem = local_find(root, "properties")
    release = default_release
    if properties_elem is not None:
        local_release = text_of(properties_elem, "maven.compiler.release")
        if local_release is not None:
            # Module-local properties shadow the root's, as they do in Maven.
            properties = dict(root_properties)
            for child in properties_elem:
                if child.text:
                    properties[strip_ns(child.tag)] = child.text.strip()
            release = resolve_property(local_release, properties)

    reactor_deps = []
    deps_elem = local_find(root, "dependencies")
    if deps_elem is not None:
        for dep in local_findall(deps_elem, "dependency"):
            group_id = text_of(dep, "groupId")
            if group_id == "org.finos.legend.sdlc":
                reactor_deps.append(text_of(dep, "artifactId"))

    return Module(artifact_id, module_dir, release, reactor_deps, packaging)


def load_reactor(reactor_root):
    default_release, module_dirs, root_properties = load_root(reactor_root)
    modules = {}
    for module_dir in module_dirs:
        module = load_module(reactor_root, module_dir, default_release, root_properties)
        modules[module.artifact_id] = module
    return modules


def check_boundary(modules):
    """A module may only depend (directly or transitively) on reactor siblings whose release is
    no higher than its own. Transitive closure matters: D-102's four-module tier was discovered
    exactly because a direct Java-17 dependency drags every downstream consumer into the same
    violation even when the consumer's own pom looks untouched.

    Returns (violations, unresolved): an unresolvable release value (same failure mode
    run_audit()'s major_for_release() already guards against) is reported as unresolved rather
    than left to crash the script with an uncaught ValueError, so this check stays consistent
    with run_audit()'s "never silently fail to check a module" design. A packaging=pom module
    emits no bytecode of its own to violate the boundary with, so it is skipped from being
    flagged itself, mirroring run_audit()'s existing packaging=pom skip -- it is still walked as
    a dependency of other modules, since its reactor_deps may still need to be traversed."""
    violations = []
    unresolved = []

    def effective_release_reachable(module, seen):
        """All releases reachable from this module's reactor dependency closure, including its own."""
        if module.artifact_id in seen:
            return set()
        seen.add(module.artifact_id)
        try:
            releases = {int(module.release)}
        except (TypeError, ValueError):
            releases = set()
        for dep_name in module.reactor_deps:
            dep = modules.get(dep_name)
            if dep is not None:
                releases |= effective_release_reachable(dep, seen)
        return releases

    for module in modules.values():
        if module.packaging == "pom":
            # A relocation/aggregator POM emits no bytecode; nothing to flag as violating.
            continue
        try:
            own_release = int(module.release)
        except (TypeError, ValueError):
            unresolved.append((module.artifact_id, f"unresolvable release {module.release!r}"))
            continue
        reachable = effective_release_reachable(module, set())
        too_high = {r for r in reachable if r > own_release}
        if too_high:
            violations.append((module.artifact_id, own_release, sorted(too_high)))

    return violations, unresolved


def run_boundary(reactor_root):
    modules = load_reactor(reactor_root)
    by_release = {}
    unresolved_display = []
    for module in modules.values():
        try:
            by_release.setdefault(int(module.release), []).append(module.artifact_id)
        except (TypeError, ValueError):
            unresolved_display.append(module.artifact_id)

    print("Modules by declared release:")
    for release in sorted(by_release):
        names = sorted(by_release[release])
        print(f"  release {release}: {len(names)} module(s)")
        for name in names:
            print(f"    - {name}")
    if unresolved_display:
        print(f"  release <unresolved>: {len(unresolved_display)} module(s)")
        for name in sorted(unresolved_display):
            print(f"    - {name}")

    violations, unresolved = check_boundary(modules)
    print()
    if violations:
        print(f"BOUNDARY VIOLATIONS: {len(violations)}")
        for name, own_release, too_high in violations:
            print(f"  {name} (release {own_release}) reaches release(s) {too_high} "
                  f"through its reactor dependencies")

    if unresolved:
        print(f"UNRESOLVED (this check could not evaluate these -- treated as a failure, not a skip):")
        for name, reason in unresolved:
            print(f"  {name}: {reason}")

    if violations or unresolved:
        return 1

    counts = ", ".join(f"{len(v)} at {k}" for k, v in sorted(by_release.items()))
    print(f"boundary: {counts}, violations=0")
    return 0


MAJOR_PATTERN = re.compile(r"\.class$")


def class_file_major(path):
    with open(path, "rb") as handle:
        magic = handle.read(4)
        if magic != b"\xca\xfe\xba\xbe":
            return None
        handle.read(2)  # minor
        major_bytes = handle.read(2)
        return struct.unpack(">H", major_bytes)[0]


def majors_in(classes_dir):
    majors = set()
    for dirpath, _dirnames, filenames in os.walk(classes_dir):
        for filename in filenames:
            if MAJOR_PATTERN.search(filename):
                major = class_file_major(os.path.join(dirpath, filename))
                if major is not None:
                    majors.add(major)
    return majors


def run_audit(reactor_root):
    modules = load_reactor(reactor_root)
    ok, too_high, below_declared, unresolved = [], [], [], []

    for module in modules.values():
        if module.packaging == "pom":
            # A relocation/aggregator POM emits no bytecode; nothing to audit.
            continue
        try:
            expected_major = major_for_release(module.release)
        except (TypeError, ValueError):
            unresolved.append((module.artifact_id, f"unresolvable release {module.release!r}"))
            continue

        classes_dir = os.path.join(reactor_root, module.path, "target", "classes")
        if not os.path.isdir(classes_dir):
            if not os.path.isdir(os.path.join(reactor_root, module.path, "src", "main")):
                # No src/main at all (e.g. a test-only module) -- nothing to emit, not a skip.
                continue
            unresolved.append((module.artifact_id, "no target/classes (module not built)"))
            continue

        actual_majors = majors_in(classes_dir)
        if not actual_majors:
            unresolved.append((module.artifact_id, "target/classes exists but has no .class files"))
            continue

        highest = max(actual_majors)
        if highest > expected_major:
            too_high.append((module.artifact_id, expected_major, highest))
        elif highest < expected_major:
            below_declared.append((module.artifact_id, expected_major, highest))
        else:
            ok.append(module.artifact_id)

    print(f"audit: ok={len(ok)} too-high={len(too_high)} "
          f"below-declared={len(below_declared)} unresolved={len(unresolved)}")

    if too_high:
        print("\nTOO HIGH (emits bytecode above its declared release):")
        for name, expected, actual in too_high:
            print(f"  {name}: expected major {expected}, found {actual}")

    if below_declared:
        print("\nBelow declared (safe -- a module may emit older bytecode than its ceiling allows):")
        for name, expected, actual in below_declared:
            print(f"  {name}: expected major {expected}, found {actual}")

    if unresolved:
        print("\nUNRESOLVED (this script could not audit these -- treated as a failure, not a skip):")
        for name, reason in unresolved:
            print(f"  {name}: {reason}")

    return 1 if (too_high or unresolved) else 0


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("boundary", "audit", "both"):
        print(__doc__)
        return 2

    mode = sys.argv[1]
    reactor_root = sys.argv[2] if len(sys.argv) > 2 else os.getcwd()

    exit_code = 0
    if mode in ("boundary", "both"):
        exit_code |= run_boundary(reactor_root)
    if mode in ("audit", "both"):
        if mode == "both":
            print()
        exit_code |= run_audit(reactor_root)

    print("\nPASS" if exit_code == 0 else "\nFAIL")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
