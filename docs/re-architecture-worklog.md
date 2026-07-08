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
- L1 package rename (`server.project` → `projectfiles`) and the
  `SourceSpecification` L1/L4 split were *not* done (plan permits deferral; the
  moved L2 code imports L1 types from their `server.*` packages for now).
