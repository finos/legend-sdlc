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

### Step 6: seam R2 — TCK seed with the layout invariants

- **`LayoutInvariantsTestSuite`** (abstract, in `legend-sdlc-core`'s test tree,
  published as a test-jar; package `org.finos.legend.sdlc.core.tck`) expresses
  the layout-reconciliation plan's invariants as executable contract,
  parameterized over a `ProjectFileAccessProvider`: **update ≡ create**
  (updating from an older structure version — V11/V12 seeds — yields
  byte-identical layouts to a fresh build at the target version, entity
  migration included) and **reconciling an already-correct project is a
  no-op** (re-applying the current configuration produces no revision and no
  file changes). All three invariants **hold today** for the imperative
  write-side over the in-memory provider (`TestInMemoryLayoutInvariants`).
  This grows into `legend-sdlc-backend-test-suite` in Phase 4; reconciling
  structure versions must be certified by the same suite.
- **`InMemoryProjectFileAccessProvider` + `SimpleInMemoryVCS` moved to the L1
  module's test-jar** (`org.finos.legend.sdlc.project.files`, git mv from the
  server test tree): they are the L1 SPI's test double and the TCK's default
  harness, and Phase 5's in-memory backend builds on them. The server module
  consumes the test-jar; its tests' imports updated.
- **Phase 2 leftover found by the TCK**: the V11–V13 structure factories load
  test-template resources (`project/tests/v4/*.java`) from the classpath, but
  the resources had stayed in `legend-sdlc-server`'s jar when the factories
  moved to L2 — invisible until something exercised the factories without the
  server jar present. Resources moved (git mv) to
  `legend-sdlc-project-structure`. Related audit note:
  `MavenProjectStructure.loadTestResourceCode` resolves via the **thread
  context classloader** — fine under Maven/server, fragile in an embedded/IDE
  host; added to the §4.5 audit list alongside `PROJECT_STRUCTURE_FACTORY`.
- **The FS provider does not run the suite yet**: its standard-context
  enumeration is broken (Step 1 quirks 6–7 — `getFiles` throws on the null
  revision id, canonical-directory matching never succeeds), so the invariants
  cannot even be evaluated over it. Certifying the FS backend against the TCK
  is part of its Phase 5 refit, after those defects are fixed deliberately.
- `EntityModificationOperations.updateEntities`/`performChanges` signatures
  widened from `WorkspaceSourceSpecification` to `SourceSpecification` (the L1
  type): the L1 provider is source-agnostic, the TCK and local tooling edit at
  project level, and the api delegates pass workspace specs unchanged.

### Phase 3 wrap-up: carry-ins and audit items

- **`ProjectConfigurationUpdater` placement (Phase 2 carry-in)**: resolved — L3
  (`core.project`), see Step 2. The class moves *again* only in the sense that
  Phase 4's domain-API interfaces will keep referring to it; the type itself is
  settled at L3.
- **`catch (LegendSDLCServerException)` audit (Phase 1/2 carry-in)**: the paths
  that newly call L3-thrown code are the GitLab entity/comparison delegates —
  covered by widening `BaseGitLabApi.processException`/`buildException` to the
  base type (Step 3). A repo-wide sweep finds the only remaining
  subclass-typed catches are the 8 revision-api sites (GitLab + FS), which
  Phase 2 already ruled on: they feed subclass-typed exception processors and
  guard backend-native code that never calls L3. No change.
- **§4.5 process-global state audit list** (unchanged status, restated):
  `ProjectStructure.PROJECT_STRUCTURE_FACTORY` (classloader-captured factory
  at class-init, now in L2) and — added this phase —
  `MavenProjectStructure.loadTestResourceCode`'s use of the thread context
  classloader. Both are pre-existing behavior, preserved; they must be
  addressed before Phase 6 declares L0–L3 embeddable.
- **Module inventory after Phase 3**: `legend-sdlc-core` (L3) holds
  `core.entity` (entity read/write over L1+L2), `core.project` (the
  configuration/structure write-side: `ProjectStructureUpdater` with seam R1's
  single dispatch point, `ProjectConfigurationUpdater`), `core.dependency`
  (upstream dependency walking), `core.comparison` (entity-diff assembly +
  generic walking comparison), and the TCK seed (`core.tck`, test-jar). The
  GitLab/FS api classes remain in their modules as delegating shells, to move
  (GitLab) or be refit (FS) in Phases 4–5.
- **Not done, deliberately**: no Phase 4 API shapes (no `Backend` aggregate,
  no capability model, no domain-API interface moves — all pending the Phase 4
  design review); the FS provider's characterized defects are documented, not
  fixed; the `SourceSpecification` L1/L4 split remains deferred (Phase 4
  design-review decision, per the Phase 2 record).

**Status: complete** pending the full-reactor verification build recorded in
the closing commit.

### Correction (2026-07-09, user review): seam S1 implementation reverted

Step 5's implementation is reverted on two user rulings:

1. **`legend-sdlc-model` takes no dependencies.** The `jackson-annotations`
   dependency (added to `@JsonIgnore` the new bag accessors) is removed; the
   constraint is now stated in re-architecture §5.
2. **The bags do not belong at this stage.** They had no consumer in Phase 3,
   and the entire `@JsonIgnore` apparatus existed only because they arrived
   *ahead of* their persistence: when the config-options plan introduces them
   together with §4.2/§4.7 (values persisted from day one), no serialization
   suppression is needed at all. Landing them early created an artificial
   interim state and an L0 dependency to hold it up.

What Phase 3 now does for S1 — the seam's actual substance, re-worded in both
plan docs: the **negative obligation**. No new top-level config booleans; no
new updater API designed around the existing two flat ones (both were already
true); a pointed javadoc on the two boolean getters directing new options to
the future bags. `SimpleProjectConfiguration` and `ProjectConfiguration`
restored to their pre-Step-5 shape. The L2 wire-format pin
(`TestProjectConfigurationSerialization`) is kept in slimmed form — the flags
serialize top-level, present only when set — as the baseline the config-options
plan deliberately replaces.

## Phase 4 — Backend SPI (L4)

**Status: complete** (design review 2026-07-09; implementation landed
2026-07-09/10 as Steps 1–8 below, one commit per step, each on a green
full-reactor `mvn install javadoc:javadoc`).

### Design review (2026-07-09): the six SPI decisions, on the record

Plan §6 gates Phase 4 on a design review of the SPI details before code. Held
against the plan (§3.2–3.5, §4.5–4.6, §6, §7), the Phase 1–3 record above, and
the code on `reorg`. Six decisions; plan-doc amendments they force are listed at
the end.

#### 1. Session/auth contract: `Backend` vs. per-user `BackendSession`

Two-object contract. **`Backend`** is deployment-scoped, built once by the
factory: `getType()`, `getCapabilities()`, `newSession(BackendSessionContext)`,
`close()` (AutoCloseable; L6 ties it to the Dropwizard lifecycle).
**`BackendSession`** is the per-user view, and **the domain APIs hang off the
session, not the backend**: GitLab's api objects are meaningless without a user,
and putting them on `Backend` would re-introduce ambient per-request state — the
Guice request-scope coupling being removed. Sessions are cheap, created per
request by an L6 provider, never cached across requests by the server; a backend
may return a shared stateless instance (the contract promises nothing about
object identity).

What crosses the SPI, derived from `GitLabUserContext` (the only nontrivial
session today — it lazily builds a `GitLabApi`, refreshes OAuth tokens,
*writes back* session cookies, and *initiates* 302 redirects):

- **Identity as data**: `BackendSessionContext.getUserId()`. The Guice/servlet
  `UserContext` stays at L6; the L4 context is new and framework-free (pac4j
  appears only in the L6 adapter that builds the context from the pac4j
  `Session`).
- **Per-user persistent state as a port**: `BackendSessionContext` exposes a
  minimal string-keyed state store, implemented at L6 over the pac4j session +
  `LegendSDLCWebFilter` cookie write-back. `GitLabSession`'s token fields become
  GitLab-backend-owned state serialized into that store; pac4j types never
  cross the SPI.
- **Interactive re-auth as a typed exception**: L4 defines
  `AuthorizationRequiredException(URI authorizationUri)`; L6 maps it to 302
  (redirect allowed) or 403 with the `auth_uri` body — exactly today's two
  branches in `GitLabUserContext.setGitlabTokenForSession`.
- **The auth-flow surface goes generic**: GitLab and FS already ship
  route-identical `/auth` resources (`authorize`, `callback`,
  `termsOfServiceAcceptance`) — de-facto proof of a generic contract.
  `BackendSession` gains `isAuthorized()`, authorize/callback handling, and
  terms-of-service messages; one L6 `AuthResource` delegates to it, and the two
  per-backend resources are deleted in Phase 5.

Litmus test (plan §7) verified: the FS session is `{userId}` +
`isAuthorized() → true`, never touches the state store, returns the backend's
stateless api objects — trivial. Server-level authentication (pac4j filters,
who may reach the server at all) remains L6 policy independent of the backend.

Two contract notes to pin in javadoc: `WorkspaceSpecification`'s null `userId`
("assumed to be the current user") resolves against the session's user — the
semantics exist today but are implicit; and L6 keeps per-API `@Provides`
bindings (`EntityApi ← session.getEntityApi()`), so resources keep their
current injected types and "rewritten only in their injection" (§3.3) shrinks
to an import rename.

#### 2. Capability enumeration and HTTP mapping

`BackendCapability` enum in L4, additive, serialized as strings (clients must
tolerate unknown values): `REVIEWS`, `WORKFLOWS`, `VERSIONS`, `PATCHES`,
`BUILDS`, `BACKUP`, `ISSUES`, `CONFLICT_RESOLUTION`, `USER_WORKSPACES`,
`GROUP_WORKSPACES`.

- **Core, never capabilities**: projects, workspaces (a backend declares at
  least one of USER/GROUP), revisions, entities, project configuration,
  dependencies, comparison — L3-defaulted or provider-derived per §3.2.
- `CONFLICT_RESOLUTION` and `BACKUP` are capabilities keyed on the provider
  supporting the corresponding `WorkspaceAccessType`s; their mechanics are
  L3-generic, so declaring them is nearly free where the access types work.
