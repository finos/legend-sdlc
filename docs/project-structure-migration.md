# Migrating to `legend-sdlc-project-structure`

This is the migration recipe promised by [`re-architecture.md`](re-architecture.md) §5 for
the Phase 2 extraction of project structure out of `legend-sdlc-server`. It is aimed at
external projects that implement **structure versions** (the origin project's versions
1–10) or **project structure extensions**, or that consume project-structure classes
directly.

## What changed

| Before | After |
|---|---|
| `org.finos.legend.sdlc.server.project.ProjectStructure` (and `EntitySourceDirectory`, `ProjectStructureFactory`, `ProjectStructureVersionFactory`, `ProjectStructureV*Factory`, `ProjectStructurePlatformExtensions`, `Simple*`) in `legend-sdlc-server` | `org.finos.legend.sdlc.project.structure.*` in **`legend-sdlc-project-structure`** |
| `org.finos.legend.sdlc.server.project.maven.*` | `org.finos.legend.sdlc.project.structure.maven.*` |
| `org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension` / `…Provider` / `UpdateProjectStructureExtension` (interfaces) | `org.finos.legend.sdlc.project.structure.extension.*`; deprecated bridge interfaces remain at the old FQNs |
| `EntitySourceDirectory` nested in `ProjectStructure` | top-level `org.finos.legend.sdlc.project.structure.EntitySourceDirectory` |
| `ProjectStructure.newUpdateBuilder(…)` / `ProjectStructure.UpdateBuilder` | `ProjectStructureUpdater.newUpdateBuilder(…)` / `ProjectStructureUpdater.UpdateBuilder` (still `org.finos.legend.sdlc.server.project`, in `legend-sdlc-server`; moves to the core module in Phase 3) |
| Structure code throws `LegendSDLCServerException` (JAX-RS `Status`) | throws `org.finos.legend.sdlc.error.LegendSDLCException` (int status code, same values); `LegendSDLCServerException` is its deprecated subclass |
| `org.finos.legend.sdlc.server.project.ProjectFileAccessProvider` (and `ProjectFileOperation`, `ProjectFiles`, `ProjectPaths`, `AbstractFileAccessContext`, `CachingFileAccessContext`, `EmptyFileAccessContext`) in `legend-sdlc-project-files` | `org.finos.legend.sdlc.project.files.*` (same module; **no deprecated bridges** — update imports and recompile). The `SourceSpecification`/`WorkspaceSpecification` taxonomy keeps its `server.domain.api.*` packages until the backend-SPI phase decides its final home. |

## If you implement project structure extensions (the expected case)

**No source change is required.** The old interfaces still exist as deprecated bridges
that extend the relocated ones, so existing implementations remain valid and are usable
wherever the new interfaces are expected. When convenient:

1. Depend on `legend-sdlc-project-structure` instead of (or in addition to)
   `legend-sdlc-server`.
2. Change `implements org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension`
   (and `…Provider`) to the `org.finos.legend.sdlc.project.structure.extension` equivalents.

**Exception:** implementations of `UpdateProjectStructureExtension` are discovered via
`ServiceLoader` keyed on the interface FQN. Re-key your
`META-INF/services/org.finos.legend.sdlc.server.project.extension.UpdateProjectStructureExtension`
file to the relocated name, or the extension will not be loaded.

## If you implement structure versions (origin project, versions 1–10)

The port is mechanical:

1. **Dependency**: depend on `legend-sdlc-project-structure` instead of
   `legend-sdlc-server` for the structure-authoring classes.
2. **Imports**: apply the package renames in the table above (in particular
   `server.project.maven.*` → `structure.maven.*`, and `EntitySourceDirectory` is now
   top-level).
3. **Override visibility**: if a structure class overrides
   `collectUpdateProjectConfigurationOperations(ProjectStructure, FileAccessContext, Consumer)`
   with `protected` visibility, change the override to `public` (the base method is now
   public so that the updater can dispatch to it from outside the package).
4. **Service registration**: re-key
   `META-INF/services/org.finos.legend.sdlc.server.project.ProjectStructureVersionFactory`
   to `META-INF/services/org.finos.legend.sdlc.project.structure.ProjectStructureVersionFactory`
   (contents: your factory class names, at their new packages if you moved them).
   *Release-timing slack*: the bridge release also loads factories registered under the
   **old** key (instantiated against the relocated base class), so a jar that has
   recompiled against the new packages but not yet re-keyed keeps loading. This
   dual-keyed lookup is removed together with the deprecation bridges.
5. **Recompile.** That is the whole port. The new
   `ProjectStructureVersionFactory.getConfigurationProperties()` defaults to empty;
   legacy versions need not implement it.
6. **Verify** with your own version tests.

## If you catch exceptions from structure code

Relocated structure code throws `org.finos.legend.sdlc.error.LegendSDLCException`
(carrying the same int status codes: 400 for validation failures, 500 otherwise).
`LegendSDLCServerException` is now its deprecated subclass, so `catch (LegendSDLCException …)`
catches both; `catch (LegendSDLCServerException …)` no longer catches what structure code
throws. Server deployments are unaffected on the wire: the server maps both types to
identical HTTP responses.
