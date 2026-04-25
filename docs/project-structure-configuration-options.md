# Version- and Extension-Scoped Project Configuration Options

## 1. Problem

Some project configuration is meaningful only for a particular **project structure
version**, or only for a particular **project structure extension version** — not for
projects in general. Today such configuration is modeled as flat, top-level fields on the
version-independent `ProjectConfiguration`, which does not scale and leaks
version/deployment concerns into the shared model.

The motivating example is already in the tree. `ProjectConfiguration` carries:

```java
default Boolean getProduceShadedServiceJar() { return null; }
default Boolean getRunDependencyTests()      { return null; }
```

`produceShadedServiceJar` only makes sense for the multi-module Maven structure that has a
`service_execution` module (it gates a Maven shade plugin); it is meaningless for a
structure version that has no such module. Yet it sits on the top-level model, and adding
it required touching **four** places in lockstep:

| Layer | File | What had to change |
|---|---|---|
| L0 model | `ProjectConfiguration` | new `getProduceShadedServiceJar()` default |
| project.json (de)serialization | `SimpleProjectConfiguration` | new field, ctor arg, getter/setter, `@JsonProperty` |
| update logic | `ProjectConfigurationUpdater` | new field, `with…`/`set…`, merge in `update()` |
| REST | `UpdateProjectConfigurationCommand` | new field, `@JsonProperty`, wire into updater |

