# Re-architecting `legend-sdlc`: Separating Core, Backends, and Server

## 1. Goals

This plan re-architects `legend-sdlc` so that:

1. **The backend is separate from the server.** GitLab becomes one backend among many,
   with a clearly defined SPI for implementing a backend and a configuration-driven way
   to select one for a server deployment.
2. **A backend implements as little as possible.** Functionality that can be expressed
   over the project file abstraction (entity access and editing, project configuration,
   dependency resolution, comparison) is implemented once, generically, and inherited by
   every backend.
3. **As much functionality as possible is usable outside the server.** In particular:
   entity editing operations on a model in a local checkout, including a Legend model
   embedded in a larger project that also contains non-Legend content. The headline
   consumer of this goal is **Legend IDE plugins** (IntelliJ, an LSP server): model
   editing — and, for a managed project, project-structure/configuration editing — driven
   from an IDE rather than from Studio, with the IDE owning version control and reviews.
   See §4, which now treats the IDE forms as first-class requirements.

This plan subsumes [`project-structure-extraction.md`](project-structure-extraction.md):
that document's phases remain valid and become the early phases here (its Phase 1 is
already partially complete). Where this document and that one conflict, this one governs.

A companion feature plan,
[`project-structure-configuration-options.md`](project-structure-configuration-options.md),
covers version- and extension-scoped project configuration options (e.g. letting a project
select among a structure version's optional *features* — whether it has service execution,
and whether that builds a shaded jar — add user-maintained modules, or set a deployment's
GitLab-CI runner-tag options). It is kept
*separate* — it changes the `project.json` layout and the REST surface, both non-goals here
— but it is *dependent* on this plan and sequenced onto it; this plan reserves three small
seams for it (noted in §6 and §7).

A second companion plan,
[`project-layout-reconciliation.md`](project-layout-reconciliation.md), replaces the
imperative write-side of structures and extensions (hand-computed `ProjectFileOperation`
deltas) with a declarative model: build the complete desired layout from configuration +
entities, and let one generic reconciler diff it against current files. It too is kept
separate — it deliberately *changes update behavior* (files outside the managed layout are
deleted unless preserved), which breaks this plan's behavior-preserving premise — and it
too is dependent and sequenced onto this plan, which reserves seams R1–R2 for it (noted in
§6): most importantly, Phases 2–3 must extract the imperative write-side as the *legacy*
contract behind a single dispatch point, not enshrine it as the new public L2 SPI.

### Non-goals

- Changing the REST API surface consumed by Legend Studio. The server's resources and
  wire formats stay compatible; this is an internal re-layering.
- Changing the on-disk project layout (`project.json`, entity source directories,
  project structure versions). Layout knowledge moves; its semantics do not change.
- Building any specific new backend beyond what is needed to prove the SPI (the existing
  filesystem backend, plus an in-memory backend for testing). GitHub/Bitbucket/etc. become
  *possible*, not *delivered*.

## 2. Current State Assessment

What exists today, and what it tells us:

| Observation | Implication |
|---|---|
| `legend-sdlc-server` is 349 source files containing five distinct things: the domain API interfaces (`server/domain/api/**` — `EntityApi`, `ProjectApi`, `WorkspaceApi`, …), the GitLab implementation (`server/gitlab/**`, ~50 files), project structure (`server/project/**`), the JAX-RS resources (`server/resources/**`), and the Dropwizard/Guice application. | The seams already exist *as packages*; they need to become *modules*. |
| The `domain/api` interfaces are already a de-facto backend SPI: `legend-sdlc-server-fs` implements them as a second backend. | We do not need to invent the SPI from scratch — we need to extract, rationalize, and minimize it. |
| `FileSystemEntityApi` (740 lines) and `GitLabEntityApi` (767 lines) are near-duplicates. Both implement entity read/write by composing `ProjectFileAccessProvider.FileAccessContext` with `ProjectStructure`. The same duplication pattern holds for configuration, comparison, and dependency APIs. | The single biggest lever for "minimize what a backend implements": generic implementations over the file abstraction, written once. The duplication is existence proof that the logic is backend-independent. |
| `ProjectFileAccessProvider` (in `legend-sdlc-project-files`) is the right low-level abstraction: files + revisions + modifications, addressed by `(projectId, SourceSpecification, revisionId)`. | The storage SPI exists. Its problems are (a) it lives in a `server.*` package, (b) it drags in workspace/patch concepts via `SourceSpecification`. |
| `BaseLegendSDLCServer` hard-wires GitLab (`GitLabBundle`, `GitLabConfiguration` in the bootstrap path), even though the interfaces would permit substitution; `legend-sdlc-server-fs` works around this with its own parallel startup. | Backend selection must move from inheritance/forking to configuration. |
| The generation modules and Maven plugins (`legend-sdlc-generation-*`, `legend-sdlc-entity-maven-plugin`, …) and `EntityLoader` already operate on files/entities with no server dependency. | The "usable outside the server" goal is already achieved for *reading and generating*. The gap is *editing* and *project-structure awareness* outside the server. |
| `legend-sdlc-shared` exists; `StringTools`/`IOTools` have moved; `LegendSDLCServerException` still carries a JAX-RS dependency. | Phase 1 of the extraction doc is nearly done. |

## 3. Target Architecture

### 3.1 Layers