- Capabilities are **deployment-static** (`Backend.getCapabilities()`).
  Per-user permission failures remain 403s from the APIs — capabilities
  describe the deployment, not the caller.
- Enforcement sits in two places, both TCK-certified: `BackendSession`
  accessors for whole-concept APIs throw `UnsupportedCapabilityException`
  (L4, carries the capability) for undeclared ones; and the L3-default
  implementations throw it from *cross-API* scope methods (e.g.
  `EntityApi.getVersionEntityAccessContext` under absent `VERSIONS`).

HTTP mapping: one JAX-RS mapper, `UnsupportedCapabilityException` → **501**
with body `{"capability", "backendType", "message"}`. 501 over 404 because 404
already means "no such project/workspace/entity/review" on virtually every
route — overloading it would make feature-absence indistinguishable from an id
typo. Route → capability is by resource concern (reviews ← REVIEWS, workflows ←
WORKFLOWS, version-scoped entity/config/pmcd routes ← VERSIONS, …);
patch-scoped review/workflow routes require the conjunction. Behavior change is
confined to endpoints that today throw raw `UnsupportedOperationException`
(500) — with one flagged exception: FS's two `getReviews` overloads return
empty lists today and would 501 under the model. Consistency wins on the
record, but the Phase 5 FS refit must verify Studio/omnibus tolerance before
flipping them (Phase 4 itself changes no FS behavior; GitLab declares
everything).

#### 3. `Backend`/`BackendFactory`/`BackendEnvironment` shapes; `backend:` config

`BackendFactory` as sketched in §3.5 (`getType()`, `getConfigurationClass()`,
`build(config, environment)`), registered via `META-INF/services`; lookup and
selection stay at L6 (the backend `ServiceLoader` is expressly an L6 concern
per §4.5). `BaseLegendSDLCServer`'s `mode` string and GitLab hard-wiring die;
`GITLAB_MODE` is deprecated.

`BackendConfiguration` is an abstract Jackson-polymorphic base in L4,
discriminator `type`, **subtype fields inline** — amending §3.5's sketch, which
nested a second `gitlab:` block under `backend:`. One wrapper less, standard
subtype resolution; subtypes are registered at bootstrap from the
ServiceLoader'd factories (`getType()` is the type name):

```yaml
backend:
  type: gitlab
  server: { ... }   # gitlab fields inline
  app: { ... }
```

Legacy-config adapter for one transition release: top-level `gitLab:` present
and `backend:` absent ⇒ synthesized `backend: {type: gitlab, …}`.

`BackendEnvironment` (deployment-scoped host services; L4 interface, grows by
`default` methods): the object mapper; the task processor
(`BackgroundTaskProcessor` relocates out of `server.tools` to a framework-free
home so L4 can name it — Phase 1 flagged it as Phase 4/5 material); and the
deployment's structure context — `ProjectStructureExtensionProvider` and
`ProjectStructurePlatformExtensions` (L2 types). The structure context passes
*through* the environment to `AbstractBackend`'s L3-default wiring: the backend
jar still bundles no extensions (§6 seam-S3 obligation) — the deployment
supplies them, the generic code consumes them. Explicitly **not** in the
environment: anything per-user (that is `BackendSessionContext`), and metrics
(deferred; additive later). The server config class
(`ProjectStructureConfiguration`) itself stays at L6; whatever else generic
code needs from it crosses as data via environment accessors.

`AbstractBackend` (L4): constructor takes the environment; a backend supplies
its `ProjectFileAccessProvider`, ProjectApi, WorkspaceApi, and any natively
implemented optional APIs; it inherits the L3-default
entity/configuration/dependencies/comparison implementations and the
capability plumbing.

#### 4. Discovery surface (seam S3) and the §4.6 provider-acquisition question

One read-only surface under the existing `/configuration` root (already the
deployment-describing resource):

- `GET /configuration/capabilities` → `{backendType, capabilities[]}` — Studio
  adapts its UI instead of harvesting 501s.
- `GET /configuration/projectStructureVersions` → per structure version:
  version, extension versions, and `configurationProperties[]`; per extension
  version likewise. This is the "describe what this structure/extension
  supports" call; `ConfigurationProperty`/`ConfigurationPropertyType` (seam S2,
  already at L0) are its schema vocabulary, and the config-options plan's
  discovery phase extends **this same call** (S3 satisfied: no parallel
  endpoint later; response shape evolves additively).

§4.6 answered on the record, from one governing fact: **discovery serves schema
and identity, never behavior.** A `ProjectStructureExtension` is executable
Java that computes files; no endpoint ships that. Therefore:

- Option (b) holds for everything fetchable: option schemas, extension
  names/versions, the capability set — enough for an IDE to render forms,
  validate values, and know the deployment's extension lineup. Fetched from the
  owning server and cached.
- Executable extension behavior reaches local tooling only as jars — bundled
  (a) or user-configured (c); choosing between those is plugin packaging, not
  an SDLC-server contract.
- Degraded mode (provider absent), picked explicitly from §4.6's menu: entity
  editing is always permitted; structure/configuration edits are permitted but
  **leave extension-managed files untouched**, reconciled when the change
  returns through the server — the reconciliation-friendly branch, which the
  layout-reconciliation plan later makes automatic. Blocking those edits
  instead would strand IDE users on trivial dependency bumps.
- Deployment identity: explicit tool configuration (the SDLC server URL, a
  per-checkout/workspace setting). No inference from Git remotes (hosting ≠
  deployment, §3.3) and no `project.json` change (wire format is a non-goal).
- Corollary confirmed for Phase 6: `legend-sdlc-local`'s structure-aware
  operations take an explicit `ProjectStructureExtensionProvider` and behave
  per the degraded mode when it is absent.

#### 5. The deferred `SourceSpecification` L1/L4 split: **rescinded**

The taxonomy is finalized at L1; §3.3's end state (opaque L1 handle, taxonomy
at L4) is amended. Grounds:

1. The feared dependency does not exist. The whole hierarchy
   (`SourceSpecification` + four subclasses + visitors,
   `WorkspaceSpecification`, `WorkspaceSource`) compiles against L0 only
   (`VersionId`, `WorkspaceType`) plus L1's own `WorkspaceAccessType` — which
   was always a `ProjectFileAccessProvider` nested enum. The "conceptual server
   dependency" was the package name, removed by rename.
2. Generic code genuinely consumes the taxonomy: L3's `ProjectStructureUpdater`
   visits `WorkspaceSourceSpecification` and unwraps the workspace spec
   (`core.project.ProjectStructureUpdater`, workspace-branch validation), and
   the TCK addresses providers at project and workspace level. An opaque L1
   handle would not remove the taxonomy — it would push L3 and every provider
   to downcast, trading type safety for nothing.
3. There is no L1-only implementor to protect: §3.2's minimal backend contract
   already includes `WorkspaceApi`, and the one true L1-only provider —
   `legend-sdlc-local`'s directory context — ignores source specifications
   entirely.
4. Sealedness (package-private constructors, total visitors) is a feature for
   generic code and the TCK; splitting subclasses across layers would break it.

Consequence: Phase 4 renames the packages **once**, to their final home in the
L1 module: `org.finos.legend.sdlc.project.source` (source specifications +
visitors) and `org.finos.legend.sdlc.project.workspace`
(`WorkspaceSpecification`, `WorkspaceSource*`) — following the 2026-07-08
`project.*` convention (flagged: `project.files.source` is the alternative if
the user prefers module-aligned naming). No bridges — same ruling and same
population as the Phase 2 L1 rename (§5's promises cover extension
implementors, not storage-SPI consumers); the migration recipe documents the
import rename. The L4 domain interfaces keep trafficking in these L1 types
directly; no re-export layer.

#### 6. Delegating api classes to L4 alongside their interfaces

