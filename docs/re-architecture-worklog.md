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

**Status: in progress** (design review complete 2026-07-09; implementation
running, steps below after the review record).

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