Every new knob repeats this four-place edit. Worse, there is **no schema or discovery**:
a client (Studio, or an IDE plugin) cannot ask "which options apply to structure version
N?" — the set is hard-coded into the client. And the field is half-wired: `produceShaded­
ServiceJar` exists in the model and updater but the V13 factory still adds the shade plugin
unconditionally, so the flag does nothing yet. It is a top-level field that should have been
a property of one structure version from the start.

Two further use cases sharpen the requirement — both about **shaping what a managed
project contains** in the multi-module Maven structures, and both currently impossible:

1. **User-maintained additional modules.** Some users want a *managed* project plus one
   extra module they maintain themselves (custom Java code, tooling, ...) in the same
   repository and build. Today that cannot survive: every configuration update regenerates
   the root `pom.xml` from the structure's fixed module set
   (`MultiModuleMavenProjectStructure.configureMavenProjectModel`), silently dropping the
   user's `<module>` entry. There is nowhere in `project.json` to record "this project also
   contains module X — keep it in the build, but leave its contents alone."

2. **Selecting optional features.** Every managed project gets *everything* its structure
   version defines, used or not — V13 generates `versioned-entities`, `service-execution`,
   and `file-generation` modules alongside `entities`. A project that defines no services
   still carries a service-execution module and pays its build/CI cost. The right unit of
   choice here is not the module but the **feature**: a project should select among named
   optional features its version offers (*service execution*, *file generation*, …), and
   whether a given module exists is a *consequence* of that selection. The `entities`
   module *is* the project — not a feature, never deselectable — but everything else a
   version generates is at least potentially optional per project. Framing this as
   feature selection rather than module exclusion also subsumes the motivating flag:
   producing a shaded jar is not a free-standing project property, it is a *parameter of
   the service-execution feature* (§4.8).

Both are per-project, version-scoped configuration — they parameterize how one structure
version lays the project out — but they are **list-valued** and **schema-dependent** (the
feature menu is the version's to declare), which is precisely why the answer must be a
typed, schema-validated mechanism (§4.8) and not another round of flat top-level booleans.

The same need exists one level out, for **extensions**. Project structure extensions
(`ProjectStructureExtension`, keyed by `(structureVersion, extensionVersion)`) are the
deployment-specific hook — e.g. a GitLab deployment's extension that injects CI files. A
deployment will want options of its own: a GitLab extension might expose which runner tags
the generated pipeline targets. Those options are even less appropriate as top-level model
fields: they are specific to one deployment's extension, invisible to every other.

### Goal

Let a project structure version, and a project structure extension version, **declare**
typed configuration options; **persist** their values in a namespaced section of
`project.json`; **validate** values against the declared schema; **expose** the schema for
discovery so clients render the right form; and **consume** values during structure
build/update — all while the version-independent `ProjectConfiguration` and the generic
SDLC core stay ignorant of any specific option.

### Non-goals

- Removing the existing top-level getters outright. `getProduceShadedServiceJar()` /
  `getRunDependencyTests()` become deprecated read bridges over the new mechanism (§6), not
  hard breaks.
- A general user-defined settings system. Options are declared by **code** (the version
  factory or the extension), not by end users; this is typed metadata, not a free-form map
  that anything can write to. (User-supplied *values* are fine — the feature selection and
  module lists of §4.8 are user data — so long as the option itself, its type, and its
  validation are declared by the owning version.)
- Per-entity or per-module configuration. The unit remains the project (`project.json`).

## 2. Relationship to the Re-architecture — and the decision

This is the question raised alongside the re-architecture
([`re-architecture.md`](re-architecture.md)): fold this in, or keep it separate?

**Decision: keep it as this separate plan, but treat it as *dependent on* — not orthogonal
to — the re-architecture. It is sequenced onto the new layers, and the re-architecture
reserves three small seams (§8) so this lands additively rather than as a re-migration.**

Why separate rather than merged:

1. **It crosses two of the re-architecture's load-bearing non-goals.** That plan explicitly
   does *not* change the REST surface or the on-disk `project.json` layout; those non-goals
   are what let it be reviewed as a behavior-preserving re-layering with the existing test
   suite as the safety net. This work **does** change both: a new namespaced block in
   `project.json` and a new discovery endpoint (plus additive request fields). Merging it in
   would forfeit "pure refactor, existing tests prove it" and entangle two different risk
   profiles.
2. **Different audience and review gate.** The re-architecture is an internal platform
   refactor. This is a user-visible feature touching Studio (form rendering) and IDE plugins
   (§7 of the re-architecture's local story). It deserves its own design review with the
   client teams.
3. **It delivers value on its own** and does not *require* the modularization to function —
   whereas the modularization does not require it.

Why not orthogonal:

1. **It edits exactly the classes the re-architecture relocates** — `ProjectConfiguration`
   (L0), `ProjectStructure` + version factories + `ProjectStructureExtension` (→ L2),
   `ProjectConfigurationUpdater` (→ L3), and the REST command (L6). Doing it *before* those
   move means doing it twice; doing it *after* means designing against the final boundaries.
2. **Extension options depend on the extension SPI's new home.** The re-architecture moves
   the configuration *updater* to L3, which forces the `ProjectStructureExtension` interface
   down to L2 (L3 applies extensions) while concrete extensions remain deployment-scoped
   server config — *not* backend-owned (§5). Extension-scoped options are declared on that
   interface, so they must be designed against its settled L2 shape, not the server-side
   shape it has today.
3. **It is the same shape of problem as the capability model.** The re-architecture already
   introduces "declare what is supported, expose it for discovery, enforce it." Option
   schemas are "declare what is configurable, expose it for discovery, validate against it."
   They should share machinery, not invent two parallel discovery endpoints.

**Therefore: schedule this after re-architecture Phases 1–4** (foundations, structure
extraction → L2, core → L3, backend SPI + capability model → L4), and have those phases
honor the reserved seams in §8.

## 3. Current State

- **`ProjectConfiguration`** (`legend-sdlc-model`, L0) — the version-independent model.
  Has accreted `getRunDependencyTests()` and `getProduceShadedServiceJar()` as `default`
  getters returning `null`.
- **`SimpleProjectConfiguration`** (server today; the `project.json` (de)serializer) —
  flat `@JsonProperty` fields, one per option.
- **`ProjectConfigurationUpdater`** (server `domain/api/project`; → L3/L4) — merges an
  updater's set fields onto an existing config in `update()`.
- **`ProjectStructureVersionFactory`** + `ProjectStructureV0/V11/V12/V13Factory`
  (server `project`; → L2) — build POMs/structure from a `ProjectConfiguration`; this is
  where option values are *consumed* (e.g. V13's `configureServiceExecutionModule` adds the
  shade plugin).
- **`MultiModuleMavenProjectStructure`** (server `project/maven`; → L2) — base class of the
  multi-module structures. Holds a **fixed** module set per version: the entities module
  plus an immutable `otherModules` map (V13: `versioned-entities`, `service-execution`,
  `file-generation`). `configureMavenProjectModel` regenerates the root POM's `<modules>`
  and dependency management from exactly that set on every update — which is what drops any
  user-added `<module>` entry (§1) — and `collectUpdateProjectConfigurationOperations`
  deletes the files of any module present in the old structure but absent from the new one.
  (`project.json` already carries one per-project module-shaping list, `artifactGenerations`,
  whose module hook in the base class is deprecated — precedent that per-project module
  composition belongs in the configuration, but no supported mechanism today.)
- **External structure versions 1–10** — implemented in the origin (pre-open-sourcing)
  project as subclasses of these same classes, registered through the same
  `ServiceLoader<ProjectStructureVersionFactory>` lookup, and filling the 1–10 slots of
  the shared version-number space (see re-architecture §5). Whatever this plan adds to
  the factory/structure SPI is inherited by them.
- **`ProjectStructureExtension`** / `ProjectStructureExtensionProvider`
  (server `project/extension`) — extensions keyed by `(structureVersion, extensionVersion)`;
  contribute file operations via `collectUpdateProjectConfigurationOperations(oldConfig,
  newConfig, fileAccessContext, consumer)`. They carry **no typed options** today.
- **Precedent — `GenerationProperty`** (`legend-sdlc-model`): the artifact-generation
  subsystem already declares typed option metadata: `name`, `description`, `type`
  (`GenerationItemType`), `items`, `defaultValue`, `required`. We mirror this rather than
  invent a new shape.

## 4. Design

### 4.1 Option schema (declaration)

Introduce a typed option descriptor in `legend-sdlc-model` (L0), modeled on
`GenerationProperty`:

```java
public interface ConfigurationProperty
{
    String getName();
    String getDescription();
    ConfigurationPropertyType getType();   // BOOLEAN, STRING, INTEGER, ENUM, LIST<…>
    Object getDefaultValue();
    boolean getRequired();
    List<String> getAllowedValues();       // for ENUM; null/empty otherwise
}
```

(Reuse `GenerationItemType`/`GenerationPropertyItem` if the type lattice already fits;
otherwise a small parallel enum keeps the two subsystems decoupled. The `LIST` kind is not
speculative: the `features` and `additionalModules` options of §4.8 are list-valued from
day one.)

A **structure version** declares its options. Add to the L2 factory abstraction:

```java
// ProjectStructureVersionFactory (L2)
default List<ConfigurationProperty> getConfigurationProperties() { return Collections.emptyList(); }
```

A **structure extension** declares its options. Add to the extension SPI:

```java
// ProjectStructureExtension
default List<ConfigurationProperty> getConfigurationProperties() { return Collections.emptyList(); }
```

Both default to empty, so existing versions/extensions are unchanged until they opt in.

### 4.2 Option values (persistence)

Add two **namespaced, schema-validated** value bags to the L0 `ProjectConfiguration`,
stored in `project.json`:

```java
// ProjectConfiguration (L0)
default Map<String, Object> getStructureConfiguration() { return Collections.emptyMap(); }
default Map<String, Object> getExtensionConfiguration() { return Collections.emptyMap(); }
```

```jsonc
// project.json
{
  "projectStructureVersion": { "version": 13, "extensionVersion": 2 },
  "groupId": "...", "artifactId": "...",
  "structureConfiguration": {
    "features": ["versioned-entities", "service-execution"],  // file-generation deselected
    "produceShadedServiceJar": true,                          // scoped to the service-execution feature
    "additionalModules": ["my-tooling"]
  },
  "extensionConfiguration": { "gitlabRunnerTags": ["legend", "docker"] }
}
```

The generic core treats these as opaque typed values; **only the owning version/extension
interprets a key.** The active version is already recorded in `projectStructureVersion`, so
the bags need no further qualifier — the schema to interpret them against is implied by the
config itself. `SimpleProjectConfiguration` gains two `Map` fields instead of one new field
per option; the four-place edit (§1) collapses to "declare the property on the version."

### 4.3 Who declares what

| Option lives on | Declared by | Stored under | Read by |
|---|---|---|---|
| A structure version | the version's factory (L2) | `structureConfiguration` | the version factory at build/update |
| A deployment's extension | the `ProjectStructureExtension` (L2 interface; bound per deployment, see §5) | `extensionConfiguration` | the extension's `collectUpdate…Operations` |

### 4.4 Validation

On config create/update, validate each bag against the active version's / extension's
declared `ConfigurationProperty` list:

- unknown key → reject;
- missing required key with no default → reject;
- value not assignable to the declared type / not in `allowedValues` → reject (for
  list-valued options, element-wise).

The generic checks cover what the schema can express. The owning version/extension can
additionally contribute **option-specific validation** for constraints it cannot — value
grammar (module-name syntax), collisions with structural names, and cross-option
consistency (an option scoped to a deselected feature, §4.8) — run in the same validate
step.

Validation lives where the schema lives (L2 for structure options; with the extension for
extension options) and is invoked from the L3 updater's validate step — the same place that
already validates group/artifact ids, platform configs, and metamodel dependencies. No
generic code hard-codes any option name.

### 4.5 Discovery

Clients must learn the schema to render a form. Expose it through the **same discovery
mechanism the re-architecture builds for capabilities** rather than a one-off endpoint —
e.g. extend the structure/capabilities describe call so that, given a structure version
(and optionally an extension version), it returns the `ConfigurationProperty` list. For IDE
plugins (the re-architecture's `legend-sdlc-local` consumer) the same schema is available
**in-process** off the L2 factory (structure options, always) and off the deployment's
extension provider when one is supplied (extension options) — no server required, which is
exactly why the schema must live in L2, not in a server resource.

### 4.6 Consumption

Version factories and extensions read values from the bags instead of from bespoke
top-level getters. Example, the motivating case:

```java
// ProjectStructureV13Factory.configureServiceExecutionModule(...)
configuration.addPlugin(this.legendServiceExecutionGenerationPluginMavenHelper.getPlugin(this));
if (booleanOption(projectConfiguration, "produceShadedServiceJar", true))   // default true = today's behavior
{
    configuration.addPlugin(this.legendServiceExecutionGenerationPluginMavenHelper.getShadePlugin());
}
```

where `booleanOption(config, name, default)` reads `getStructureConfiguration()` with the
declared default — a small typed accessor on the structure base class.

### 4.7 Migrating the existing flags

`produceShadedServiceJar` and `runDependencyTests` become **declared options of their owning
structure version(s)**, stored in `structureConfiguration`. To stay compatible:

- The top-level getters `getProduceShadedServiceJar()` / `getRunDependencyTests()` remain as
  `@Deprecated` bridges that read the value out of `structureConfiguration` (so old Java
  callers still compile and behave).
- `SimpleProjectConfiguration` deserialization accepts the **old** top-level
  `project.json` keys and folds them into `structureConfiguration` on read (read-compat for
  existing repositories).
- On the next configuration write, the file is normalized to the namespaced form; a
  one-time, behavior-preserving migration.
- The REST `UpdateProjectConfigurationCommand` keeps accepting the old fields (deprecated)
  and also accepts a generic `structureConfiguration` map; both feed the updater.

### 4.8 Feature selection and module composition

The two use cases from §1 become the first **list-valued** structure-version options. The
guiding re-cast: what a project opts in or out of is not a module but a **feature** — a
named, user-selectable unit of optional function that a structure version offers. The
version, not the user, decides what a feature translates into. Today each optional V13
module is the footprint of one feature (selecting the *service-execution* feature adds the
`service-execution` module), but a feature may equally govern build plumbing *inside* a
module — the shaded service jar — or, in a later version, contribute several modules or
none. Modules are the implementation detail; features are the contract. The `entities`
module is the only *intrinsic* module — it is the project itself, not a feature — and
every other managed module is contributed by some feature.

| Option | Type | Default | Meaning |
|---|---|---|---|
| `features` | `LIST<STRING>` | the version's default-selected feature names | The project's complete selection among the version's declared optional features; `allowedValues` = the declared feature names |
| `additionalModules` | `LIST<STRING>` | `[]` | Module names the **user** maintains; included in the build, never generated or deleted by the structure |

A structure version declares its feature menu alongside its options — name, description,
default-selected flag, and the feature's own scoped configuration properties:

```java
// L2 — declared by multi-module structure version factories
public interface StructureFeature
{
    String getName();          // stable across versions, e.g. "service-execution"
    String getDescription();   // for discovery/Studio: "Build executable service jars"
    boolean isDefaultSelected();
    List<ConfigurationProperty> getConfigurationProperties();  // feature-scoped options
}
```

Feature-scoped options are what unify the motivating flag: `produceShadedServiceJar` stops
being a free-floating version option and becomes a property **of the service-execution
feature**. Its *value* still persists flat in `structureConfiguration` (no nested schema,
no data migration from §4.7's shape); what changes is ownership metadata — discovery
groups it under its feature, and one **generic** validation rule replaces any ad-hoc
cross-check: *an option scoped to a feature may only be set when that feature is
selected.*

The **effective module set** of a structure instance becomes
`entities + modules contributed by selected features + additionalModules`, and it flows
through every place the fixed set flows today: root-POM `<modules>` and dependency
management (`configureMavenProjectModel`), module POM generation and stale-module deletion
(`collectUpdateProjectConfigurationOperations`), and the artifact-id streams
(`getAllArtifactIds` / `getArtifactIdsForType`). Each version factory keeps declaring its
full feature/module map; the multi-module base applies the selection centrally when
computing the modules to build.

**`features` semantics.** Unset means the version's default selection — which reproduces
today's full layout, so unconfigured projects are untouched. Set, it is the *complete*
selection (declarative, not a delta against the default). The multi-module base applies a
selection only against a **declared** menu: a version that declares no features keeps its
fixed module set byte-for-byte — which is what keeps every version that predates the
mechanism, including the origin project's external versions 1–10 (§6), unaffected. Deselecting a feature removes
its modules via the existing stale-module deletion path, exactly as when a version upgrade
drops a module; reselecting regenerates them — safe, because managed modules are fully
generated. The first supporting version's menu falls out of V13's module map:
`versioned-entities`, `service-execution` (carrying `produceShadedServiceJar`), and
`file-generation` — one feature per non-entities module, names matching
`getDefaultModuleName(ArtifactType)`.

**Why features rather than module exclusion** (the shape this section previously took):

- A feature name is stable user *intent*; a module name is a per-version layout detail. A
  selection containing `service-execution` survives a future version renaming, splitting,
  or merging the modules that implement it — an `excludedModules` list keyed to concrete
  module names would break, or silently change meaning, across such an upgrade.
- A feature can govern more than module existence. The shaded jar is the proof: same
  subject (service execution), different mechanism (a Maven shade plugin inside the
  module). Module exclusion cannot express it; feature scoping gives the flag and the
  module a single home and a single consistency rule.
- Discovery becomes human-meaningful. Studio renders a checklist of described features
  with their nested options, not a list of internal Maven module names to exclude.

**`additionalModules` semantics.** The structure adds each named module to the root POM's
`<modules>` — so configuration updates stop dropping it — and does nothing else: it never
writes, regenerates, or deletes anything under the module's directory. The user owns the
module's `pom.xml` and contents. Removing a name from the list removes the `<module>` entry
but **orphans** the directory rather than deleting it — the deliberate inverse of managed
modules, whose files the structure owns and removes. Additional modules carry no
`ArtifactType`, so they never appear in `getArtifactIdsForType` (they are not Legend
artifacts); whether they participate in `getAllArtifactIds` / dependency management is an
open question (§9).

**Validation** (the owner-supplied checks of §4.4, beyond generic type/allowed-values):

- `features`: element-wise `allowedValues` check against the version's declared feature
  names; unknown names rejected.
- **Feature-scoped options** (the generic rule above): an option declared by a feature is
  only settable when that feature is selected — e.g. setting `produceShadedServiceJar`
  while deselecting `service-execution` is rejected, because the flag parameterizes a
  feature the project opted out of.
- `additionalModules`: each name matches the existing module-name grammar
  (`\w+(-\w+)*`, the `VALID_MODULE_NAME` rule); no collision with the entities module,
  any feature-contributed module name — selected *or not*, so a later reselection can
  never collide — or another entry.

Both options pass the §9 litmus test for per-project values — they belong in version
control with the project, are portable across deployments (structure-scoped, not
extension-scoped), and matter equally for local/IDE editing of a managed project: the L2
placement of schema + validation is what lets `legend-sdlc-local` enforce them with no
server.

A third companion plan,
[`project-layout-reconciliation.md`](project-layout-reconciliation.md), extends this
pattern from modules to individual files: under its build-and-reconcile model, files not
produced by the structure/extensions are deleted on update, so reconciling structure
versions declare a sibling **`additionalFiles`** option (a `LIST<STRING>` of preserved
path patterns) through this plan's mechanism. The interplay is deliberate: each
`additionalModules` entry implicitly preserves its module directory, and feature
deselection stops needing the hand-written stale-module deletion path — a deselected
feature's contributions are simply absent from the layout the selected features build.

## 5. Extensions belong to the deployment, not the backend

Founding design intent: **the project *structure* is universal and portable** — the same
structure is meant to work on GitHub, GitLab, or any other platform — **while project
structure *extensions* exist to absorb the particulars of a deployment's environment** and
are expected to differ from one environment to the next. Configuration options inherit this
split: structure-version options are portable; extension options are environment-specific.

The pivotal distinction for extension-scoped options:

- A **backend** (e.g. the GitLab backend) is **generic** — one implementation usable against
  *any* GitLab instance. It knows how to talk to GitLab; it knows nothing about a particular
  environment.
- **Project structure extensions** are tailored to a **particular deployment and its
  environment**. Examples: a `.gitlab-ci.yml` whose `tags` select runners that exist only on
  that GitLab instance; a `settings.xml` pointing at Maven repositories reachable only in
  that environment. Different instances → different extensions.

Concretely, an environment with two GitLab instances — one for production projects, one for
user-sandbox projects — is **two SDLC Server deployments**, both running the *same generic*
`gitlab` backend, each configured with its **own** project structure extensions. The sandbox
deployment must not push artifacts to the production Maven repository, so its pipeline
definition differs. The differing piece is the *extensions*, not the backend. Extensions are
a function of the deployment, **orthogonal to backend type** — orthogonal *by design*, but
correlated *in practice*: both follow from the environment, so a GitLab backend is normally
paired with GitLab-oriented extensions. The pairing is conventional, not enforced; an unusual
deployment could mix them.

So the layering for extensions:

| Concern | Where | Notes |
|---|---|---|
| Extension *contract* — `ProjectStructureExtension` / `…Provider` interfaces, `collectUpdate…Operations`, and the new `getConfigurationProperties()` | **L2** (project-structure) | Pure structure manipulation over `ProjectConfiguration` + file operations; no backend or server deps, so `legend-sdlc-local` and the TCK can exercise it directly. |
| *Applying* extensions during config update | **L3** (core) | Calls whatever provider it is handed; knows no concrete extension. |
| *Binding* the concrete provider/extensions for a deployment | **deployment config** | A *server* binds them via `ProjectStructureConfiguration` (as today); local/IDE tooling maintaining a *managed* project must be supplied the same deployment provider. **Not** the backend. |

This is essentially how it already works — extensions are *already* deployment-configured via
`ProjectStructureConfiguration`. The re-architecture forces only one change, and a necessary
one: because it moves the configuration *updater* down to L3, and the updater *applies*
extensions, the `ProjectStructureExtension` **interface must fall to L2** (it lives in the
server today only because the updater does). The concrete providers stay deployment
configuration. The re-architecture must take care **not to bundle a deployment's extensions
into the generic backend jar** as it extracts L5.

Consequences for option discovery:

- **Structure-version options** are declared by the version itself (L2) → available
  *everywhere*, including `legend-sdlc-local` / IDE plugins with no server. They are part of
  the portable structure, so they travel with the project regardless of environment.
- **Extension options** are declared by a deployment's extensions → available *wherever that
  deployment's extension provider is loaded*: on the server (from
  `ProjectStructureConfiguration`) **and** in local/IDE tooling maintaining a *managed*
  project, which must be handed the deployment's provider — a managed project belongs to its
  deployment whether reached through the server or edited locally. Only **embedded** projects
  escape this: `ProjectType.EMBEDDED` forbids extensions, so Form 2 of the IDE story (§4.4 of
  the re-architecture) involves no extension options at all.

This is why §2 sequences this work after the structure/core/SPI phases: the extension option
schema is declared on an interface whose home (L2) and application point (L3) the
re-architecture is establishing.

## 6. Compatibility

- **`project.json`**: read-compat for old files (old top-level keys folded into the bag,
  §4.7); new files use the namespaced form. No project becomes unreadable.
- **REST**: additive — old request fields still accepted (deprecated); new
  `structureConfiguration`/`extensionConfiguration` maps and a discovery response are added.
  No route or existing payload changes meaning.
- **Java API**: top-level getters deprecated, not removed, for one release cycle.
- **Defaults preserve behavior**: each migrated option's default reproduces today's
  hard-coded behavior (e.g. shade jar default `true`), so unconfigured projects build
  identically.
- **External structure versions (1–10)**: unaffected until they opt in. The new SPI
  methods default to empty — no options, no feature menu — and the multi-module base
  applies a `features` selection only against a declared menu (§4.8), so an external
  version that declares nothing keeps its exact behavior after upgrading its
  `legend-sdlc` dependency. Setting `structureConfiguration` on a project whose version
  declares nothing is rejected as unknown keys — the correct signal. Opting in is the
  same act as for in-repo versions, because the mechanism is inherited from the base
  classes rather than wired per-version: override `getConfigurationProperties()` /
  declare a `StructureFeature` menu. Cross-boundary upgrades (say, external V5 → V13)
  work by construction: option carry-over is validated against the *target* version's
  declared schema, wherever that version is implemented.
- **External extensions**: held to a stricter standard than versions, because defining
  extensions is what external projects are *expected* to do (re-architecture §5 — the
  origin project ships its own). This plan's entire footprint on the extension SPI is
  one `default` method, `getConfigurationProperties()`, so every existing extension
  compiles and runs unchanged, declaring no options until its author chooses to.

## 7. Phased Plan

Each phase keeps the build green; phases 1–2 are pure platform with no client surface.

**Phase 0 — prerequisite.** Re-architecture Phases 1–4 complete (config model in L0,
structure in L2, core/updater in L3, backend SPI + capability/discovery in L4), honoring the
seams in §8.

**Phase 1 — schema + persistence (no behavior change).** Add `ConfigurationProperty` (L0),
the two namespaced bags on `ProjectConfiguration`/`SimpleProjectConfiguration`, the
`getConfigurationProperties()` SPI methods (default empty) on the version factory and the
extension. Wire read-compat for the old top-level keys. Nothing declares options yet; tests
prove `project.json` round-trips unchanged.

**Phase 2 — migrate the existing flags.** Declare `produceShadedServiceJar` (finishing its
wiring — gate the shade plugin on it) and `runDependencyTests` as options of their owning
version(s). Add validation. Deprecate the top-level getters/fields as bridges. Existing
tests + new round-trip/migration tests are the net. (`produceShadedServiceJar` starts here
as a plain version option and is re-homed under the service-execution feature in Phase 4 —
ownership metadata only; its persisted key does not change.)

**Phase 3 — discovery + client surface.** Extend the discovery mechanism to return option
schemas; teach the REST command to accept the generic maps. Studio renders options from the
schema. `legend-sdlc-local` exposes the schema in-process for IDE plugins.

**Phase 4 — feature selection + module composition (§4.8).** Declare the feature menu
(`StructureFeature`) and the `features` / `additionalModules` options on the multi-module
structure version(s), implemented once in the multi-module base: effective-module-set
computation, root-POM preservation of user modules, selection-aware update/deletion,
re-homing `produceShadedServiceJar` under the service-execution feature, and the
owner-supplied validation (name grammar, collisions, allowed values, feature-scoped
option rule). End-to-end tests: a configuration update preserves a user module's
`<module>` entry and never touches its files; removing it from the list orphans (does not
delete) the directory; deselecting then reselecting `service-execution` round-trips;
`produceShadedServiceJar` with `service-execution` deselected is rejected. (Needs Phase
1's persistence and Phase 3's REST map to be settable; sequenced after discovery so Studio
can render the feature checklist rather than users hand-editing JSON.)

**Phase 5 — extension options.** With the extension↔backend boundary settled (§5), let an
extension declare and consume options; prove with one concrete extension option end-to-end
(e.g. a sample GitLab runner-tags option behind the GitLab backend).

## 8. Reserved seams for the re-architecture

Three cheap accommodations the re-architecture should make *now* so this work is additive,
not a second migration of `project.json`:

| Seam | Where | Why |
|---|---|---|
| **S1 — introduce the namespaced bag when the config model/updater are touched** | L0 `ProjectConfiguration`, L3 updater | The refactor already rewrites these; adding `getStructureConfiguration()`/`getExtensionConfiguration()` (even if only the two existing booleans live there, behind their deprecated getters) avoids re-migrating `project.json` later. Do **not** entrench new flat booleans. |
| **S2 — reserve the option-schema method shape on the version/extension SPI** | L2 `ProjectStructureVersionFactory`, `ProjectStructureExtension` | Land `getConfigurationProperties()` defaulting empty so versions/extensions can opt in without an SPI break later. |
| **S3 — make capability/discovery extensible to option schemas** | L4 discovery endpoint (Phase 4 of re-architecture) | Design the "describe what this structure/extension supports" call so it can also carry option schemas, instead of bolting on a separate endpoint afterward. |

## 9. Risks and Open Questions

| Risk / question | Notes / mitigation |
|---|---|
| **Typed values in an opaque `Map<String,Object>`** lose compile-time safety for option authors. | Acceptable: the `ConfigurationProperty` schema + validation enforce types at the edges; typed accessors (`booleanOption(...)`) keep call sites clean. Mirrors how generation properties already work. |
| **Option lifetime across version upgrades.** When a project moves structure version, options that no longer apply must be dropped; carried-over ones validated against the new schema. | Slot into the existing config-update re-serialization (the same flow that moves/re-serializes entities on version change). Define policy: drop unknown options on upgrade, warn on dropped non-default values. Feature selection is *designed* to carry: feature names are stable intent, surviving upgrades even where the modules implementing them change; where a target version lacks a declared feature, validation flags it at upgrade time. |
| **The update path must never delete a user-maintained module** (§4.8). The existing stale-module deletion assumes every module is structure-owned; `additionalModules` breaks that assumption. | Make "not structure-owned ⇒ not deletable" a structural rule in the multi-module base, not a filter bolted onto the deletion path; test the orphan-on-removal behavior explicitly. Conversely, *deselecting* a feature deletes its modules' directories per existing semantics — any user files placed inside a managed module's directory are lost; document this and warn on deselection. |
| **Deselecting a feature changes the project's artifact set.** `getAllArtifactIds()` / `getArtifactIdsForType()` feed artifact publication and downstream dependency resolution; deselecting e.g. *versioned-entities* may break downstream projects that consume its artifacts. | Per-version judgment on which features are offered and which are default-selected (a version may keep a feature mandatory for ecosystem reasons, not just entities); surface a warning when deselecting a feature whose artifacts are consumable downstream. |
| **Feature granularity and ownership.** Is one-feature-per-module the right initial grain, and can *extensions* declare features of their own (deployment-scoped optional function)? | Start with the V13-derived menu — one feature per optional module — which costs nothing to coarsen or refine later, since the model never promises a feature↔module bijection. Extension-declared features are deferred: extension options (§5, Phase 5) cover the near-term need; revisit if a deployment needs user-selectable optional modules of its own. |
| **Missing `pom.xml` for an additional module** breaks the build until the user supplies it (the structure deliberately never writes into the module). | Open question: scaffold a minimal module POM once, on first addition, if and only if the directory is absent — never touch it again — vs. validate-and-warn only. Leaning: scaffold-once; decide in Phase 4. Related open question: whether additional modules join `getAllArtifactIds()`/dependency management or stay entirely outside the artifact machinery. |
| **Where a runner-style option actually belongs.** "GitLab runner tags" could be a per-project value, an extension's fixed output, or backend connection config. | Three distinct layers: the per-project *selection* among deployment-offered choices → `project.json` (this plan); the *menu of choices and the `.gitlab-ci.yml` template* → the deployment's extension (server config, §5); the GitLab *connection* → the backend's `backend:` config. Test for the per-project layer: does this value belong in version control with the project? |
| **Two discovery endpoints** if §8/S3 is missed. | Land S3 during the re-architecture's Phase 4; otherwise this plan adds a parallel endpoint and the client integrates twice. |
| **Studio form rendering** is out of this repo. | Schema is designed to be client-agnostic (name/type/default/required/allowed-values is enough to render a generic form); coordinate the wire shape with Studio before Phase 3. |