```
┌────────────────────────────────────────────────────────────────────┐
│  L6  SERVER         legend-sdlc-server (JAX-RS resources,          │
│                     Dropwizard, auth, Guice, backend selection)    │
├────────────────────────────────────────────────────────────────────┤
│  L5  BACKENDS       legend-sdlc-backend-gitlab                     │
│                     legend-sdlc-backend-fs                         │
│                     legend-sdlc-backend-inmemory (testing)         │
│                     (future: github, bitbucket, plain git, …)      │
├────────────────────────────────────────────────────────────────────┤
│  L4  BACKEND SPI    legend-sdlc-backend-api (the domain API,       │
│                     capability model, backend factory SPI)         │
│      + BACKEND TCK  legend-sdlc-backend-test-suite                 │
├────────────────────────────────────────────────────────────────────┤
│  L3  SDLC CORE      legend-sdlc-core (generic implementations of   │
│                     entity access/edit, configuration read/update, │
│                     dependency resolution, comparison — written    │
│                     once over L1+L2)                               │
├────────────────────────────────────────────────────────────────────┤
│  L2  STRUCTURE      legend-sdlc-project-structure (read-side       │
│                     layout knowledge, per the extraction doc)      │
├────────────────────────────────────────────────────────────────────┤
│  L1  STORAGE SPI    legend-sdlc-project-files                      │
│                     (ProjectFileAccessProvider et al.)             │
├────────────────────────────────────────────────────────────────────┤
│  L0  FOUNDATIONS    legend-sdlc-model, legend-sdlc-shared,         │
│                     legend-sdlc-entity-serialization               │
└────────────────────────────────────────────────────────────────────┘

      LOCAL TOOLING (parallel consumer of L0–L3, no L4–L6):
      legend-sdlc-local — entity editing on a local checkout,
      model-in-a-larger-project support, CLI-able,
      embeddable in IDE plugins (IntelliJ, LSP)
```

Rules:

- Nothing below L6 may depend on Dropwizard, Guice, pac4j, or JAX-RS.
- Nothing below L5 may depend on GitLab4J or any backend-specific library.
- L3 (core) depends only on L0–L2. A backend (L5) depends on L4 (and hence L0–L3).
- `legend-sdlc-local` depends on L0–L3 only. It must be usable from a plain `main`
  method, a Maven plugin, or another product's JVM with no container of any kind.

### 3.2 The two SPIs

There are deliberately **two** levels at which one can plug in, because they serve
different implementers:

1. **The storage SPI (L1)** — `ProjectFileAccessProvider`: files at revisions,
   file modifications, revision history. This is what a backend implements to get all
   of L3's generic functionality for free.
2. **The backend SPI (L4)** — the domain APIs (`EntityApi`, `ProjectApi`,
   `WorkspaceApi`, `ReviewApi`, …). Most of these get default implementations from L3;
   a backend *overrides* rather than *implements* them when it can do better natively
   (e.g. GitLab can serve a comparison via the GitLab compare API instead of walking
   files), or when the concept is inherently backend-native (reviews ≈ merge requests,
   workflows ≈ CI pipelines).

The minimal backend contract is therefore:

| Required | Why it cannot be generic |
|---|---|
| `ProjectFileAccessProvider` (file access + modification + revision contexts) | This *is* the storage |
| Project lifecycle (`ProjectApi`: create/get/list/delete, access roles if supported) | Maps to backend's repository concept |
| Workspace lifecycle (`WorkspaceApi`: create/list/delete/update) | Maps to backend's branching concept |
| A `BackendFactory` (see §3.5) | Bootstrap |

Everything else — entity read/write, project configuration read/update, dependency
resolution, comparison, conflict-resolution mechanics — comes from L3 defaults, and
the remaining concept-level APIs (reviews, versions, patches, workflows, builds) are
**optional capabilities** (§3.4).

### 3.3 What moves where

**`legend-sdlc-project-files` (L1) — rationalized, not just relocated:**

- Stays the home of `ProjectFileAccessProvider`, `ProjectFile`, `ProjectFileOperation`,
  `ProjectPaths`, caching/empty/abstract contexts.
- `SourceSpecification` is split. Today it conflates two ideas: *"which line of
  development"* (project / workspace / patch) and *"address of file content"*. L1 keeps
  an opaque, backend-interpreted source address; the workspace/patch taxonomy moves up
  to L4 where those concepts are defined. Concretely: L1's contract becomes
  `getFileAccessContext(sourceHandle, revisionId)` where `sourceHandle` is produced by
  L4/L5 code; generic L3 code never inspects its internals. This removes the last
  conceptual server dependency from the storage layer. (If splitting proves too
  disruptive in early phases, an acceptable interim is to move `SourceSpecification`
  as-is and split later; the phase plan allows both.)
- Packages migrate from `org.finos.legend.sdlc.server.project` to
  `org.finos.legend.sdlc.projectfiles` (or similar non-`server` package) — see §5 for
  the compatibility strategy.

**`legend-sdlc-project-structure` (L2) — as per the extraction doc, unchanged in scope:**

- Read-side of `ProjectStructure`, version factories, `EntitySourceDirectory`,
  `Simple*` configuration classes, the `maven/` structure implementations.
- One addition to the extraction doc: the **write-side** (`UpdateBuilder` /
  `updateProjectConfiguration()`) does *not* stay in the server — it moves to L3
  (`legend-sdlc-core`), because configuration updates are expressed entirely as
  `ProjectFileOperation`s against a `FileModificationContext` and are needed by local
  tooling too (e.g. adding a dependency to a local checkout). The extraction doc placed
  it in the server only because L3 did not exist in its world view. Note the
  layout-reconciliation companion plan intends to *replace* the imperative write-side
  contract (`collectUpdateProjectConfigurationOperations` on structures and extensions)
  with a declarative build-and-reconcile model — so the extraction should route all
  structure/extension write logic through a single dispatch point and treat the imperative
  contract as legacy scaffolding, not new public SPI (seam R1, §6).
