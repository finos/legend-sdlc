# Re-architecture Implementation Worklog

Running log of the implementation of [`re-architecture.md`](re-architecture.md).
One section per phase; within a phase, newest entries last. Records what moved,
decisions taken where the plan allowed a choice, deviations from the plan, and
bugs observed-but-preserved (Phases 2–3 are behavior-preserving; fixes are logged
here and deferred). A fresh session should be able to resume from this file plus
`git log` on the `reorg` branch.

## Conventions adopted (apply to all phases)

- **New package roots**: relocated classes leave `org.finos.legend.sdlc.server.*`
  per re-architecture §5. Chosen so far: `org.finos.legend.sdlc.tools` (shared
  utilities), `org.finos.legend.sdlc.error` (framework-free exceptions). These
  follow the existing non-server precedents (`…sdlc.serialization`,
  `…sdlc.domain.model.*`, `…sdlc.generation`).
- **Bridge pattern for classes**: old FQN retained as a `@Deprecated` subclass of
  the relocated class, in the module that historically shipped the old FQN. The
  Javadoc says the bridge is retained *temporarily* — it does not promise a
  specific release cycle (removal timing is coordinated per re-architecture §5).
  Static utility methods are reachable through subclass names, so bridges for
  utility classes are empty subclasses (relocated utility constructors were made
  `protected` to permit this).
- **Build/verify**: `mvn install javadoc:javadoc` on Java 25 (matches
  `.github/workflows/build.yml`); checkstyle and `dependency:analyze`
  (`failOnWarning=true`) are active, so a green build implies both.

## Phase 1 — Foundations (completing `legend-sdlc-shared`)

**Status: complete** (this commit).

What moved:

- `StringTools`, `IOTools` (+ tests): `org.finos.legend.sdlc.server.tools` →
  `org.finos.legend.sdlc.tools`, staying in `legend-sdlc-shared`. `@Deprecated`
  empty-subclass bridges left at the old FQNs in `legend-sdlc-shared` (that is
  where the old FQNs currently ship on this branch). All in-repo references
  (22 files across `legend-sdlc-project-files`, `-server`, `-server-fs`,
  `-server-shared`) updated to the new package.
- `LegendSDLCServerException` → **`org.finos.legend.sdlc.error.LegendSDLCException`**
  in `legend-sdlc-shared`. The JAX-RS `Response.Status` field is replaced by a
  plain `int statusCode` (`getStatusCode()`); defaults preserved exactly
  (constructors default 500; `validate*` helpers default 400). Static `validate*`
  helpers mirrored with `int` overloads.
- `LegendSDLCServerException` remains in `legend-sdlc-server-shared` as a
  `@Deprecated` subclass: identical constructor signatures (`Status`-typed,
  null → 500), `getStatus()` now derived via `Status.fromStatusCode(getStatusCode())`
  (exact round-trip — all constructors take the enum), and its own static
  `validate*` helpers still constructing/throwing `LegendSDLCServerException` so
  existing catch sites and the JAX-RS mapper are unaffected.
  `TestLegendSDLCServerException` kept unchanged as the compat pin; new
  `TestLegendSDLCException` covers the int-based API.

Decisions / deviations:

- **Constructor visibility loosened on relocated utilities**: `StringTools`'s
  private constructor became `protected` (and `IOTools` gained an explicit
  `protected` one, where it previously had an implicit public default) so the
  deprecated bridges can extend them. Net API effect: `new IOTools()` is no
  longer possible for outsiders — it was never meaningful.
- **Serialization compatibility**: `LegendSDLCServerException` keeps its
  `serialVersionUID`, but its serialized shape changed (status now an `int` in
  the superclass instead of an enum field). Java-serialized instances of the old
  class are not deserializable across this change. Judged acceptable: these are
  server-side HTTP exceptions, never persisted; noted for the release notes.
- **Split package `org.finos.legend.sdlc.server.tools` persists** for the
  deprecation cycle: bridges in `legend-sdlc-shared`, plus 6 unrelated server
  classes (`AuthenticationTools`, `BackgroundTaskProcessor`, …) in
  `legend-sdlc-server`. Pre-existing condition; it ends when the bridges are
  deleted. The server-only classes in that package are Phase 4/5 material.