- **Interfaces**: `server/domain/api/**` →
  `org.finos.legend.sdlc.backend.api.<concern>` (concern subpackages preserved;
  no classes at the bare root, per the L3 precedent). Old FQNs remain as
  `@Deprecated` bridge **interfaces** extending the relocated ones — the
  Phase 2 extension-SPI interface-bridge pattern — because external server
  assemblies (the origin project's among them) reference these types in Guice
  modules and resource code, and interfaces bridge cleanly.
- **`DependenciesApiImpl`** — the Phase 3 Step 4 deviation closes as recorded:
  it moves to L4 as the default implementation,
  `…backend.api.dependency.DefaultDependenciesApi`, wired by `AbstractBackend`
  over ProjectApi + ProjectConfigurationApi + RevisionApi and L3
  `DependencyOperations`. `@Deprecated` constructor-forwarding bridge subclass
  stays at the old FQN in the server (both Guice modules bind it by FQN today;
  external assemblies may too).
- **Comparison**: there is no backend-neutral impl class to move — GitLab's is
  native (gitlab4j, stays put and moves to L5 in Phase 5), FS's is a stub that
  dies in the Phase 5 refit. Phase 4 instead *creates*
  `…backend.api.comparison.DefaultComparisonApi` over L3
  `ComparisonOperations.compare`, which finally gains its production caller
  (anticipated by Phase 3 Step 4): workspace comparisons (creation/source)
  fully generic via provider contexts + RevisionApi; review comparisons
  defaulted only when `REVIEWS` is declared (review → workspaces/revisions →
  compare), with native override expected (GitLab keeps its compare API).
  `AbstractBackend` wires it; the TCK certifies it.
- `ProjectConfigurationApi` continues to consume L3's
  `ProjectConfigurationUpdater` (settled in Phase 3 Step 2); an L4 interface
  importing an L3 type is the correct dependency direction — no action.
- Inventory note: `TestModelBuilder` (`domain/api/test`) is classified during
  the move by dependency audit; not part of this decision.

#### Deliberately not decided

Metrics on `BackendEnvironment` (additive later); `IssueApi`'s long-term fate
(the `ISSUES` capability suffices); the backend-neutral contract documentation
for each API (§7) — Phase 4 implementation work, not review scope; the §4.5
process-global-state audit items (`PROJECT_STRUCTURE_FACTORY`, TCCL resource
loading) remain open and gate Phase 6, not Phase 4.

#### Plan-doc amendments these decisions force — applied 2026-07-09

User ratified decisions 5 (split rescinded) and 2 (501 over 404) explicitly and
directed the amendments be made; applied to `re-architecture.md` the same day:

- §3.3 L1 bullet: the `SourceSpecification` split rescinded (decision 5);
  taxonomy final at L1 with renamed packages. §3.3 L6 bullet: `404`/`501`-style
  → decided `501` with structured body (decision 2).
- §3.4 (consistency pass, beyond the original list): decided capability starter
  set replaces the example enum; `Backend.getReviewApi()` →
  `BackendSession.getReviewApi()` + L3 cross-API enforcement; capabilities
  endpoint now definite; "minimal backend" wording adjusted (empty capability
  set is no longer literal — at least one workspace flavor is declared).
- §3.5: config sketch now inline-fields polymorphic form with the legacy-config
  adapter noted; the `Backend.forUser(UserContext)` sketch replaced by the
  decided `Backend`/`BackendSession`/`BackendSessionContext` contract;
  `BackendEnvironment` bullet rewritten to the decision-3 shape.
- §6 Phase 4 bullet: review-held banner, L4 package root + bridge interfaces,
  `DefaultDependenciesApi`/`DefaultComparisonApi`, source/workspace package
  rename, per-API session providers, discovery endpoints; sequencing note marks
  the review held with this section as the authoritative record.
- §7 rows: "SourceSpecification split" → resolved-rescinded; "session/auth
  contract" → contract decided, residual risk = implementation faithfulness on
  real GitLab auth flows; "managed projects edited locally" → decided
  (schema-not-behavior, degraded mode, explicit server URL), confirm in
  Phase 6.

Deliberately untouched: §4.6's body still narrates the question and its menu —
it defers to this review for the answer, which the §7 row now carries; stamping
§4.6 itself is cosmetic and can ride along with any future §4 edit.

### Step 1: source taxonomy to its final L1 packages (decision 5)

- **Moved (git mv, no bridges)**, within `legend-sdlc-project-files`:
  `server.domain.api.project.source.*` (7 classes) →
  `org.finos.legend.sdlc.project.source`; the six workspace-spec classes
  (`WorkspaceSpecification`, `WorkspaceSource`, `Project`/`PatchWorkspaceSource`,
  `WorkspaceSourceVisitor`/`Consumer`) from `server.domain.api.workspace` →
  `org.finos.legend.sdlc.project.workspace`. The L1 module now contains no
  `server.*` packages at all.
- **Split package resolved**: `server.domain.api.workspace` had been split
  across the L1 module (the specs) and the server (`WorkspaceApi`); the server
  file gained explicit imports of the two relocated types it had been using
  same-package. `WorkspaceApi` is now the old package's only resident, pending
  its own Step 2 move.
- References updated repo-wide by scripted rewrite (~200 files); checkstyle's
  `CustomImportOrder` does not enforce intra-group alphabetical order (verified
  against existing files), so in-place import rewrites are order-safe.
- `project-structure-migration.md` gains two rows for the renames (external
  storage-SPI consumers: update imports and recompile; the domain API
  interfaces themselves are untouched by this step and relocate with bridges in
  Step 2).
- Verified: full-reactor `mvn install javadoc:javadoc` green (tests included).

### Step 2: `legend-sdlc-backend-api` (L4); domain API interfaces move

- **New module `legend-sdlc-backend-api`** (L4): depends on model, shared,
  project-files, core. All `server/domain/api/**` interfaces moved (git mv) to
  `org.finos.legend.sdlc.backend.api.<concern>` — concern subpackages preserved,
  except `conflictResolution` normalized to `conflictresolution` (house
  lowercase style, matching the FS module's precedent).
  `ProjectConfigurationStatusReport` moved in from `server.project`. Left
  behind in the server: `DependenciesApiImpl` (relocates in Step 4),
  `TestModelBuilder` (server utility — consumes the depot `MetadataApi`, so it
  is L6-bound and only its imports changed), and the `ProjectConfigurationUpdater`
  bridge.
- **Bridges**: `@Deprecated` bridge interfaces at every old FQN
  (Phase 2 extension-SPI pattern — old extends relocated; nested member types
  are inherited, so `WorkspaceApi.WorkspaceUpdateReport` etc. still resolve
  through the bridges). **`NewVersionType` is an enum and cannot be bridged** —
  documented in the migration recipe. `ProjectRevision` (class) bridged as a
  constructor-forwarding subclass.
- **De-servered on the way (the review's L4-cleanliness obligation)** — the
  relocated interfaces now have zero `org.finos.legend.sdlc.server.*` imports:
  - `ProjectApi.configureProjectInWorkspace(GitLabProjectId, …)` **dropped from
    L4**: it has no resource callers — it is GitLab-internal (called by
    `GitLabProjectApi`/`GitLabWorkspaceApi`; FS and in-memory only stubbed it).
    Kept abstract on the deprecated bridge for external implementors; the
    GitLab-internal call now goes through the concrete class (cast at the one
    `GitLabWorkspaceApi` site); the FS/in-memory stubs are deleted.
  - `ConflictResolutionApi.acceptConflictResolution` now takes
    `(message, List<? extends EntityChange>, revisionId)` instead of the
    Jackson application bean `PerformChangesCommand` (which stays at L6). The
    command-taking overloads (including the deprecated user/group-workspace
    forms) live on the bridge as delegating defaults; the four resources unwrap
    the command themselves and call the neutral method (two of them had been
    calling deprecated overloads).
  - `VersionApi`/`BuildApi` default methods throw `LegendSDLCException(…, 400)`
    instead of JAX-RS-typed `LegendSDLCServerException` (Phase 1/2 pattern;
    wire behavior unchanged).
- Same-package shadowing imports added where server classes remained in bridge
  packages (`DependenciesApiImpl`, `TestDownstreamProjectSearch`) — the JLS
  7.5.1 pattern already on record from Phases 2–3.
- Build note: **PMD is active in the build** alongside checkstyle
  (`UnnecessaryImport` caught a same-package import the scripted rewrite
  produced).
- Migration doc: one new row covering the interface relocation, the unbridged
  enum, and the three relocated-shape changes.

### Step 3: the SPI and capability model (`backend.api.spi`)

- **New package `org.finos.legend.sdlc.backend.api.spi`** (SPI machinery vs the
  `backend.api.<concern>` domain interfaces): `Backend` (type / capabilities /
  `newSession` / `close`, `AutoCloseable`), `BackendSession` (the 17 domain-API
  accessors + `getUserId()`; contract javadoc pins the review's rulings — cheap
  per-request creation, no identity guarantees, capability-gated accessors,
  null-userId workspace specs resolve to the session user),
  `BackendSessionContext` (user id as data + `BackendSessionStateStore`, the
  host-implemented per-user string store for e.g. OAuth tokens),
  `BackendFactory`, `BackendConfiguration` (Jackson-polymorphic base,
  `@JsonTypeInfo` by `type`, inline subtype fields), `BackendEnvironment`
  (object mapper, task processor, the deployment's extension provider +
  platform extensions — pass-through, never backend-bundled),
  `BackendCapability` (the decided starter enum, javadoc per constant),
  `UnsupportedCapabilityException` (extends `LegendSDLCException` with 501
  baked in, so the existing exception mapper already produces the right status;
  carries capability + backend type for the structured body later), and
  `AuthorizationRequiredException` (URI as data; 403 default, the auth
  resource turns it into 302 where redirects are allowed).
- **The auth-flow surface (authorize/callback/terms-of-service) is deliberately
  NOT on `BackendSession` yet**: it lands in Step 5 shaped against GitLab's
  real flows rather than designed blind — same phase, no SPI break.
- **`BackgroundTaskProcessor` relocated** `server.tools` →
  `org.finos.legend.sdlc.backend.api.tools` with a constructor-forwarding
  deprecated bridge at the old FQN (nested `Task`/`RetryableTask` resolve
  through the bridge). It could not go to `legend-sdlc-shared`: shared has
  zero compile dependencies (L0 tier) and the class needs slf4j.
- backend-api pom gains project-structure, jackson-annotations,
  jackson-databind, slf4j-api.

### Step 4: `AbstractBackend` and the L4 default implementations

- **`DefaultDependenciesApi`** (`backend.api.dependency`): `DependenciesApiImpl`
  relocated and renamed (git mv), `@Inject` stripped — L4 takes no
  `javax.inject`; the old FQN remains in the server as a `@Deprecated`
  constructor-forwarding bridge *with* the `@Inject`, and both Guice modules
  keep binding it until Step 6 switches to session providers.
- **`DefaultComparisonApi`** (`backend.api.comparison`, new): the generic
  `ComparisonApi` over a `ProjectFileAccessProvider` + L3
  `ComparisonOperations.compare` — which thereby gains its production caller,
  as anticipated in Phase 3 Step 4. Revision semantics preserved from the
  GitLab native implementation: workspace-creation = base → current of the
  workspace source; workspace-source = source HEAD → workspace HEAD (from/to
  order preserved). Review comparisons resolve the review via a supplied
  `ReviewApi` supplier and assume a project-source workspace (the `Review`
  model carries no workspace source) — backends with patch-scoped reviews
  override; the supplier is wired to the session's `getReviewApi()`, so the
  REVIEWS capability gate lives in exactly one place.
- **`AbstractBackend`** (`backend.api.spi`): ctor takes
  (type, capabilities, environment); inner abstract `Session` implements
  `BackendSession` with the two defaults above wired in, capability-gated
  throwing accessors for the eight optional APIs, and an abstract
  `getProjectFileAccessProvider()` — the §3.2 minimal-contract shape.
  **`getEntityApi()` stays abstract for now**: a `DefaultEntityApi` over
  `core.entity` operations is the natural first task of the Phase 5 FS refit
  (both current backends have delegating shells already; nothing in Phase 4
  needs the api-level default).
- backend-api pom gains eclipse-collections (api + impl).

### Step 5: the GitLab `Backend` (in the server module, pending Phase 5)

- **Auth-flow surface lands on `BackendSession`** (closing the Step 3
  deferral), shaped from the two backends' route-identical `/auth` resources:
  `isAuthorized()`, `authorize()` (throws `AuthorizationRequiredException`
  when interaction is needed), `handleAuthorizationCallback(code, state)`,
  `getUnacceptedTermsOfService()` — all with `default` implementations that
  make a no-auth backend's session trivially correct (the litmus test,
  literally).
- **`GitLabBackend extends AbstractBackend`** (`server.gitlab`; declares all
  ten capabilities): per-request `Session` constructs the existing GitLab api
  classes exactly as Guice does today (deployment inputs from the backend +
  environment; `GitLabUserContext` from the session context), inherits the
  `DefaultDependenciesApi` wiring (which matches today's `DependenciesApiImpl`
  composition exactly), overrides comparison natively, and implements the auth
  surface by delegation (`isUserAuthorized`, `getGitLabAPI(true)`,
  `gitLabAuthCallback`; terms-of-service logic replicated from the resource,
  which keeps its copy until Phase 5 rewires it onto the session).
- **Staging decision, on the record**: in Phase 4 the GitLab backend reaches
  its servlet-bound machinery by unwrapping the server's
  `ServletBackendSessionContext` (which wraps `UserContext`); pac4j/servlet
  types are legal here because GitLab code *is* at L6 until Phase 5. The
  context's state store is request-transient for now — persistent write-back
  (session store + cookie refresh) lands when the first backend actually
  consumes the store, i.e. when GitLab leaves the server and its
  `GitLabSession` token fields are re-plumbed through it. The existing GitLab
  auth resources are untouched; the generic `/auth` resource that consumes the
  session surface is Phase 5 work.
- **`BackendEnvironment.getService(Class)`** added (default null): a typed
  escape hatch for backend-specific needs outside the SPI proper —
  `GitLabBackendFactory` uses it to obtain the server's
  `ProjectStructureConfiguration` (which `GitLabProjectApi` consumes but the
  environment deliberately does not name; empty-config fallback preserved).
- **`GitLabBackendConfiguration`** (`backend: {type: gitlab, …}`, fields
  inline, built on `GitLabConfiguration`'s creator; the legacy `uat`/`prod`
  mode sections are not carried into the new form) and
  **`GitLabBackendFactory`** registered under
  `META-INF/services/…spi.BackendFactory`.
- `GitLabApiWithFileAccess.getProjectFileAccessProvider()` widened
  protected→public so the session can supply the L1 provider (feeds the
  inherited comparison default; GitLab itself overrides comparison natively).

### Step 6: the server consumes the `Backend`; polymorphic `backend:` config

- **Configuration**: `LegendSDLCServerConfiguration` gains the polymorphic
  `backend:` section; `BaseLegendSDLCServer` registers each ServiceLoader'd
  factory's configuration class as a Jackson subtype at bootstrap. The
  **legacy adapter runs in both directions**: `getBackendConfiguration()`
  synthesizes a GitLab backend config from a legacy top-level `gitLab:`
  section (a legacy deployment needs no config change), and
  `getGitLabConfiguration()` falls back to the one embedded in
  `backend: {type: gitlab}` (so the GitLab bundle, app info, and auth
  machinery work under the new form).
- **`GITLAB_MODE` deprecated and no longer consulted**: the GitLab bundle is
  added unconditionally and now no-ops (log + return, previously threw) when
  no GitLab configuration is present; all GitLab Guice bindings are gated on
  configuration presence, not the mode string.
- **`BaseModule` rewired**: the sixteen per-interface GitLab api bindings are
  replaced by a `@Singleton Backend` provider (resolves the configured
  `BackendConfiguration` to its factory by configuration class and builds it
  with the environment), a `@Singleton BackendEnvironment` (assembled from the
  already-bound extension provider / platform extensions / task processor;
  `getService` publishes `ProjectStructureConfiguration`; the object mapper is
  a plain `Jackson.newObjectMapper()` for now — revisit whose mapper the
  environment should carry when a backend actually consumes it), a
  `@RequestScoped BackendSession` provider
  (`backend.newSession(new ServletBackendSessionContext(userContext))`), and
  sixteen one-line per-API `@Provides` methods reading from the session — so
  the ~200 resources keep their injected types untouched, as decided.
  `UserContext` binding became an overridable hook: `BaseModule` binds it to
  `GitLabUserContext` when GitLab is configured (the session context must wrap
  the GitLab-typed context until the Phase 5 re-plumb); `InMemoryModule` and
  the FS module are untouched (they bind api interfaces directly and keep
  doing so until their Phase 5 refit).
- **`UnsupportedCapabilityExceptionMapper`** registered in
  `BaseLegendSDLCServer.run`: 501 with `{capability, backendType, message}` —
  more specific than the `LegendSDLCException` mapper, so Jersey selects it
  automatically. It sits in `server.backend` (not `server.error`, which would
  split server-shared's package).
- `DependenciesApi` stays commonly bound to the bridge (`DependenciesApiImpl`)
  for all modules — identical composition to the session default; switching it
  to the session is deferred to the Phase 5 module refits to keep
  `InMemoryModule` untouched.

### Step 7: discovery endpoints under `/configuration` (seam S3)

- **`GET /configuration/capabilities`** → `{backendType, capabilities[]}` from
  the injected `Backend`; **`GET /configuration/projectStructureVersions`** →
  per supported structure version: its `ConfigurationProperty` schema (seam S2
  surface) and its extension versions with theirs — the "describe what this
  structure/extension supports" call the config-options plan extends (S3
  discharged: no parallel endpoint later). Both live on the existing
  `ConfigurationResource`.
- Additive L2 surface to enumerate: `ProjectStructureFactory.getVersionFactory(int)`
  and `ProjectStructure.getDefaultProjectStructureFactory()` (a read-only
  accessor for the §4.5-audited process-global; the audit item stands).
- The resource injects `Provider<Backend>` (deref only in the capability call),
  and the backend/environment `@Provides` moved from `BaseModule` up to
  `AbstractBaseModule`, so `InMemoryModule`-based tests keep a valid injector
  graph; `FSModule` gets an interim throwing `Backend` provider (the FS server
  predates the SPI until its Phase 5 refit). Hitting `/configuration/capabilities`
  on the FS or in-memory servers errors until then — the endpoints are new, so
  nothing regresses.

### Step 8: `legend-sdlc-backend-test-suite` (L4 TCK)

- **New module** (published jar of abstract JUnit suites, junit at compile
  scope per the test-utils precedent), package
  `org.finos.legend.sdlc.backend.tck`. The Phase 3 seam-R2 seed moves in:
  `LayoutInvariantsTestSuite` from `legend-sdlc-core`'s test-jar into the
  module's main tree (`core.tck` → `backend.tck`), and
  `TestInMemoryLayoutInvariants` (its in-memory-provider runner) into the
  module's own test tree — it could not stay in core's tests without a module
  cycle. Core's test-jar remains published (the server still consumes it).
- **`BackendContractTestSuite`** (new): the capability model as executable
  contract — backend identity (type non-null; at least one workspace flavor
  declared), session user-id passthrough, and for each optional capability:
  declared ⇒ the accessor yields an api; undeclared ⇒
  `UnsupportedCapabilityException` carrying that capability and 501 (never
  `UnsupportedOperationException`, never null). Ships a default in-memory
  `BackendSessionContext` fixture.
- **`TestMinimalBackendContract`** runs the contract over a minimal
  `AbstractBackend` fixture (USER_WORKSPACES only) — certifying the base
  class's gating. The first real runner is Phase 5's in-memory backend; the
  GitLab backend cannot run it in unit tests (servlet-bound session context
  until its extraction).

### Phase 4 wrap-up: module inventory and the Phase 5 hand-off

- **Module inventory after Phase 4**: `legend-sdlc-backend-api` (L4 — the
  domain API interfaces under `backend.api.<concern>`, the SPI under
  `backend.api.spi`, `BackgroundTaskProcessor` under `backend.api.tools`, the
  two default implementations) and `legend-sdlc-backend-test-suite` (L4 TCK).
  The server consumes `Backend`/`BackendSession` through Guice providers;
  backend selection is by the polymorphic `backend:` config (legacy `gitLab:`
  adapter); discovery serves capabilities and structure/extension schemas.
- **Deliberate interim states, all Phase 5 work** (each recorded in its step):
  the GitLab backend lives in the server and unwraps
  `ServletBackendSessionContext` (the state-store re-plumb of `GitLabSession`'s
  tokens and the `AuthorizationRequiredException` conversion happen at
  extraction); the session context's state store is request-transient; the
  per-backend `/auth` resources stand until the generic resource consumes the
  session's auth surface; `FSModule`'s `Backend` provider throws pending the FS
  refit (which starts with a `DefaultEntityApi` over `core.entity`);
  `DependenciesApi` stays commonly bound to the deprecated bridge;
  `BackendEnvironment`'s object mapper is a plain `Jackson.newObjectMapper()`
  until a backend actually consumes it; the FS/getReviews empty-list→501
  compatibility check (decision 2) belongs to the FS refit.
- **§4.5 audit list unchanged**: `PROJECT_STRUCTURE_FACTORY` (now with a
  read-only accessor for discovery) and the TCCL test-resource lookup — both
  still gate Phase 6, not Phase 5.

### Correction (2026-07-10, found in Phase 5 verification): eager injector vs. run()-time state — server startup regression

Phase 5 Step 1's full-reactor verification found `reorg` HEAD red: all 9
`server.resources` test classes (33 surefire errors, one root cause,
introduced by Steps 6–7 and verified pre-existing by stash + re-run at HEAD).

- **Root cause**: guicier's `GuiceBundle` builds the injector with eager
  singleton instantiation (`Stage.PRODUCTION`), in the bundle run phase —
  before `Application.run`. Step 7 moved the `@Singleton @Provides`
  `BackendEnvironment`/`Backend` methods into `AbstractBaseModule`, so every
  server variant instantiated them at injector creation;
  `provideBackendEnvironment` injects `BackgroundTaskProcessor`, whose binding
  (`server::getBackgroundTaskProcessor`) was null until `run()` created the
  processor → `Guice/NullInjectedIntoNonNullable` `CreationException` at
  startup. Not test-only: `BaseModule` (production) failed identically, and
  `FSModule`'s interim throwing `Backend` provider was itself `@Singleton` —
  the FS server would have thrown at startup (unobserved:
  `legend-sdlc-server-fs` has no app-startup test).
- **Why Phase 4 shipped it**: the per-API session providers and the discovery
  resource were deliberately lazy (`Provider<Backend>`), and Step 7 recorded
  "InMemoryModule-based tests keep a valid injector graph" — the graph *was*
  valid, but eager singleton instantiation evaluates it at creation anyway.
  The failure is deterministic, so the Step 6–8 "green full-reactor" claims
  did not in fact hold for the server module's resource tests; the record
  stands corrected by this entry.
- **Fix (this commit)**, three parts:
  1. `BaseLegendSDLCServer.getBackgroundTaskProcessor()` creates the processor
     lazily (synchronized, on first use); `run()` now only registers its
     lifecycle shutdown. The binding may safely be pulled at injector
     creation; the underlying `ThreadPoolExecutor` starts no threads until a
     task is submitted (and core threads time out), so early creation cannot
     pin the JVM of a command that never runs the server.
  2. `AbstractBaseModule.provideBackend` is no longer `@Singleton`: it
     memoizes (double-checked) so the backend is still built once per
     deployment, but only on first actual use — restoring Step 7's intended
     semantics: a backend-less configuration (the in-memory test servers; FS
     pending its refit) yields a working server in which only requests that
     actually exercise the backend fail. `provideBackendEnvironment` remains
     an eager singleton — with (1), all of its inputs exist at creation time.
  3. `FSModule.provideBackend` (the interim throwing binding) likewise drops
     `@Singleton`, so the FS server starts again and the throw moves back to
     dereference time, as its javadoc always claimed.
- Verified: the 9 resource test classes run 40/40 green; full-reactor
  `mvn install javadoc:javadoc` green.
- Phase 5 forward note: the in-memory backend module upgrades the in-memory
  test servers from "backend-less by laziness" to a real in-memory `backend:`
  configuration, at which point the discovery endpoints work on test servers
  too.

## Phase 5 — Backend extraction (L5)

**Status: in progress.**

Sequencing note (user-directed, on the record): plan §6 lists the GitLab
extraction first; this phase runs the **in-memory backend first** — the first
real TCK runner proves the L4 defaults and the suite itself before the two
riskier refits (FS, GitLab) lean on them. The Phase 4 hand-off's
"`DefaultEntityApi` as the first task of the FS refit" is pulled forward for
the same reason: the in-memory backend needs a working entity api anyway, so
the remaining L4 defaults land as their own step and the FS refit consumes
them ready-made.

### Step 1: the remaining L4 defaults (entity, configuration, revision)

- **`DefaultEntityApi`** (`backend.api.entity`): the generic `EntityApi` over a
  `ProjectFileAccessProvider` — reads via `core.entity.EntityAccessOperations`,
  writes via `EntityModificationOperations`, with the null-validations of the
  delegating shells it generalizes. Review access contexts resolve the review
  through a supplied `ReviewApi` (the supplier is wired to the session's
  REVIEWS-gated accessor, the `DefaultComparisonApi` pattern) and mirror the
  default comparison semantics — from = the review workspace's source at its
  current revision, to = the workspace at its current revision; backends whose
  reviews carry native refs (GitLab MR diff refs) override.
- **`DefaultProjectConfigurationApi`** (`backend.api.project`): read = the L2
  `ProjectStructure.getProjectConfiguration` with the default-configuration
  fallback both backends use today; update = the L3 `ProjectStructureUpdater`
  at the source's current revision, applied with the deployment's extension
  provider and platform extensions from `BackendEnvironment` (decision 3's
  pass-through made concrete); artifact generations and latest structure
  version exactly as in the (previously duplicated) GitLab/FS code. The
  configuration status report carries no review ids — surfacing config-setup
  reviews is backend-native (GitLab's MR search); documented override point.
- **`DefaultRevisionApi`** (`backend.api.revision`): project/package/entity
  revision contexts over the provider's revision access contexts, with the
  packageable-path message rewriting factored from `GitLabRevisionApi`
  (exception handling widened to base `LegendSDLCException`).
  **`getRevisionStatus` is deliberately not defaulted**: which
  workspaces/versions/patches contain a revision is an enumeration only the
  backend can answer natively; the default throws 501 (a strict improvement on
  FS's raw-`UnsupportedOperationException` 500, consistent with decision 2),
  GitLab keeps its native implementation.
- **Cross-API scope gate — decision 2's second enforcement point, now real**:
  `BackendCapability.checkSourceScope(backend, sourceSpec)` — version sources
  require `VERSIONS`; patch sources (including patch-sourced workspaces)
  `PATCHES`; workspace sources their flavor (`USER_/GROUP_WORKSPACES`) and,
  for backup/conflict-resolution access types, that capability. All three new
  defaults apply it before touching storage. It lives on `BackendCapability`,
  which already carries the scope→capability documentation.
- `AbstractBackend.Session` wires the three defaults; together with Phase 4's
  dependencies/comparison defaults, §3.2's minimal contract is now literal:
  a backend supplies its storage provider, `ProjectApi`, `WorkspaceApi`, its
  user directory (`UserApi`), and a factory — everything else defaults.
- TCK: `BackendContractTestSuite` gains `testCrossApiSourceScopeGates`
  (version-/patch-scoped entity access on a backend without the capability ⇒
  `UnsupportedCapabilityException` carrying that capability and 501).
  `TestMinimalBackendContract` drops its entity/configuration/revision
  throwing stubs — the defaults now cover them over the fixture's provider,
  which is itself the certification that the base class satisfies the grown
  contract.
- **Verification note (2026-07-10)**: this step's full-reactor verification
  surfaced that `reorg` HEAD was red — a Phase 4 server startup regression,
  diagnosed and fixed as the Phase 4 correction above (its own commit,
  preceding this step's). With the correction in place the full reactor is
  green including this step.

### Step 2: `legend-sdlc-backend-inmemory` (L5) — the first real TCK runner

- **New module `legend-sdlc-backend-inmemory`**, package
  `org.finos.legend.sdlc.backend.inmemory`. `SimpleInMemoryVCS` and
  `InMemoryProjectFileAccessProvider` move (git mv) from the
  `legend-sdlc-project-files` **test-jar into this module's main tree** — they
  are now regular published classes, the storage provider behind the backend
  (no bridges: test-utility population, the Phase 2/3 ruling; migration-doc
  row added). Two provider upgrades on the way, both formerly stubbed because
  no test needed them: `getBaseRevision` is implemented (a branch records the
  parent tip it was created from), and the revision-context scope paths are
  canonicalized file-vs-directory aware (trailing-separator convention; the
  old code canonicalized everything as directories, so a file-scoped context
  matched nothing — the entity revision context scenario caught it).
- **`InMemoryBackend extends AbstractBackend`** (`type: "inMemory"`), the
  §3.2 minimal contract made literal: it supplies the storage provider and
  native `InMemoryProjectApi` (registry + structure build via the L3 updater
  with the environment's extensions), `InMemoryWorkspaceApi` (registry +
  provider branches; workspace flavor/scope gating via `checkSourceScope`;
  `updateWorkspace` is NO_OP-or-501 — the VCS has no source-into-branch
  merge), and `InMemoryUserApi` (no directory: the session user is the only
  known user); everything else — entities, configuration, revisions,
  dependencies, comparison — is the inherited L4 defaults. Declares
  `USER_WORKSPACES` only, deliberately: the first runner exercises both the
  declared-capability branch and all the undeclared-capability gates.
  `InMemoryBackendFactory`/`InMemoryBackendConfiguration` registered under
  `META-INF/services`; project ids are project names; all state is
  process-local.
- **TCK grows `BackendScenarioTestSuite`** (extends the contract suite, so
  one runner class certifies both): end-to-end over a real session — project
  creation (configured, right coordinates, no entities), workspace lifecycle
  (create/list/get/delete/outdated), entity round-trip (create/read/delete in
  a workspace; project source unaffected), configuration update in a
  workspace, workspace source+creation comparisons (one entity diff), and
  revision contexts (project current revision; entity-scoped revision
  history). This is the certification of the Step 1 defaults over real
  storage. TCK pom gains `legend-sdlc-shared`, drops the project-files
  test-jar; `TestMinimalBackendContract`'s fixture now uses an inline
  throwing provider (the capability contract never reaches storage).
- **Runners**: `TestInMemoryBackendContract` (contract + scenarios, 10 tests)
  and `TestInMemoryLayoutInvariants` (moved from the TCK's own test tree —
  it could not stay there once the provider lives in a module that depends on
  the TCK) run in this module's test tree, i.e. in CI on every build — the
  first real `BackendContractTestSuite` runner, per the phase's sequencing
  note.
- **The test server now declares the in-memory backend**: `config-test.yaml`
  replaces its dummy `gitLab:` section (values "na") with
  `backend: {type: inMemory}`, so the resource tests boot with the GitLab
  bundle inactive and the real factory/config chain in place, and the new
  `TestBackendDiscovery` hits `GET /configuration/capabilities` end to end
  (200, `backendType: inMemory`, `USER_WORKSPACES`) — discharging the Phase 4
  correction's forward note; the interim "backend-less by laziness" state is
  gone from the in-memory test server. (`InMemoryModule` still binds the
  fixture apis the resource tests seed — replacing that fixture with the real
  in-memory backend is possible follow-up work, not Phase 5 scope.)
- Consumer updates: `legend-sdlc-server` swaps the project-files test-jar for
  a test dependency on this module (3 test classes re-import);
  `legend-sdlc-core` drops its project-files test-jar dependency (stale since
  the Phase 4 TCK-seed move). The project-files test-jar remains published
  (its two remaining classes are its own tests).

### The decision-2 compatibility check: Studio/omnibus tolerance of FS `getReviews` → 501 (2026-07-14)

Decision 2 flagged one behavior change requiring verification before the FS
refit lands it: FS's two `getReviews` overloads return empty lists today and
would 501 under an undeclared `REVIEWS` capability; "consistency wins on the
record, but the Phase 5 FS refit must verify Studio/omnibus tolerance before
flipping them." legend-studio is not in this workspace; the check was run
against `finos/legend-studio` master and the `finos/legend` omnibus sources
(2026-07-14). Findings, verified verbatim from source:

1. **The pairing is real.** The omnibus `example-esg-2023`/`example-ghc-2023`
   variants ship Studio against the FS server (`run-sdlc.file-system.sh` runs
   `org.finos.legend.sdlc.server.startup.LegendSDLCServerFS` with
   `config.file-system.yml` and preloaded FS project data;
   `finos/legend`, `installers/omnibus/**`). Consequence for the refit noted
   below: omnibus also depends on the parallel-server main class the refit
   deletes — the migration recipe must cover it.
2. **Studio is NOT tolerant of `getReviews` → 501.** In standard mode the
   editor's initialization awaits `fetchCurrentWorkspaceReview()`
   (`WorkspaceReviewState`), which calls
   `GET /projects/{id}/reviews?state=OPEN&…`; its catch block calls
   `EditorSDLCState.handleChangeDetectionRefreshIssue(error)`, which raises a
   **blocking modal alert for any error** (404 gets a "project or workspace no
   longer exists" flavor; everything else — a 501 included —
   `setBlockingAlert({message: error.message})`). Flipping the list routes to
   501 puts a blocking modal on every workspace open of an FS-backed
   deployment.
3. **The intolerance is specific to the review-list route.** The other
   awaited init fetches degrade gracefully: `fetchLatestCommittedReviews`
   (the second `getReviews` overload's consumer) and
   `checkIfWorkspaceIsOutdated` notify-and-continue; `fetchProjectVersions`
   (FS `getVersions` also returns an empty list today, unflagged by
   decision 2) and `fetchAuthorizedActions` log-and-continue. So `VERSIONS` and the
   rest can go undeclared with 501s per decision 2 without breaking Studio;
   only review *enumeration* under an absent `REVIEWS` capability cannot 501
   until Studio adapts.

Tolerance is thereby established **in the negative**, so decision 2's default
(consistency wins) does not apply — it was expressly conditioned on verified
tolerance. Options put on the record for the user:

- **(a) Flip anyway** — rejected by the findings above.
- **(b) Undeclared-capability enumeration affordance at L6** (recommended):
  FS declares no `REVIEWS`; discovery stays honest; the server's per-API
  `ReviewApi` provider, on `UnsupportedCapabilityException`, supplies a
  no-reviews implementation whose *list* methods return empty lists and whose
  every other method rethrows — review enumeration degrades to "none", all
  other review routes 501 per decision 2. Applies to any reviews-less backend
  (in-memory included), is one provider method, and is retained temporarily
  until Studio consumes `GET /configuration/capabilities`, at which point it
  is removed and decision 2 applies in full.
- **(c) FS declares `REVIEWS` with a null implementation** (empty lists, 404
  unknown review, 501 mutations) — preserves today's wire behavior but makes
  the discovery surface lie (Studio's capability-adaptive UI would offer
  reviews a backend can never create); rejected as poisoning the seam the
  capability model exists to provide.

**Decision (user, 2026-07-14): option (b)** — the undeclared-capability
enumeration affordance at L6. Recorded as a compatibility amendment to
decision 2's HTTP mapping: review *enumeration* under an absent `REVIEWS`
capability reports no reviews (200, empty list) instead of 501; every other
review route 501s as decided. The affordance is L6-only (capabilities and
discovery are untouched — FS does not declare `REVIEWS`), and is removed once
Studio consumes `GET /configuration/capabilities` for its review UI.

### Step 3: FS refit, part 1 — the characterized provider defects fixed deliberately

Phase 3 Step 1's FS quirks 6–9 are fixed in place in `legend-sdlc-server-fs`
(the module refit onto the SPI follows as part 2), and the characterization
pins updated in the same commit to assert the fixed behavior — the suite's
javadoc now marks it as pinning post-fix behavior rather than preserved bugs.

- **Quirk 6 (standard-context enumeration broken)**:
  `FileSystemFileAccessContext.getFilesInCanonicalDirectories` now walks the
  tree of the context's resolved commit, mapping git tree paths (no leading
  separator) to canonical paths (leading `/`) before matching against the
  canonical directory list (root short-circuits), and reads blobs from that
  same tree; `ObjectId.fromString(null)` is gone — a null revision id
  resolves to the tip of the branch the source specification designates.
- **The context now honors its revision id** (latent, unpinned defect fixed
  with quirk 6, same root): `getFile` and `fileExists` previously read the
  branch tip regardless of the context's revision id; all three accessors now
  resolve one commit — pinned revision or branch tip — and read its tree.
  `FileSystemEntityApi.getEntityAccessContext` previously dropped its
  `revisionId` argument on the floor (passed null to the provider); it now
  passes it through. New pin: `testRevisionPinnedAccessContext`.
- **Quirks 7 and 9 (platform-dependent enumeration; two code paths)**: fixed
  by unification — `FileSystemEntityApi`'s git-tree-walk enumeration variant,
  its private `EntityProjectFile`, and the `java.nio.Path` relativization
  that produced `\`-separated paths on Windows are deleted; `getEntities`
  joins `getEntity`/`getEntityPaths` on `core.entity.EntityAccessOperations`
  over the (now working) standard context. The entity api is a pure
  delegating shell, and the FS enumeration pins are no longer conditional on
  `File.separatorChar`.
- **Quirk 8 (stale reference revision loses its 409)**:
  `FSException.getLegendSDLCServerException` passes a `LegendSDLCException`
  through unchanged instead of re-wrapping it into a message-concatenated
  500 (return type widened to the base `LegendSDLCException`; every call
  site is a `throw`). The modification context's conflict now surfaces as
  409 with its original message, pinned exactly.
- **New defect found by the re-pinned tests, fixed**: `submit()` built its
  returned revision from the branch `Ref` looked up *before* committing — a
  stale snapshot, so every write reported its *parent* revision (invisible
  until the revision-pinned pin above compared trees). It now builds the
  revision from the `RevCommit` that `git.commit().call()` returns.
- **Deliberately not fixed here** (they belong to part 2, where the TCK
  certifies them): `getAllRevisions` still throws; the revision access
  context still ignores its `paths` scoping; `getBaseRevision` is not a true
  merge base; the workspace api's defects (`deleteWorkspace` unimplemented,
  `getWorkspaces` swaps the USER/GROUP flavors, `updateWorkspace`
  unsupported).
- Verified: FS characterization 9/9 green; full-reactor
  `mvn install javadoc:javadoc` green.

### Step 4: FS refit, part 2 — `legend-sdlc-backend-fs` (L5); the parallel server deleted

The file-system implementation is refit from a standalone server onto the
backend SPI: a new module `legend-sdlc-backend-fs` (package
`org.finos.legend.sdlc.backend.fs`), and `legend-sdlc-server-fs` reduced to a
relocation POM. One commit, not the two remaining pieces of the natural split
(part 1 took the defect fixes separately): the review-enumeration affordance
is the module's Studio-safety prerequisite, and the deletion's relocation POM
points at the module — landing them together keeps the commit-on-green
convention intact without verifying an intermediate tree no one will check
out.

**The decision-(b) affordance, implemented at L6.** New
`org.finos.legend.sdlc.server.backend.NoReviewsReviewApi`: both `getReviews`
overloads return empty lists; every other method rethrows the stored
`UnsupportedCapabilityException`. `BaseModule.provideReviewApi` supplies it
when `session.getReviewApi()` throws for the undeclared capability, so it
applies to any reviews-less backend (in-memory included), not just FS.
Capabilities and discovery are untouched; the javadoc records the amendment
and the removal condition (Studio consuming
`GET /configuration/capabilities`). Pinned by `TestNoReviewsReviewApi`.

**The module.** `FileSystemBackendFactory` (ServiceLoader-registered) builds
`FileSystemBackend` from `backend: {type: fileSystem, rootDirectory: …}`.
The backend extends `AbstractBackend`, declares **only `USER_WORKSPACES`**,
and creates the root directory if missing. Its session owns a session-scoped
`FileSystemProjectFileAccessProvider` (jgit over one repository per project
under the root; project source = `master`, workspace = branch
`workspace/{user}/{id}`) and supplies three native apis —
`FileSystemProjectApi` (repository scan/init, git-config metadata,
structure built at the latest version through `ProjectStructureUpdater` with
the environment's extensions), `FileSystemWorkspaceApi` (branch lifecycle;
`updateWorkspace` reports `NO_OP` when current, 501 when outdated — the
provider has no merge), `FileSystemUserApi` (session user only). Everything
else — entity, configuration, revision, dependencies, comparison — is the
inherited L4 defaults over the provider; the old module's fourteen stub api
classes have no successors at all, the capability gates replace them.

**The provider completes what part 1 deferred**, and the TCK certifies it:
`getAllRevisions` implemented (alias resolution for BASE/HEAD/CURRENT/LATEST,
since/until/limit); the revision access context honors its `paths` scoping
(scoped `log`, membership-checked `getRevision`); `getBaseRevision` is a true
merge base (`RevFilter.MERGE_BASE`; first commit on `master` itself);
`deleteWorkspace` works; the USER/GROUP listing swap is gone.

**Decisions taken in the refit** (checked against the worklog; none had a
prior ruling):

- **`USER_WORKSPACES` only.** The old server's group-workspace support was
  characterized broken (quirk: flavors swapped), so it is not carried:
  group creation fails on the `checkSourceScope` gate (501), group-filtered
  listings return empty lists. Studio-safe — its group-workspace fetches
  tolerate both.
- **`deleteProject` stays 501** (`PROJECT_DELETION` undeclared). The old
  implementation existed but deleting a git repository out from under
  concurrent sessions was never safe; a deliberate implementation can declare
  the capability later.
- **Projects are created at the latest structure version.** The old server
  consulted `ProjectCreationConfiguration` for a default version; that type
  is server configuration (L6) and would drag a server dependency into L5.
  If a configured default is wanted, it belongs in the config-options plan
  (seam S2).
- **Null session user maps to `local_user`**, the old server's fixed user id,
  so existing root directories — and their `workspace/local_user/…` branches
  — keep working unchanged.
- **`submit` refuses empty commits**: jgit commits unconditionally by
  default; a no-op change set now returns null (no revision) instead of
  minting an empty commit. Found by the reconcile-no-op layout invariant.
- **The server does not gain a `legend-sdlc-backend-fs` dependency.** The
  standard server distribution currently bundles no L5 backend; whether it
  should ship with backends on the classpath or leave that to deployments is
  the same question the GitLab extraction must answer (the GitLab backend
  starts *inside* the server), so it is deferred to that step rather than
  answered piecemeal here.

**The deletion.** `legend-sdlc-server-fs` loses `src/**`, its Dockerfile,
and its shaded-jar build; the POM becomes a relocation POM
(`distributionManagement/relocation` → `legend-sdlc-backend-fs`), so
dependents get the pointer at resolution time. No deprecated bridges: the old
classes were a self-contained runnable, not an API surface. The migration
recipe gains the table row and an "If you deploy the file-system SDLC server"
section (standard server + `backend:` config + classpath; the omnibus
`run-sdlc.file-system.sh` dependency found in the tolerance check is covered
by it).

- Verified: `legend-sdlc-backend-fs` 22/22 — scenario+contract TCK 10/10 on
  the first substantive run, characterization 9/9 (moved into the module,
  now driving the session's L4 `DefaultEntityApi`), layout invariants 3/3
  (after two test-harness/product fixes: a fresh root per invariant case —
  the suite reuses project ids — and the empty-commit guard above);
  `legend-sdlc-server` 269 green with the affordance; full-reactor
  `mvn install javadoc:javadoc` green.

### Step 5: GitLab extraction, part 1 — the SPI crossings (L4, additive)

The GitLab extraction needs four things to cross the SPI that Phase 4's
staging deferred (per decision 1 and the Step 5 staging record). All are
additive to `legend-sdlc-backend-api`; no consumer changes in this commit.

- **`BackendSessionContext.getService(Class)`** (default null): the per-user
  counterpart of `BackendEnvironment.getService` — typed lookup for auth
  material the host can offer beyond identity + state store. Everything
  published through it is data or a JDK type; pac4j never crosses. The server
  will publish `javax.security.auth.Subject` (Kerberos), plus two new L4
  value types: **`OidcAuthMaterial`** (issuer, access token, scopes, refresh
  token, expiration) and **`PersonalAccessTokenAuthMaterial`** (host, token).
  These replace the token harvesting that today lives *inside* the GitLab
  session classes (`GitLabOidcSession`/`GitLabPersonalAccessTokenSession`
  constructors): the host describes how the user authenticated, as data; the
  backend decides whether that material is usable against its upstream
  (issuer/host match) — that logic is backend knowledge and moves to L5.
- **`StaleAuthorizationException`** (503 default): decision 1 enumerated two
  redirect flows (302 to the authorization URI; 403 with `auth_uri`), both
  covered by `AuthorizationRequiredException(URI)`. The GitLab code has a
  *third* — `BaseGitLabApi.buildException`'s 401 branch clears the stale
  token and redirects the client back to *the original request* (GET; 503
  "please retry" otherwise). Its redirect target is the request itself, which
  only the host knows, so it crosses as a type: the backend throws after
  discarding stale material; the server's mapper reproduces today's
  302-to-self / 503 pair exactly.
- **`BackendFactory.configureObjectMapper(ObjectMapper)`** (default no-op):
  the GitLab configuration needs a Jackson mix-in for its polymorphic
  `gitlabAuthorizers` list, registered today by a hard-wired
  `GitLabConfiguration.configureObjectMapper` call in
  `BaseLegendSDLCServer.initialize`. The factory hook lets any backend
  configure the host's configuration mapper at bootstrap, alongside subtype
  registration; the hard-wired call dies in part 2.
- **`BackendEnvironment.getProjectCreationConfiguration()`** (default null) +
  L4 value type **`ProjectCreationConfiguration`** (default structure
  version, groupId/artifactId patterns): `GitLabProjectApi`'s only real use
  of the server's `ProjectStructureConfiguration` — reached today through the
  `getService` escape hatch — is its project-creation section. Decision 3
  said such needs cross "as data via environment accessors"; this is that
  accessor. The server's configuration class stays at L6; the environment
  publishes the data view. (The FS refit's "projects are created at the
  latest structure version" ruling stands — whether FS *adopts* the policy
  remains config-options work; the accessor exists because GitLab's current
  behavior must be preserved through the extraction.)

Verified: full-reactor `mvn install javadoc:javadoc` green.

### Step 5: GitLab extraction, part 2 — the re-plumb, in place

The GitLab code is re-plumbed onto the SPI *inside* the server module, so the
relocation (part 3) is purely mechanical. Everything decision 1 staged for the
extraction lands here: no GitLab code touches servlet, pac4j, Guice, or JAX-RS
types any more.

**The server side (backend-independent):**

- **Generic state sessions** (`legend-sdlc-server-shared`, replacing the GitLab
  session family): `StateSession` — identity plus a mutable string-keyed state
  bag — with `CommonProfileStateSession`/`KerberosStateSession` over the pac4j
  profiles and a `StateSessionBuilder`. The bag is what backends see through
  the session state store port; the cookie encodes it generically (format
  marker + sorted key/value pairs, `SessionStateCodec`). Cookies from before
  the format change decode to an *empty* bag — the marker distinguishes them —
  so existing sessions re-acquire their state once after upgrade: OIDC-, PAT-,
  and Kerberos-authenticated users silently (the harvest/authorizer chain
  re-runs), interactive-OAuth users through one extra authorize redirect.
  `TestStateSession` pins the round-trip and the legacy-cookie tolerance.
- **`StateSessionWebFilter`** (server) replaces `GitLabWebFilter` and is
  registered *unconditionally* in `BaseLegendSDLCServer.run` under the name
  `LegendSDLCSession` (same supported profile types as before). Deployments
  order the filter via `filterPriorities` under its historical name "GitLab";
  `LegendSDLCServerConfiguration.getFilterPriorities()` aliases that name to
  the new one so existing configuration keeps working (migration row).
  `GitLabBundle` and `GitLabServerHealthCheck` are deleted: the bundle's only
  remaining job was the filter, and the health check only validated config
  shape (its own TODO said as much). A backend health surface on the SPI is
  deferred (additive later, like metrics); the `gitLabServer` health-check
  entry disappears from deployments (migration row).
- **`ServletBackendSessionContext` completes** (closing the Phase 4 interim):
  the state store is real — reads and writes go to the `StateSession` bag,
  every write triggers the session-cookie write-back — and `getService`
  publishes the per-user auth material as data: `javax.security.auth.Subject`
  for Kerberos sessions, `OidcAuthMaterial` / `PersonalAccessTokenAuthMaterial`
  harvested from the pac4j profile types. This is decision 1's "L6 adapter"
  made literal: the only place pac4j appears on the backend session path. A
  request-transient store remains as fallback for non-state sessions (test
  fixtures).
- **The generic `/auth` surface** (`server.resources.auth`, bound for every
  server variant in `AbstractBaseModule`): `AuthResource`
  (authorize/callback/termsOfServiceAcceptance) over `BackendSession`'s auth
  contract, and `AuthCheckResource` (authorized) with the session-bootstrap
  machinery (PAT header, session store) from the former GitLab check resource.
  The OAuth `state` round-trip is owned here: the resource encodes the current
  request into `state` and appends it to the backend's authorization URI —
  parameter order (and hence the URI) identical to the old GitLab-built one.
  The former GitLab auth resources are deleted; routes and wire behavior are
  unchanged. `TestAuthResources` pins the surface on the in-memory test server.
- **Two new exception mappers**: `AuthorizationRequiredException` → 403 with
  the historical `{"message":"Authorization required","auth_uri":"/auth/authorize"}`
  body (byte-identical, by delegating to the server exception mapper), except
  on the authorize route, where `AuthResource` catches it and issues the 302.
  `StaleAuthorizationException` → 302-back-to-the-same-request for GET, 503
  "please retry" otherwise — exactly the former 401-stale-token pair.
  Implementation note, learned the hard way: the stale mapper needs the
  request, and `@Context` *field* injection of request-scoped types into a
  Jersey-registered provider instance fails at servlet initialization under
  the Guice–HK2 bridge (the bridge provisions eagerly instead of proxying);
  the mapper is therefore Guice-bound (lazy `Provider<HttpServletRequest>`)
  and reaches Jersey through the Guice bundle's binding scan, like the
  resources do.
- **Module rewiring**: `BaseModule` loses all GitLab bindings and its
  `UserContext` override (plain `UserContext` everywhere); the request-scoped
  `BackendSession` provider moves up to `AbstractBaseModule` (the auth
  resources need it under `InMemoryModule` too). The environment no longer
  publishes `ProjectStructureConfiguration` through `getService` — its one
  consumer now gets data (below); it implements
  `getProjectCreationConfiguration()` instead, built from the server config.
- **The legacy-config adapter is re-based on raw JSON**: the server no longer
  compiles against the GitLab configuration classes, so
  `LegendSDLCServerConfiguration` holds the legacy `gitLab:` section as a
  `JsonNode` and `AbstractBaseModule.buildBackend` synthesizes
  `backend: {type: gitlab, ...}` from it through the bootstrap object mapper
  (which carries the ServiceLoader-registered subtypes and the factories'
  mapper hooks). The legacy `uat:`/`prod:` mode sections are accepted and
  flattened by `GitLabBackendConfiguration`'s creator (GitLab owns its legacy
  configuration shapes; the host adapter only stamps the type), so the
  raw-JSON path handles everything the old typed parse did. Validation timing
  changes: a
  structurally invalid legacy section now fails at first backend use rather
  than at config parse (the backend was already built lazily).
  `GitLabConfiguration.configureObjectMapper` at bootstrap is replaced by the
  part-1 `BackendFactory.configureObjectMapper` hook, which the GitLab factory
  implements.

**The GitLab side (framework-free):**

- **`GitLabTokenManager` persists through the session state store** (keys
  `gitlab.appId`/`gitlab.token.type`/`gitlab.token`/`gitlab.refreshToken`/
  `gitlab.tokenExpiry`, write-through): state is keyed to the GitLab
  application id, as the old cookie encoding was. `PRIVATE`-typed tokens are
  never OAuth-refreshed — replicating the former PAT session's
  `shouldRefreshToken() == false` override in the one manager that now serves
  all auth flavors. `GitLabTokenResponse` gains a typed-token constructor (the
  PAT harvest yields a `PRIVATE` token) and an optional exact expiry (the OIDC
  harvest carries the profile's expiration; a response without any expiry gets
  the default-derived one — the former OIDC-without-expiration edge, which
  left the expiry null and forced an immediate refresh attempt, now trusts the
  token for the default window; judged the saner reading of an
  unreachable-in-practice edge). `TestGitLabTokenManager` re-pins all of this
  over an in-memory store (the old test pinned the dead cookie encoding).
- **`GitLabUserContext` is rebuilt framework-free** (name kept — it is still
  the per-user view; 17 api classes construct against it unchanged): identity
  and state from `BackendSessionContext`, the `GitLabApi`/token life cycle
  logic otherwise verbatim. The OIDC/PAT token harvests that lived in the
  session-class constructors run at user-context construction when the
  persisted state has no token — preserving `/auth/authorized` semantics for
  OIDC/PAT users (authorized on first request, before any interactive flow).
  One deliberate micro-fix: `isUserAuthorized` returns false when the
  authorizer chain yields nothing (previously an NPE → 500 on a
  cleared-token-with-future-expiry session).
- **The authorizer chain crosses the SPI**: `GitLabAuthorizer.authorize` takes
  `BackendSessionContext` instead of the server `Session` — a breaking change
  for externally configured authorizers (Jackson-polymorphic `gitlabAuthorizers`
  list; class names in YAML keep resolving, implementations re-target the new
  signature — migration recipe in part 3). `KerberosGitLabAuthorizer` reads
  the `Subject` from `getService`; two new harvest authorizers
  (`OidcGitLabAuthorizer`, `PersonalAccessTokenGitLabAuthorizer`) head every
  chain, then the configured authorizers or the historical Kerberos default.
- **The redirect flows convert** per decision 1: interactive authorization is
  `AuthorizationRequiredException(buildAppAuthorizationURI(appInfo))` (no
  state — the host appends it); auth failure 403 / auth error 500 as before
  (base exception type); `BaseGitLabApi.buildException`'s 401 branch clears
  the token and throws `StaleAuthorizationException`.
- **The `LegendSDLCServerException` sweep**: ~330 throw/validate/catch sites
  across the GitLab tree converted to the base `LegendSDLCException` with int
  status codes (identical mapper output; the Phase 2/3 precedent applied
  wholesale), `javax.ws.rs` gone from the tree (`Status.Family` classification
  replaced by an int range check; two `Status.fromStatusCode` coercions became
  int passthrough — unknown codes no longer collapse to 500 on those two
  paths, unreachable with our own thrown codes). `@Inject` stripped from all
  api classes (L5 takes no `javax.inject`).
- **`GitLabBackend`** builds its own `GitLabAppInfo` and authorizer manager
  from `GitLabConfiguration`, constructs the user context from the session
  context (the `ServletBackendSessionContext` unwrapping and its
  `IllegalArgumentException`s are gone), and now implements the auth surface
  fully: `isAuthorized` absorbs the check resource's
  `GitLabAuthAccessException` → false handling;
  `getUnacceptedTermsOfService` is aligned to the deleted resource's exact
  wire behavior (401/403 → 403 with the "Error checking acceptance of terms
  of service" message — the Phase 4 replication had dropped that mapping, a
  latent drift caught at this rewiring). `GitLabProjectApi` consumes the L4
  `ProjectCreationConfiguration` from the environment (decision 3's
  data-crossing made concrete); the factory's `getService` escape hatch use is
  gone.

Verified: `legend-sdlc-server` 261 green (258 + the new `TestAuthResources` 3,
including all resource tests — the server boots with the unconditional session
filter and the generic auth surface under the in-memory backend);
`legend-sdlc-server-shared` green with the new session pins; full-reactor
`mvn install javadoc:javadoc` green.

### Step 5: GitLab extraction, part 3 — `legend-sdlc-backend-gitlab` (L5); the relocation

The mechanical half: the re-plumbed GitLab tree moves (git mv) from
`legend-sdlc-server` to the new module **`legend-sdlc-backend-gitlab`**,
packages `org.finos.legend.sdlc.server.gitlab.*` →
`org.finos.legend.sdlc.backend.gitlab.*` (root, `api`, `auth`, `tools`).
GitLab4J, jsoup, and commons-compress leave the server's dependency tree; the
`BackendFactory` services registration travels with the factory. The plan's
§3.3 expectation ("expected to shrink substantially") was already realized by
Phases 3–5: what moves is only what genuinely *is* GitLab.

- **The deferred bundling decision (Phase 5 Step 4), resolved**: the standard
  server distribution ships all three L5 backends — the server pom takes
  **runtime**-scoped dependencies on `legend-sdlc-backend-gitlab`,
  `legend-sdlc-backend-fs`, and `legend-sdlc-backend-inmemory` (test →
  runtime). Grounds: the Phase 4 legacy-config promise ("a legacy deployment
  needs no config change") requires the GitLab factory on the standard
  distribution's classpath, which settles the question for gitlab; decided
  once and applied to all three per the Step 4 hand-off (§3.5's "can bundle
  any set of backend jars" and §8's "backends arrive on the runtime
  classpath" made concrete — compile scope would violate the layering,
  runtime scope expresses exactly "present, not depended on"). The omnibus
  file-system pairing gets its backend from the standard distribution for
  free. Assemblies that build their own classpath add the backend jar(s) they
  deploy (migration row).
- **No bridges at the old FQNs** — and not by the usual population argument
  alone: the server module *cannot* alias classes that now live in a module
  below it (a bridge would need a compile dependency the layering forbids),
  and bridges inside the L5 jar under `server.gitlab.*` would ship old names
  in a new artifact to no benefit (any consumer must change its dependency
  anyway). The gitlab classes were server-internal implementation, never a
  published API; the migration doc carries the rename rows. One genuinely
  consumed surface gets a recipe instead of a bridge: **`GitLabAuthorizer`**
  (externally implemented, configured by class name in YAML) — its part-2
  signature change is documented with the getService/state-store re-targeting
  recipe.
- **Stragglers found by the move**, each with a ruling:
  - `server.tools.CallUntil`/`ThrowingRunnable`/`ThrowingSupplier` (Phase 1's
    remaining "Phase 4/5 material") were gitlab-only consumers but generic
    utilities; `CallUntil` needs slf4j, which rules out zero-dependency
    `legend-sdlc-shared` — they join `BackgroundTaskProcessor` in
    **`backend.api.tools`** (the exact Phase 4 precedent), with deprecated
    bridges at the old server FQNs. `AuthenticationTools` (Kerberos/SPNEGO
    HTTP plumbing) is consumed only by the GitLab SAML authenticators and
    moves with them to `backend.gitlab.tools` (no bridge, migration row).
    `server.tools` now holds only `SessionProvider` and the bridges — the
    Phase 1 carry-in is discharged.
  - `GitLabApiTools`' retry counter called the server-shared prometheus
    handler (`SDLCMetricsHandler`, "gitlab retryable exception"). Metrics on
    `BackendEnvironment` were expressly deferred in the Phase 4 review, so
    the counter is **dropped** (debug log in its place), not smuggled through
    a new port; it returns when the environment grows a metrics surface.
    Deployment-visible: the counter disappears (migration row).
  - `DepotServerException.getDetail()` walked a `GitLabAuthException` cause —
    an incidental coupling from shared authorship; the branch is removed
    (depot exceptions never carry GitLab causes on any live path).
  - The deprecated `ProjectApi` bridge's `configureProjectInWorkspace`
    (GitLab-specific, kept on the bridge in Phase 4 "for external
    implementors") referenced `GitLabProjectId` and cannot survive on a
    server-resident bridge; it is removed (migration row). The bridge
    interface itself stands.
  - `FinosGitlabProjectStructureExtensionProvider` (+ its yaml/ci resources)
    stays in the server at its old FQN: deployments reference it by class
    name in configuration, and concrete extensions are deployment-scoped
    configuration, not backend code (§3.3) — the gitlab backend jar bundles
    no extensions (seam-S3 obligation held).
  - The moved tests: `JerseyGuiceUtils.install` static appeasement dropped
    (jersey2-guice is not on the module's classpath — and in the server pom
    that bridge is now runtime-scoped, its only compile references having
    been these tests); the two project tests' extension fixture
    (`DefaultProjectStructureExtension`, a server class) replaced by an
    inline test fixture over the L2 SPI; the characterization's
    entity-normalization pin follows the FS module's precedent (asserts
    against the in-use serializer's normal form rather than assuming the
    engine serializer extensions are present). The `test-gitlab-com`
    failsafe profile moves to the module pom.
- The server pom also sheds `metrics-healthchecks`, `commons-codec`,
  `commons-compress`, and `hk2-api` (all orphaned by the extraction — found
  by `dependency:analyze`).
- The GitLab backend can now in principle run the TCK's contract suite (the
  servlet-bound session context is gone); wiring a
  `TestGitLabBackendContract` needs thought about which contract tests are
  meaningful without a reachable GitLab (the fully-declared capability set
  means the undeclared-gate branches never fire) — left to Phase 6-adjacent
  test work rather than done thinly here.

Verified: `legend-sdlc-backend-gitlab` 55 green (the moved unit tests: token
manager over the state store, backend configuration incl. the legacy
uat/prod flattening, SAML authenticator, project id, api statics,
characterization 17/17); `legend-sdlc-server` 204 green after a **clean**
build (the stale-`target/classes` services-file hazard from the Phase 2
record struck again — the moved `BackendFactory` registration lingered in
`target/classes` and broke the `ServiceLoader` at app bootstrap until
`mvn clean`); full-reactor `mvn clean install javadoc:javadoc` green.