- A second, forced addition follows from a founding design principle: **the project
  *structure* is universal and portable — the same structure works on GitHub, GitLab, or any
  other platform — while project structure *extensions* exist to handle the particulars of a
  deployment's environment** and are expected to differ per environment (e.g. a
  `.gitlab-ci.yml` with runner tags valid only on one GitLab instance, or a `settings.xml`
  for that environment's Maven repos). So the `ProjectStructureExtension` /
  `ProjectStructureExtensionProvider` **interfaces** travel down to L2 with the rest of the
  portable structure: they sit in the server today only because the updater does, and once
  the updater (which *applies* extensions) is at L3, the interfaces must be at L2 so L3 can
  call them and `legend-sdlc-local` / the TCK can exercise them.
- **Concrete** extensions and their provider are **deployment-scoped configuration**, *not*
  part of the generic backend. A *server* deployment binds them via
  `ProjectStructureConfiguration` (as today); local/IDE tooling that maintains a *managed*
  project must likewise be supplied that deployment's provider, because a managed project
  belongs to its deployment whether it is reached through the server or edited locally (only
  *embedded* projects have no extensions). The L5 extraction must keep extensions out of the
  backend jar. Backend choice and extensions are **decoupled by design but correlated in
  practice** — both follow from the deployment environment, so a GitLab backend is normally
  paired with GitLab-oriented extensions; the pairing is conventional, not enforced. Two
  GitLab instances (prod vs. sandbox) are two deployments running the same `gitlab` backend
  with different extensions. See
  [`project-structure-configuration-options.md`](project-structure-configuration-options.md)
  §5.

**`legend-sdlc-core` (L3) — new; the de-duplication target:**

- `EntityAccessContext` / `EntityModificationContext` implementations over
  `FileAccessContext` + `ProjectStructure` (today's duplicated logic in
  `GitLabEntityApi`/`FileSystemEntityApi`, factored once: entity-file discovery,
  serialization/deserialization via `legend-sdlc-entity-serialization`, create/update/
  delete computed as file operations).
- Project configuration read (already in `ProjectStructure`) and update
  (`ProjectStructureUpdater`, generalized from `UpdateBuilder`).
- Dependency resolution (`DependenciesApiImpl` is already backend-neutral — it moves
  here).
- Comparison at the file/entity level (compute diffs by walking two
  `FileAccessContext`s); backends may override with native diffing.
- Conflict-resolution mechanics that are pure file/entity computation.

**`legend-sdlc-backend-api` (L4) — extracted from `server/domain/api/**`:**

- The API interfaces (`EntityApi`, `ProjectApi`, `ProjectConfigurationApi`,
  `WorkspaceApi`, `RevisionApi`, `ReviewApi`, `VersionApi`, `PatchApi`, `WorkflowApi`,
  `WorkflowJobApi`, `BuildApi`, `ComparisonApi`, `ConflictResolutionApi`, `BackupApi`,
  `DependenciesApi`, `UserApi`, `IssueApi`), the workspace/source taxonomy, and
  `ProjectConfigurationUpdater`.
- A `Backend` aggregate interface: one object exposing the APIs plus capability
  discovery, so consumers hold one handle rather than seventeen injected interfaces.
- The capability model (§3.4) and backend factory SPI (§3.5).
- Abstract base classes wiring L3 defaults: a backend extends e.g.
  `AbstractBackend`, supplies its `ProjectFileAccessProvider` + lifecycle APIs, and
  inherits entity/configuration/dependency/comparison behavior.

**`legend-sdlc-backend-test-suite` (L4) — new:**

- A TCK: abstract JUnit test classes parameterized over a `BackendFactory`, exercising
  the full SPI contract (file access semantics, entity round-trips, configuration
  updates, workspace lifecycle, capability declarations vs. actual behavior). "A
  clearly defined way to implement a backend" is as much executable contract as
  documentation. The in-memory and FS backends run it in this repo; GitLab runs it
  against the existing integration-test infrastructure; external backend authors run
  it against theirs.

**`legend-sdlc-backend-gitlab` (L5) — extracted from `server/gitlab/**`:**

- The 50 GitLab files, minus whatever dissolves into L3 defaults. Expected to shrink
  substantially: the file-walking halves of the `GitLab*Api` classes disappear;
  what remains is GitLab API plumbing (auth, branches/MRs/pipelines/tags ↔
  workspaces/reviews/workflows/versions) — the part that genuinely *is* GitLab.
- GitLab4J and GitLab auth move here; the server keeps no GitLab dependency.

**`legend-sdlc-backend-fs` (L5) — `legend-sdlc-server-fs` refactored:**

- Keeps its local-git storage implementation; drops its duplicated entity/config logic
  in favor of L3; drops its parallel server startup in favor of the standard server +
  backend selection. (The module today is 29 files largely because it had to stub
  every API; with capabilities + defaults it should be a fraction of that.)

**`legend-sdlc-server` (L6) — what remains:**

- JAX-RS resources, Dropwizard application, configuration, pac4j auth, Guice modules,
  depot client, error handling. Resources are rewritten only in their injection: they
  consume the `Backend` (L4) instead of concrete implementations. Routes and payloads
  unchanged.