- **No exception mapper for `LegendSDLCException` yet**, deliberately: nothing
  below the server throws it until Phase 2 moves code down. Phase 2 must (a) add
  a JAX-RS mapper (or extend `BaseExceptionMapper`) for the new base type and
  (b) audit `catch (LegendSDLCServerException …)` sites on any path that will
  call relocated code, since relocated code will throw the base type, which those
  catches do not match.

Explicitly *not* done (deferred to their phases):

- `legend-sdlc-server-shared`'s other contents (auth/session, `BaseServer`,
  mappers, `BaseResource`) are server-ware and stay put (L6-adjacent).
- The 6 `server.tools` classes in `legend-sdlc-server` (above).
- Anything in `legend-sdlc-project-files` package renames (`server.project.*` →
  `projectfiles`) — that is Phase 2 territory alongside the `SourceSpecification`
  decision.

## Phase 2 — Project structure extraction

**Status: complete.** Landed as four commits: EntitySourceDirectory promotion;
server pass-through handling for `LegendSDLCException`; write-side extraction
(`ProjectStructureUpdater`, seam R1); module creation and read-side move. External
impact is documented in [`project-structure-migration.md`](project-structure-migration.md).

What moved:

- **`legend-sdlc-project-structure` (L2)** now holds the read-side:
  `ProjectStructure` (write-side stripped), top-level `EntitySourceDirectory`,
  factories (`ProjectStructureFactory`, `ProjectStructureVersionFactory`,
  V0/V11/V12/V13), `ProjectStructurePlatformExtensions`, the `Simple*` config
  classes (`org.finos.legend.sdlc.project.structure`), the whole maven family
  (`…structure.maven`), and the extension SPI **interfaces**
  (`…structure.extension`) including `UpdateProjectStructureExtension` — an
  addition to the plan's list, forced because V11+ factories consume it; it is
  `ServiceLoader`-discovered, so external impls must re-key (bridge javadoc +
  recipe call this out; **no** dual-key lookup for it, unlike version factories —
  judged authoring-framework surface with likely no external registrants).
- **`ProjectStructureUpdater`** (server module, `server.project`): the write-side —
  `updateProjectConfiguration`, validators, legacy-dependency upgrading,
  `UpdateBuilder`. No server-infrastructure imports (destined for L3). **Seam R1**:
  the structure/extension/legacy-collect calls are folded into one private dispatch
  method with a do-not-extend javadoc.
- **Seam S2**: `ConfigurationProperty` + `ConfigurationPropertyType` in
  `legend-sdlc-model` (verbatim from the config-options plan §4.1; the enum's value
  set is a starter owned by that plan) and `getConfigurationProperties()` (default
  empty) on `ProjectStructureVersionFactory` and `ProjectStructureExtension`.
- **Dual-keyed factory lookup**: `ProjectStructureFactory.newFactory(ClassLoader)`
  also reads the *legacy* services resource manually and instantiates entries
  against the relocated base class (so re-keying can lag recompilation), deduped by
  class name. Beware stale `target/classes`: a non-clean build leaves the old
  services file behind and the legacy loader trips on it (`ClassNotFoundException`
  → `ExceptionInInitializerError`); `mvn clean` fixes it.
- **Exception migration**: relocated code throws `LegendSDLCException` (400/500 as
  before). Server-side: new `LegendSDLCExceptionMapper` (+ `ExtendedErrorMessage`
  routing, `BaseExceptionMapper.buildResponse(int, …)`), `BaseResource` and the pure
  rethrow guards widened to the base type. The 8 revision-api catch sites
  (`GitLabRevisionApi`, `FileSystemRevisionApi`) deliberately stay subclass-typed:
  they feed subclass-typed exception processors and guard backend-native code only.

Decisions / deviations:

- **Deliberate L2 API widenings** (all forced by the updater living outside L2's
  package — which is also Phase 3's geometry, so this was decided now, not deferred):
  `getProjectConfigurationFile` / `readProjectConfiguration` /
  `serializeProjectConfiguration` public (L2 owns the `project.json` format);
  comparators, `validateDependencyConflicts`, `isLegacyProjectDependency`,
  `newEntitySourceDirectory`, `SimpleProjectConfiguration.newConfiguration`/copy-ctor
  public; and the legacy 3-arg `collectUpdateProjectConfigurationOperations` hook
  protected→public (its deprecated 4-arg sibling was already public). Overriders
  updated in-repo; external overriders need `protected`→`public` (recipe step 3).
  Static-method widenings can break external code that *hides* those statics with
  `protected` re-declarations — accepted under the may-break framework contract.
- **No deprecated `newUpdateBuilder` forwarders on `ProjectStructure`**: the class
  left the server module in the same phase, so forwarders would only have lived for
  intermediate commits; the recipe documents the rename instead (deviation from
  extraction-doc step 8, which re-architecture §5 supersedes in spirit).
- **Extension impls** (`Default*`/`Base*`/`Simple*`/`Void*`) stay in the server and
  now implement the *relocated* interfaces via single-type imports that shadow the
  same-package bridge interfaces (JLS 7.5.1/6.4.1 — legal, slightly subtle; noted
  here so nobody "fixes" the imports away).
- **Tests stay in the server module** this phase (they exercise relocated classes
  through new imports; the characterization net is intact). Moving the pure
  read-side tests to L2 is deferred — natural to do in Phase 3 when the TCK starts.
- **`TestProjectStructure`'s five `assertThrows` now expect `LegendSDLCException`**:
  the thrown *type* from update flows changed by design; codes/messages asserted
  unchanged.

For Phase 3 (carry-ins):

- `ProjectConfigurationUpdater` is consumed by the updater but slated for L4 in the
  plan (§3.3 vs §6 tension). Resolve when the updater moves to L3 — likely the
  config-delta value object belongs at L3, not L4.
- `ProjectStructure.PROJECT_STRUCTURE_FACTORY` is a process-global static (loads
  factories from `ProjectStructure`'s classloader at class-init). Preserved as-is;
  it is on the §4.5 "no process-global mutable state below L4" audit list.
- ~~L1 package rename~~ *(done post-phase, see below)*; the `SourceSpecification`
  L1/L4 split was *not* done (plan permits deferral).

## Post-phase package renames (2026-07-08)

Two renames landed after the Phase 2 commits, both while the `reorg` branch is
still pre-release:

- **L2**: `org.finos.legend.sdlc.structure[.maven|.extension]` →
  `org.finos.legend.sdlc.project.structure[.maven|.extension]` (user decision on
  the package convention). Service key re-keyed to match; the dual-key legacy
  lookup still reads the historical `server.project` key.
- **L1 storage SPI**: `org.finos.legend.sdlc.server.project` →
  `org.finos.legend.sdlc.project.files` for `ProjectFileAccessProvider`,
  `ProjectFileOperation`, `ProjectFiles`, `ProjectPaths`, and the three
  `FileAccessContext` helpers (+ tests). **No deprecation bridges**: an interface
  with nested types cannot be aliased in a way that keeps consumer code
  compiling, and external L1 consumers are speculative (§5's promises cover
  extension implementors, not storage providers); the migration recipe documents
  the import rename instead. The `SourceSpecification`/`WorkspaceSpecification`
  taxonomy deliberately **keeps** its `server.domain.api.*` packages: §3.3
  earmarks it for the L1/L4 split, so its final package is a Phase 4
  design-review decision — renaming it now would mean renaming it twice.
- The old `org.finos.legend.sdlc.server.project` package now contains only
  server-side residents: `ProjectStructureUpdater` (L3-bound),
  `ProjectConfigurationStatusReport`, `config/`, and the concrete extension
  impls. The updater and same-package tests gained explicit imports of the
  relocated L1/L2 types.

## Phase 3 — SDLC core (L3)

**Status: in progress.**

### Step 1: characterization tests (before any move)

Two suites pin the duplicated entity access/modification behavior of
`GitLabEntityApi` and `FileSystemEntityApi` as it stands, so the Phase 3
factoring has a net. Both stay put (they pin the api classes, which do not
move):

- `TestGitLabEntityApiCharacterization` (server module, `gitlab.api` package):
  drives the real `GitLabEntityApi` over the test `InMemoryProjectFileAccessProvider`
  via a subclass overriding `getProjectFileAccessProvider()` (constructed with
  null GitLab config/user context/task processor — none is touched on non-GitLab
  paths; `buildException` passes `LegendSDLCServerException` through). Covers
  `getEntity`/`getEntities`/`getEntityPaths` (predicates, `excludeInvalid`),
  `updateEntities` (replace semantics, validation), `performChanges` (all four
  change types, validation, error statuses/messages). GitLab-native paths
  (review from/to contexts, GitLab error translation) are out of reach and out
  of scope.
- `TestFileSystemEntityApiCharacterization` (new test tree in
  `legend-sdlc-server-fs`, which previously had none; pom gains `junit` usage
  and the `legend-sdlc-model` test-jar): end-to-end over a real git repo in a
  temp directory via `FileSystemProjectApi.createProject` +
  `FileSystemWorkspaceApi.newWorkspace`.

Behavior observed and pinned (bugs preserved, not fixed — behavior-preserving
phase; candidates for post-phase fixes):

1. **Entity content does not round-trip identically** (both backends): the
   serializer normalizes content (a class gains `superTypes`, `stereotypes`,
   `constraints`, expanded `genericType`, …). What `getEntity` returns is the
   normalized form, not what was submitted.
2. **No-op suppression is byte-level, not content-level**: `updateEntities`
   with semantically identical entities still generates MODIFY changes, but
   `entityChangeToFileOperation` drops them when the re-serialized bytes equal
   the current file bytes → null revision (pinned: null revision on no-op
   updates and on empty change lists).
3. **RENAME moves the entity file without rewriting its content** (GitLab
   path): after a rename, the file at the new location still declares the old
   package/name, so reading the renamed entity fails with a path-mismatch
   deserialization error, while `getEntityPaths` happily lists the new path.
4. **`getEntityPaths` lists entity files it cannot parse** (it never
   deserializes); `getEntities(…, excludeInvalid=true)` silently drops them;
   `getEntities(…, excludeInvalid=false)` fails wholesale with 500.
5. **Non-validation failures are 500s** ("entity already exists",
   "could not find entity"), only argument validation is 400; unknown entity
   on `getEntity` is 404.
6. **FS: enumeration through the standard `FileAccessContext` is broken** —
   `FileSystemFileAccessContext.getFilesInCanonicalDirectories` compares
   canonical directory names (leading `/`) against git tree paths (no leading
   `/`), so it never matches; additionally it does `ObjectId.fromString(revisionId)`
   with the null revision id the entity access context is always created with.
   Consequences pinned: `getEntityPaths` **always throws** ("Error getting
   files in directories …"); `updateEntities` never sees existing entities —
   modifying an existing entity fails with "already exists" (500), and
   `replace=true` deletes nothing.
7. **FS: `getEntities` enumeration is platform-dependent** — the git-tree-walk
   variant relativizes canonical file paths with `java.nio.Path` + string
   concatenation, producing `\`-separated paths on Windows that match no
   source directory: `getEntities` returns entities on POSIX, empty on
   Windows (assertions conditional on `File.separatorChar`; POSIX branch
   verified by CI).
8. **FS: stale reference revision loses its 409**: the modification context
   correctly detects the conflict, but `FSException.getLegendSDLCServerException`
   re-wraps it into a 500 with a concatenated message.
9. **FS: `getEntities`' two overloads take different code paths** — the
   3-arg default and 4-arg `excludeInvalid` form go through the git tree
   walk, `getEntityPaths` through the (broken) standard context; GitLab's
   implementation routes everything through one path.

### Step 2: create `legend-sdlc-core` (L3); move the updaters into it

- **Package decision (user, 2026-07-08)**: the module is `legend-sdlc-core`
  (per the plan doc), rooted at `org.finos.legend.sdlc.core` — but with **no
  classes at the bare root**. Concern subpackages mirror the domain-API concern
  names: `core.entity` (model editing), `core.project` (configuration/structure
  write-side), `core.dependency`, `core.comparison`. Rationale: L3 spans model
  editing *and* project maintenance, so `…sdlc.project.core` was rejected as
  structure-slanted, and bare `…sdlc.core` as too general for classes to live
  in directly; the umbrella marks the module and keeps one JPMS-friendly root.
- **Moved (git mv)**: `ProjectStructureUpdater`
  (`server.project` → `org.finos.legend.sdlc.core.project`; **no bridge** — the
  class was created in Phase 2 on this branch and no release ever shipped it at
  the server FQN) and `ProjectConfigurationUpdater`
  (`server.domain.api.project` → `core.project`; `@Deprecated` bridge subclass
  left in `legend-sdlc-server`, with the caveat that the fluent `with*` methods
  return the relocated type). `TestProjectConfigurationUpdater` moved along.
- **Carry-in resolved — `ProjectConfigurationUpdater` placement (L3, not L4)**:
  it is a pure configuration-delta value object over L0 types, consumed by the
  L3 updater and needed by local tooling (adding a dependency to a checkout);
  the plan's §3.3 listing under L4 reflects the domain-API *interfaces*
  consuming it, which Phase 4 will re-export — the type itself belongs at L3.
- `ProjectConfigurationApi` (same package as the bridge) now single-type-imports
  the relocated type so its signatures bind to the L3 class, not the bridge
  (same JLS 7.5.1 shadowing pattern as the Phase 2 extension impls).
- Seam R1 unaffected: the updater's single write-side dispatch method moved
  verbatim, javadoc intact.

### Step 3: factor the duplicated entity logic into `core.entity`

- **New in `org.finos.legend.sdlc.core.entity`**: `EntityAccessOperations`
  (getEntity / getEntities / getEntityPaths / entity-project-file streaming with
  predicate filtering), `EntityModificationOperations` (updateEntities diffing,
  entity-change validation, change→file-operation translation, performChanges),
  and `EntityProjectFile` — a verbatim transplant of the GitLab implementation
  (which was identical to the FS one on these paths). Exception-context strings
  are parameterized: operations take a nullable `referenceInfo` description, so
  GitLab passes `getReferenceInfo(…)` and FS passes null /
  `String.valueOf(sourceSpecification)` — pinned messages preserved exactly.
- **`GitLabEntityApi` and `FileSystemEntityApi` delegate; they do not move.**
  GitLab keeps: review from/to access contexts (GitLab-native), its
  `buildException` error translation around every core call, null-validations.
  FS keeps: its git-tree-walk enumeration variant and its own
  `EntityProjectFile` with root-directory relativization (including the
  platform-dependent behavior pinned in Step 1), review contexts unsupported.
  FS's standard-variant enumeration and all duplicated write logic deleted in
  favor of core (observable behavior identical — the FS access context, where
  the Step 1 bugs live, is unchanged).
- **Core throws `LegendSDLCException`** (base type, framework-free), statuses
  and messages unchanged. `BaseGitLabApi.processException`'s pass-through
  branch (and `buildException`'s return type) widened from
  `LegendSDLCServerException` to the base `LegendSDLCException` so core-thrown
  exceptions keep their status/message through GitLab's error translation —
  first instalment of the Phase 2 catch-site audit carry-in. The
  characterization tests' `assertThrows` were deliberately widened to the base
  type in the same commit (Phase 2 precedent: thrown *type* may change to the
  base for relocated code; codes/messages stay pinned; HTTP behavior unchanged
  since the server maps both types identically).
- **Documented wrapping-boundary drift** (error-path edges, not pinned
  behavior): (a) GitLab's `updateEntities` now wraps the whole flow in
  `buildException` (previously only the inner performChanges compute+submit
  was wrapped), so a raw non-`LegendSDLCException` failure during the diffing
  phase — e.g. an unparseable entity file — now surfaces as "Failed to perform
  changes on …" (500) instead of a raw exception (still 500 via the generic
  mapper); (b) GitLab's unknown-entity 404 now passes through the identity
  branch of `buildException` instead of being thrown after the try block —
  same observable exception; (c) the debug-log lines moved with the logic, so
  their logger names are now `org.finos.legend.sdlc.core.entity.*`; (d) — the
  one the characterization net caught — `getEntities` over an unparseable
  entity file (`excludeInvalid=false`): the L2 deserialization
  `LegendSDLCException` used to be re-wrapped by `buildException` as "Failed
  to get entities for <context>: …", and now passes through directly ("Error
  deserializing entity from file …"; same 500, more precise message, request
  context lost). Accepted deliberately: the alternative — distinguishing
  "core-thrown" from "L2-thrown" base exceptions at the GitLab boundary — has
  no type-level expression, and re-narrowing the pass-through would break the
  404/400 preservation for core-thrown exceptions. Pin updated in the same
  commit.

### Step 4: dependency resolution and comparison logic into core

- **`core.dependency.DependencyOperations`**: the transitive upstream walk from
  `DependenciesApiImpl.searchUpstream`, parameterized over a
  dependency→configuration resolver. `DependenciesApiImpl` delegates, passing a
  resolver over its `ProjectConfigurationApi`. **Plan deviation, resolved on
  the record**: §3.3/§6 say `DependenciesApiImpl` "moves" to L3, but the
  domain-API interfaces it implements and consumes (`DependenciesApi`,
  `ProjectApi`, `ProjectConfigurationApi`, `RevisionApi`) are L4 material that
  stays in the server until Phase 4 — so in Phase 3 the backend-neutral *logic*
  moves and the api impl shrinks to wiring; the class itself moves in Phase 4
  with its interfaces. `getDownstreamProjects` stays as-is: its substance is
  project enumeration + a one-line dependency test; there is no L3-sized logic
  to extract.
- **`core.comparison`**: `FileDiff` (backend-neutral file-change description)
  and `ComparisonOperations` — `newComparison` (the entity-diff assembly
  factored verbatim from `GitLabComparisonApi`, consuming `FileDiff`s instead
  of gitlab4j `Diff`s) plus `compare`, the generic two-`FileAccessContext`
  walking comparison from plan §3.3 (byte-level; no rename detection — a move
  surfaces as delete+create). `GitLabComparisonApi` keeps its native GitLab
  compare call and delegates the assembly, translating `Diff` → `FileDiff`.
  The generic `compare` has no production caller yet (FS/in-memory comparison
  apis are stubs); it is exercised by the seam-R2 TCK seed and becomes the L3
  default behind the Phase 4 `AbstractBackend`.

### Step 5: seam S1 — namespaced configuration bags on the L0 model

- `ProjectConfiguration` (L0) gains `getStructureConfiguration()` /
  `getExtensionConfiguration()` (default empty maps) — the namespaced,
  version-/extension-scoped option bags of the config-options plan (§4.1–4.2).
  The two legacy flat booleans are now *defined* as structure-configuration
  options: `getRunDependencyTests()` / `getProduceShadedServiceJar()` are
  `@Deprecated` and their interface defaults read the bag, and
  `SimpleProjectConfiguration` exposes its (still field-stored) booleans
  through a read-only bag view. **No new top-level config booleans may be
  added** — new options go in the bags.
- **Wire format deliberately unchanged**: the bags are `@JsonIgnore`d at the
  interface, so neither `project.json` (written via L2's mapper over any
  `ProjectConfiguration` impl) nor REST payloads (Dropwizard bean
  serialization) gain keys; the flat booleans still serialize top-level from
  `SimpleProjectConfiguration`'s overriding getters. Migrating storage to the
  namespaced form (read-fold + write-normalization, §4.7) belongs to the
  config-options plan. `TestProjectConfigurationSerialization` (first test in
  the L2 module's new test tree) pins all of this; it is the pin that plan
  replaces when it lands.
- **`legend-sdlc-model` gains a `jackson-annotations` dependency** (its first
  compile dependency) to carry the `@JsonIgnore`. Alternative considered and
  rejected: keeping L0 annotation-free by scattering `@JsonIgnore` overrides
  across every implementation (`Simple*`, FS, in-memory, anonymous updater
  output) plus a mapper mix-in in L2 — covers the file format but not REST
  serialization of arbitrary impls, and is easy to get wrong for the next
  implementation. `jackson-annotations` is a small, dependency-free jar and
  the annotation is additive; noted here because L0 is the stable tier.