- Returns `404`/`501`-style responses derived from the capability model where a
  deployment's backend lacks an optional capability (replacing today's ad-hoc
  `UnsupportedOperationException`s from stub implementations).

**`legend-sdlc-local` — new; the out-of-server consumer:**

See §4.

### 3.4 Optional capabilities

Not every backend has merge requests, CI pipelines, release tags, or patch branches.
Today this surfaces as stub classes throwing `UnsupportedOperationException`
(`legend-sdlc-server-fs` is full of them). Instead:

- L4 defines a small capability enumeration (e.g. `REVIEWS`, `WORKFLOWS`, `VERSIONS`,
  `PATCHES`, `BUILDS`, `BACKUP`, `ISSUES`, plus finer flags where existing APIs imply
  them, e.g. user-vs-group workspaces).
- `Backend.getCapabilities()` reports what is supported; `Backend.getReviewApi()` etc.
  throw a single well-defined `UnsupportedCapabilityException` for absent ones.
- The server maps absent capabilities to consistent HTTP responses, and can expose a
  capabilities endpoint so Studio (eventually) can adapt its UI rather than discovering
  unsupported features by error.
- The TCK verifies that declared capabilities work and undeclared ones fail in the
  defined way.

This is what makes "minimize what a backend implements" real: a minimal backend is
storage + project/workspace lifecycle + `getCapabilities() == {}`, and it is already a
useful deployment (browse, edit, configure, resolve dependencies, compare).

### 3.5 Backend selection

Replace inheritance-based assembly (`BaseLegendSDLCServer` → GitLab bundles) with
configuration + `ServiceLoader`:

```java
public interface BackendFactory
{
    String getType();                                  // e.g. "gitlab", "filesystem"
    Class<? extends BackendConfiguration> getConfigurationClass();
    Backend build(BackendConfiguration config, BackendEnvironment environment);
}
```

- Registered via `META-INF/services`; the server config gains a polymorphic
  `backend:` section (Jackson subtype resolution by `type`, the same pattern the
  Legend stack already uses for store extensions):

  ```yaml
  backend:
    type: gitlab
    gitlab:
      ...existing GitLab configuration...
  ```

- `BackendEnvironment` is the server-provided context a backend may need: the
  per-request user identity/credentials, object mapper, metrics. This is the one
  genuinely tricky seam: GitLab's implementation is session-scoped (per-user OAuth via
  `GitLabUserContext`). The SPI must therefore distinguish the *backend* (deployment
  -scoped, built once) from the *backend session* (request-scoped view bound to an
  authenticated user). Concretely: `Backend.forUser(UserContext)` (or the factory
  produces request-scoped API objects, as Guice does today — the design point is that
  the SPI owns this contract, not Guice). Auth *protocols* (pac4j filters, cookies,
  tokens) stay in the server; auth *material* (the resulting identity/credential)
  crosses the SPI as data.
- The single `legend-sdlc-server` distribution can bundle any set of backend jars;
  deployment chooses by configuration. One main class, no per-backend server modules.

## 4. Local, Embedded, and IDE Usage (`legend-sdlc-local`)

The second headline goal: use SDLC functionality on a local working copy, with no
server, including when the Legend model is part of a larger non-Legend project. The
sharpest consumer of this is a **Legend IDE plugin** (IntelliJ, or an LSP server), which
§4.4–4.5 promote to first-class requirements — they constrain the design of L0–L3 and this
module, not just `legend-sdlc-local` in isolation.

### 4.1 What it is

A small module providing:

- **`LocalProjectFileAccessProvider`** (actually in L1 or here): `FileAccessContext` /
  `FileModificationContext` over a directory tree. No revisions (`RevisionAccessContext`
  unsupported or backed by JGit later); modifications write files directly. This is
  *not* a backend in the L4 sense — no projects, no workspaces — it plugs in at L1,
  which is exactly why L3 functionality must depend only on L1/L2.
- **`LocalModel` (working-copy façade)**: discover and open a Legend model rooted at a
  directory; read entities; **edit entities** (create/update/delete via the L3 entity
  modification logic, written straight to the source files in the correct source
  directory and serialization format); read and **update project configuration**
  (add/remove dependencies, change structure version) via the L3 updater.
- A thin CLI veneer is optional and deferred; the API is the deliverable.

### 4.2 Model-in-a-larger-project

Key design decision: **rooting is a storage-layer concern.** `FileAccessContext` paths
are project-relative; therefore a Legend model living at `analytics/model/` inside a
larger repository is handled by a *rooted* file access context (a decorator that
prefixes/strips the subpath). Everything above L1 — structure, entities, configuration
— is oblivious. This gives us:

- One repo containing one or more Legend models, each at its own root, each opened as
  its own `LocalModel`.
- Discovery helper: scan a tree for `project.json` files (with sensible pruning) to
  enumerate the models in a checkout.
- Non-Legend content is simply outside the root (or inside it but outside entity
  source directories, which L2 already tolerates).

This also benefits backends: nothing prevents a future backend from mapping a "project"
to a subdirectory of a repository rather than a whole repository, using the same rooted
context — a frequently requested monorepo pattern, and it falls out of the layering
rather than requiring new machinery. (Making the *GitLab* backend support this is out
of scope; the point is the layers do not preclude it.)

### 4.3 Relationship to existing pieces

- `EntityLoader` (read-only, directories/jars) remains for consumers that just need
  entities; `LocalModel` is the structure-aware, writable superset. `EntityLoader`
  stays untouched for compatibility.
- The EMIT project extractor need (per the extraction doc's motivation) is satisfied
  by L2 alone; `legend-sdlc-local` is the fuller answer for tooling that also edits.

### 4.4 IDE plugins: the two forms

An IDE plugin uses this module in one of two forms, which map onto the existing
`ProjectType` enum (`MANAGED`, `EMBEDDED`) — so the layers already distinguish them:

- **Form 1 — a fully managed project, edited via the IDE instead of Studio**
  (`ProjectType.MANAGED`). The plugin needs **entity editing *and* project-structure /
  configuration editing** (add/remove dependencies, change structure version, set
  version-scoped options — see the companion config-options plan). The IDE owns version
  control and reviews, so the plugin needs **no L4 backend**: it edits the working copy
  through L1's local file context and L3's entity/configuration logic, and the IDE commits
  the result with its own Git. It does, however, still belong to a *deployment*: structure /
  configuration edits that touch extension-managed files (CI config, `settings.xml`) need
  that deployment's `ProjectStructureExtensionProvider` supplied to `legend-sdlc-local`. That
  provider is the one piece of "environment" the IDE must be told about — it is not an L4
  backend, just the deployment's extension binding (§3.3).
- **Form 2 — a Legend model embedded in a larger non-Legend project**
  (`ProjectType.EMBEDDED`, typically at a subpath). The plugin needs **entity editing
  only**; the host owns everything else. This is §4.2's rooted-context scenario. Embedded
  projects have no project structure extensions by definition, so this form involves no
  environment-specific scaffolding at all.

Both forms consume L0–L3 + `legend-sdlc-local`; neither touches L4–L6. This is the same
"another product's JVM, no container" rule from §3.1 — an IntelliJ/LSP process *is* that
JVM.

### 4.5 What IDE embedding adds to the design

A long-lived IDE host is a stricter environment than a one-shot CLI, and these constraints
must be honored *down the stack*, not bolted onto `legend-sdlc-local`:

- **No process-global mutable state below L4.** Strengthen §3.1's "usable from a plain
  `main`" into "safe to instantiate many times concurrently in a shared JVM." Static
  caches, singletons, and `ServiceLoader` results captured in statics are out for L0–L3 and
  this module. (The backend `ServiceLoader` stays an L6 concern.)
- **A real lifecycle, because the plugin is not the sole writer.** The user edits files
  directly and the IDE's Git mutates the working tree underneath. `LocalModel` therefore
  needs `open(root) → handle → close` plus an explicit **invalidate/refresh** (or
  file-watch integration): caches must be invalidatable so the in-memory view reconciles
  with on-disk reality rather than diverging. State the threading contract for a handle
  (e.g. "not thread-safe; callers serialize") — IDEs call from many threads.
- **A two-tier surface matching the two forms.** Expose a minimal *entities-only* editing
  capability (Form 2) that does not drag in structure-update machinery, and a
  *structure-aware* capability (Form 1) layered on top. This mirrors the L2/L3 split:
  entity read/write vs. project-structure update are already distinct.
- **Diagnostics as data, not exceptions.** LSP/IntelliJ want structured, ideally positioned
  validation results (malformed entity, unresolved dependency, invalid config-option
  value). The structure/configuration layer should offer validation that *returns* results;
  deep semantic compilation stays an Engine concern, but the SDLC/Engine boundary should be
  clean — SDLC validates *layout and configuration*, Engine validates *model semantics*.
- **A stable, lean, published API.** Just as the backend SPI (L4) is a contract for backend
  authors, this becomes the contract for IDE-plugin authors: a small, documented,
  semver-stable surface (the API is the deliverable, per §4.1), with a deliberately lean,
  shade-friendly dependency footprint — the layering already bars Dropwizard/Guice/JAX-RS
  below L6; be equally deliberate about Jackson/Eclipse-Collections so the plugin does not
  fight its host's classpath.

### 4.6 Open question: handling a managed project in a local/IDE setting

Form 1 needs to be thought through before Phase 6, because a managed project **belongs to a
deployment** even when edited locally: structure / configuration edits that touch
extension-managed files (CI config, `settings.xml`, …) — and the surfacing of
extension-scoped options — require that deployment's `ProjectStructureExtensionProvider`
(§3.3). Entity editing needs none of this; structure-aware editing does. Three sub-questions,
none of which should be answered by accident:

- **How does the tooling acquire the provider?**
  - *(a) Bundled* with the plugin — simplest, works offline, but couples plugin releases to
    deployment changes and forces redistribution whenever an extension changes.
  - *(b) Fetched from the SDLC server the project belongs to* — the server already holds it
    via `ProjectStructureConfiguration`, so the **Phase-4 capability/discovery surface** can
    serve the extension schema and behavior. Always current; no offline story.
  - *(c) User/workspace-configured* — most flexible, most setup burden, most room for
    misconfiguration.
- **Which deployment does this checkout belong to?** `project.json` records the
  structure+extension *version*, not deployment identity. The tooling needs a way to resolve
  "this checkout → that deployment's provider" (the Git remote is a hint, but hosting ≠
  deployment config — see the "correlated in practice" caveat in §3.3).
- **What is the offline / provider-absent degraded mode?** A safe default: always permit
  entity editing; for structure/config edits with no provider available, either block the
  operations that would touch extension-managed files or perform them and **leave
  extension-managed files untouched**, letting the deployment's extension reconcile when the
  change returns through the server. Pick one explicitly.

A workable default is **(b) with a bundled/cached fallback**: fetch from the owning server
when reachable, cache it, and degrade to entity-only editing when neither a cached provider
nor a server is available. But this is a contract to decide on the record — settled in the
Phase 4 SPI/discovery review (which owns the server side) and confirmed in Phase 6 (which
builds the local consumer). The corollary for `legend-sdlc-local`'s API: structure-aware
operations must take a `ProjectStructureExtensionProvider` as an explicit input, and the
module must behave well when it is absent.

## 5. Compatibility Strategy

- **REST API**: unchanged routes, payloads, and semantics for GitLab deployments.
  The capability model only changes responses for endpoints that today fail anyway.
- **Java packages**: extracted classes leave `org.finos.legend.sdlc.server.*` for
  non-server packages (`org.finos.legend.sdlc.projectfiles`, `…sdlc.project.structure`,
  `…sdlc.core`, `…sdlc.backend.*`). Unlike the extraction doc (which proposed keeping
  the `server.*` package in the new modules), this plan accepts a source-incompatible
  rename **with a deprecation bridge**: the old `server.*` names remain for one release
  cycle as deprecated subclasses/aliases in a `legend-sdlc-server-compat` shim (or in
  the server module itself), then are removed. Rationale: the `server.*` package
  permanently lying about its location is worse than a one-time migration, and split
  packages across modules invite JPMS and shading trouble later. This is the main
  deliberate divergence from the earlier doc.
- **Maven coordinates**: existing modules keep their artifactIds; new modules are
  additive; `legend-sdlc-server-fs` is renamed (with relocation POM) to
  `legend-sdlc-backend-fs`.
- **Downstream consumers** (Studio server config, internal deployments): a GitLab
  deployment's only required change is moving its GitLab config under the new
  polymorphic `backend:` key; a legacy-config adapter can keep even that working for
  a transition release.
### The external-consumer contract: what stays stable, what may break

The open-sourcing of legend-sdlc left external consumers with different kinds of
dependency on this repo, and they get different promises. The origin project is the
extreme case: it implements structure versions 1–10 — a *split version space* (this repo
has 0 and 11–13), assembled at deployment time by the `ServiceLoader` lookup in
`ProjectStructureFactory` — plus its own project structure extensions. But the general
expectation for external projects is narrower: **they define extensions, not structure
versions.** The origin project's versions 1–10 are grandfathered, not a pattern.

**Stable interfaces — evolve additively, deprecation cycles only:**

- **`legend-sdlc-model`** and **`legend-sdlc-entity-serialization`** (L0). Already in
  non-server packages; they do not move. New configuration accessors (config-options
  seam S1) arrive as `default` methods.
- **The Maven plugins** — `legend-sdlc-entity-maven-plugin`, the
  `legend-sdlc-generation-*-maven-plugin` family,
  `legend-sdlc-test-generation-maven-plugin`, `legend-sdlc-version-package-maven-plugin`
  — and `legend-sdlc-test-utils`. These are a standalone public product, not an SDLC
  implementation detail: **any** build can use them, including hand-written POMs in
  projects that never touch an SDLC server. Their consumer set is therefore unknown and
  unenumerable — managed projects' generated POMs (including those on external versions
  1–10) are the *tractable* subset, since a structure version upgrade can rewrite those
  references, while nobody can rewrite a hand-authored build. So their coordinates,
  goals, and parameter contracts cannot break even transiently. (They already sit
  outside the server dependency-wise, §2; this plan does not move them.)
- **The extension SPI** — `ProjectStructureExtension` / `ProjectStructureExtensionProvider`.
  Defining extensions is exactly what external projects are *supposed* to do, so these
  interfaces get the strongest treatment: the L2 relocation uses an **interface bridge**
  (the old `server.*` interface remains for the deprecation cycle, extending the
  relocated L2 interface; the updater consumes the L2 type, so existing implementations
  stay valid unmodified), and every new SPI method arrives as a `default` method —
  `getConfigurationProperties()` from the config-options plan, `contributeToLayout` from
  the layout-reconciliation plan (defaulting to contributing nothing). Nothing is removed
  without a deprecation cycle, and removing the deprecated imperative write-side is
  additionally gated on external extension authors (that plan's §6).

**The structure authoring framework — may break, with a specified path:**

`ProjectStructureVersionFactory`, `ProjectStructure`,
`MavenProjectStructure`/`MultiModuleMavenProjectStructure`, and their protected helper
surface (`addOrModifyModuleFile`, `moveOrAddOrModifyModuleFile`, `getDefaultModuleName`,
…) are the framework for *authoring versions*, whose only known external implementor is
the origin project. This plan and its companions may change that framework
**incompatibly** — the layout-reconciliation plan intends to reshape authoring outright —
provided every break ships with a migration recipe that is not much more than mechanical.
The recipe for the breaks this plan makes (Phase 2):

1. Depend on `legend-sdlc-project-structure` (L2) instead of `legend-sdlc-server` — the
   origin project stops needing the whole server to implement structure versions.
2. Update imports for the package rename; re-key
   `META-INF/services/…ProjectStructureVersionFactory` to the relocated class name.
3. Recompile — that is the whole port. Versions 1–10 are legacy and frozen: the new SPI
   methods default to empty, the feature/option machinery activates only for versions
   that declare it, and reconciliation dispatch keys on implementing `buildLayout`, so
   nothing needs re-authoring.
4. Verify with the origin project's own version tests (and the TCK layout invariants
   once seam R2 lands).

As a courtesy that decouples release timing — not a compatibility promise — the bridge
release can dual-key the `ServiceLoader` lookup (load the old and new service keys,
merged) so external factory jars keep loading before they migrate. Later framework
breaks (the reconciliation plan's authoring model above all) owe the same artifact: a
short migration note shipped with the release, stating the break and the
do-this-then-that recipe.

## 6. Phased Plan

Each phase is independently releasable and keeps the full build green. Phases 1–2 are
the existing extraction doc, restated; details live there.

**Phase 1 — Foundations (mostly done).**
Complete `legend-sdlc-shared`; de-JAX-RS `LegendSDLCServerException` (rename to
`LegendSDLCException` in its new home, deprecated subclass left behind), move it down.

**Phase 2 — Project structure extraction (= extraction doc Phases 2–4, amended).**
Split `ProjectStructure` read/write; create `legend-sdlc-project-structure` (L2).
Amendment per §3.3: the write-side `ProjectStructureUpdater` is extracted as a
standalone class with no server imports, so it can move to L3 in Phase 3 (it may sit
in the server temporarily; it must not *bind* to it).
*Reserved seam S2 (config-options plan):* land `getConfigurationProperties()` (default
empty) on the L2 version-factory abstraction and on `ProjectStructureExtension` so versions
and extensions can declare typed options later without an SPI break.
*Reserved seam R1, part 1 (layout-reconciliation plan):* extract the write-side so the
updater reaches structure/extension write logic through a **single dispatch point**, and
keep `collectUpdateProjectConfigurationOperations` framed as the legacy contract — do not
design new public SPI surface around it.
*External-consumer constraint:* the origin project's versions 1–10 subclass exactly the
classes this phase moves, and external projects implement the extension interfaces (§5).
The extension SPI relocation must use the interface bridge (additive-only evolution); the
version-authoring framework may break, but this phase ships the §5 migration recipe with
it — and, ideally, the dual-keyed `ServiceLoader` lookup for release-timing slack.

**Phase 3 — SDLC core (L3).**
Create `legend-sdlc-core`. Factor the duplicated entity access/modification logic out
of `GitLabEntityApi`/`FileSystemEntityApi` into core implementations; move
`DependenciesApiImpl`, comparison walking, and `ProjectStructureUpdater` in. GitLab
and FS api classes delegate to core (they shrink but do not move yet). This phase has
the highest regression risk and the highest payoff; it is pure refactoring with the
existing test suites as the net, and it is where the TCK should begin to grow
(initially run against GitLab-mocked and FS backends in-repo).
*Reserved seam S1 (config-options plan):* while the L0 config model and the updater are
open here, introduce the namespaced `getStructureConfiguration()` /
`getExtensionConfiguration()` bags (the two existing flat booleans can live there behind
their now-deprecated getters) and do **not** add further top-level config booleans — this
avoids re-migrating `project.json` later.
*Reserved seams R1 (part 2) and R2 (layout-reconciliation plan):* as the updater lands in
L3, preserve the single write-side dispatch point so a declarative build-and-reconcile
path can sit beside the imperative one without touching callers; and as the TCK begins to
grow, design it to express the layout invariants (update ≡ create; reconciling an
already-correct project is a no-op) so reconciling structure versions are certified by the
same suite.

**Phase 4 — Backend SPI (L4).**
Move `domain/api/**` to `legend-sdlc-backend-api`; introduce `Backend`,
`BackendFactory`, `BackendEnvironment`/session contract, capability model,
`AbstractBackend` defaults wired to L3. Server resources switch to consuming
`Backend`. Server config gains the polymorphic `backend:` section with a
GitLab-only registration; `BaseLegendSDLCServer`'s GitLab hard-wiring is removed.
*Reserved seam S3 (config-options plan):* design the capability/discovery surface so a
"describe what this structure/extension supports" call can also carry option schemas,
rather than bolting on a separate options endpoint afterward. As L5 is extracted, keep
project structure extensions as **deployment-scoped configuration** (the deployment supplies
the `ProjectStructureExtensionProvider` — to the server via `ProjectStructureConfiguration`,
or to local/IDE tooling for a managed project) — they are *not* backend-owned (§3.3); the
generic backend jar must not bundle a deployment's extensions.

**Phase 5 — Backend extraction (L5).**
Move `server/gitlab/**` to `legend-sdlc-backend-gitlab` (GitLab4J leaves the server's
dependency tree). Refit `legend-sdlc-server-fs` as `legend-sdlc-backend-fs` on the
defaults + capabilities; delete its parallel server. Add
`legend-sdlc-backend-inmemory` and make the TCK (`legend-sdlc-backend-test-suite`) a
published artifact run by all three backends in CI.

**Phase 6 — Local / IDE tooling.**
`LocalProjectFileAccessProvider` (rooted contexts, discovery), `LocalModel` editing
façade in `legend-sdlc-local`, with the §4.5 IDE constraints baked in: `open/close` +
invalidate/refresh lifecycle, a stated threading contract, the two-tier (entities-only vs.
structure-aware) surface, and validation that returns results. Validate the embedded
scenario end-to-end: a test repository containing non-Legend content plus two models; open,
edit entities, update configuration, verify files; then mutate files on disk underneath an
open handle and confirm refresh reconciles. The published API surface is itself a
deliverable (it is the IDE-plugin contract).

Sequencing notes: Phases 1–3 do not change module boundaries seen by deployments and
can proceed immediately. Phase 4 is the API-shape commitment and deserves a design
review on the SPI details (especially the session/auth contract and capability set)
before code. Phases 5 and 6 are independent of each other once 4 lands.

## 7. Risks and Open Questions

| Risk / question | Notes / mitigation |
|---|---|
| **The `SourceSpecification` split (L1 vs L4) may fight the codebase.** Workspace/patch types are threaded through everything. | The phase plan permits the interim "move as-is, split later" (§3.3). The split is the right end state but is not load-bearing for Phases 2–5. |
| **Session/auth contract is the hardest SPI surface.** GitLab needs per-user OAuth state with re-auth flows; another backend may use a service account; local use has no auth at all. | Treat as the centerpiece of the Phase 4 design review. The litmus test: the FS backend's session object should be trivial, and pac4j must not appear below L6. |
| **Behavioral drift between GitLab and generic implementations during Phase 3.** GitLab's entity/config code has accumulated GitLab-shaped quirks (pagination, retries, caching via `CachingFileAccessContext`). | Factor *behavior* into core but keep backend override points; characterization tests before moving; TCK asserts the contract both must satisfy. |
| **Patch/version/review semantics are Git-flavored even as "generic" APIs.** E.g. patch release branches, MR-based reviews. | Acceptable: the L4 APIs describe SDLC concepts (line of development, proposed change, release); Git-less backends either map them or omit the capability. Document each API's contract in backend-neutral terms as part of Phase 4. |
| **Package rename breaks external code.** Two distinct populations (§5): extension implementors — the *expected* kind of external project, the origin project among them — and the origin project's structure versions 1–10, grandfathered inheritors of the authoring framework. | Per §5's external-consumer contract: the extension SPI gets an interface bridge and additive-only evolution, so implementations stay valid unmodified; the authoring framework is allowed to break, but every break ships a mechanical migration recipe (dual-keyed `ServiceLoader` lookup for timing slack); coordinate bridge removal with the origin project. The alternative (frozen `server.*` names forever) is worse. |
| **Two plans diverging** (this doc vs `project-structure-extraction.md`). | The extraction doc stays as the detailed treatment of Phases 1–2, with a banner pointing here; its two superseded decisions (keeping `server.*` packages; leaving the updater in the server) are noted in §3.3/§5. |
| **Scope creep toward monorepo-projects in backends.** | Explicitly deferred (§4.2): the architecture allows it; no backend implements it in this plan. |
| **Guice request-scoping is load-bearing in subtle ways** (e.g. `UserContext`, lazy GitLab clients). | Phase 4 keeps Guice at L6 but moves the *contract* into the SPI; integration tests on the GitLab backend with real auth flows before/after. |
| **IDE embedding exposes hidden global state / lifecycle gaps.** A long-lived host with concurrent instances and external file mutation will surface any static cache or non-invalidatable state in L0–L3 (§4.5). | Make "no process-global mutable state below L4" an enforced rule (audit static fields during Phases 3 and 6); design `LocalModel` with explicit invalidate/refresh and a stated threading contract; test the "files change under an open handle" case in Phase 6. |
| **Managed projects edited locally need their deployment's extensions.** How local/IDE tooling acquires the right `ProjectStructureExtensionProvider`, identifies which deployment a checkout belongs to, and behaves offline is unsettled. | Open question — see §4.6. Leaning: fetch from the owning server (reuse the Phase-4 discovery surface) with a cached/bundled fallback and an entity-only degraded mode. Decide in the Phase 4 review; confirm in Phase 6. |
| **Companion plans and this plan drift** (separate docs, overlapping classes: config-options; layout-reconciliation). | Both companions are *dependent* and sequenced onto this plan, which reserves seams S1–S3 and R1–R2 (§6) so they land additively. Shared contracts: extensions are deployment-scoped config (interface in L2; concrete providers bound per deployment — to the server, or to local tooling for a managed project), *not* bundled into the generic backend (§3.3); and the imperative write-side extracted in Phases 2–3 is the *legacy* contract behind one dispatch point, which the layout-reconciliation plan replaces. |

## 8. End-state Dependency Graph (SDLC modules only)

```
legend-sdlc-shared ─────────────┐
legend-sdlc-model ──────────────┤
legend-sdlc-entity-serialization┤  (L0: no SDLC deps except model←shared as today)
                                │
legend-sdlc-project-files ──────┤  L1: shared, model
legend-sdlc-project-structure ──┤  L2: + project-files, entity-serialization
legend-sdlc-core ───────────────┤  L3: + project-structure
legend-sdlc-backend-api ────────┤  L4: + core
legend-sdlc-backend-test-suite ─┤  L4: + backend-api (test-jar style)
                                │
legend-sdlc-backend-gitlab ─────┤  L5: + backend-api (+ gitlab4j)
legend-sdlc-backend-fs ─────────┤  L5: + backend-api (+ jgit)
legend-sdlc-backend-inmemory ───┤  L5: + backend-api
                                │
legend-sdlc-server ─────────────┤  L6: + backend-api (+ dropwizard/guice/pac4j);
                                │      backends arrive on the runtime classpath
legend-sdlc-local ──────────────┘  L0–L3 only
```

The generation modules and Maven plugins keep their current positions (L0-adjacent
consumers) and are untouched except where they can later choose to use
`legend-sdlc-local` conveniences.
