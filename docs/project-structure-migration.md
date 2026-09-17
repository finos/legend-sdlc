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
| `ProjectStructure.newUpdateBuilder(…)` / `ProjectStructure.UpdateBuilder` | `ProjectStructureUpdater.newUpdateBuilder(…)` / `ProjectStructureUpdater.UpdateBuilder` in `org.finos.legend.sdlc.core.project`, in **`legend-sdlc-core`** (Phase 3; it sat briefly in `org.finos.legend.sdlc.server.project` between the Phase 2 and Phase 3 commits, but no release shipped it there — no bridge) |
| `org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater` in `legend-sdlc-server` | `org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater` in **`legend-sdlc-core`**; a deprecated bridge subclass remains at the old FQN (note: the fluent `with*` methods return the relocated type) |
| Structure code throws `LegendSDLCServerException` (JAX-RS `Status`) | throws `org.finos.legend.sdlc.error.LegendSDLCException` (int status code, same values); `LegendSDLCServerException` is its deprecated subclass |
| `org.finos.legend.sdlc.server.project.ProjectFileAccessProvider` (and `ProjectFileOperation`, `ProjectFiles`, `ProjectPaths`, `AbstractFileAccessContext`, `CachingFileAccessContext`, `EmptyFileAccessContext`) in `legend-sdlc-project-files` | `org.finos.legend.sdlc.project.files.*` (same module; **no deprecated bridges** — update imports and recompile) |
| `org.finos.legend.sdlc.server.domain.api.project.source.*` (`SourceSpecification` and subclasses, `SourceSpecificationVisitor`/`Consumer`) in `legend-sdlc-project-files` | `org.finos.legend.sdlc.project.source.*` (same module; **no deprecated bridges** — update imports and recompile). Decided in the Phase 4 design review: the taxonomy's home is L1, final. |
| `org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification` / `WorkspaceSource` / `ProjectWorkspaceSource` / `PatchWorkspaceSource` / `WorkspaceSourceVisitor` / `WorkspaceSourceConsumer` in `legend-sdlc-project-files` | `org.finos.legend.sdlc.project.workspace.*` (same module; **no deprecated bridges**). `WorkspaceApi` and the other domain API interfaces are unaffected by this row (see the next row). |
| The domain API interfaces `org.finos.legend.sdlc.server.domain.api.<concern>.*` (`EntityApi`, `ProjectApi`, `WorkspaceApi`, `ReviewApi`, …, including the access contexts and `ProjectRevision`), and `org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport`, in `legend-sdlc-server` | `org.finos.legend.sdlc.backend.api.<concern>.*` in **`legend-sdlc-backend-api`** (note `conflictResolution` → `conflictresolution`); deprecated bridge interfaces remain at the old FQNs in `legend-sdlc-server`, so implementations and injection points keep compiling. **Not bridged**: `NewVersionType` (an enum cannot be bridged — update imports). **Changed on the relocated types** (old shapes remain on the bridges): `ProjectApi` no longer declares the GitLab-specific `configureProjectInWorkspace`; `ConflictResolutionApi.acceptConflictResolution` takes the message/entity-changes/revision-id directly instead of a `PerformChangesCommand`; `VersionApi`/`BuildApi` default methods throw `LegendSDLCException` (same 400 status) instead of `LegendSDLCServerException`. |
| `org.finos.legend.sdlc.project.files.InMemoryProjectFileAccessProvider` / `SimpleInMemoryVCS` in the `legend-sdlc-project-files` **test-jar** | `org.finos.legend.sdlc.backend.inmemory.*` in the main jar of **`legend-sdlc-backend-inmemory`** (no deprecated bridges — test utilities; update the dependency from the test-jar to the new module and fix imports). They are now regular published classes: the storage provider behind the in-memory backend. |
| **`legend-sdlc-server-fs`** (the standalone file-system SDLC server: `LegendSDLCServerFS`, `FSModule`, the `FileSystem*Api` classes, `FSException`) | **`legend-sdlc-backend-fs`**, package `org.finos.legend.sdlc.backend.fs` — a backend for the standard server, not a server (a relocation POM points the old Maven coordinates at the new ones; **no deprecated bridges** — the old classes were a self-contained runnable, not an API surface, and most have no equivalent in the refit: the stub api classes are replaced by the capability model and the generic L4 defaults). See "If you deploy the file-system SDLC server" below. |
| The GitLab implementation `org.finos.legend.sdlc.server.gitlab.**` in `legend-sdlc-server` (`GitLabConfiguration`, `GitLabAppInfo`, `GitLabServerInfo`, `GitLabProjectId`, the `GitLab*Api` classes, `GitLabOAuthAuthenticator`, the SAML authenticators, the auth exceptions, `GitLabApiTools`, `PagerTools`, `GitLabBackend`/`GitLabBackendFactory`/`GitLabBackendConfiguration`), plus `org.finos.legend.sdlc.server.tools.AuthenticationTools` | `org.finos.legend.sdlc.backend.gitlab.**` in **`legend-sdlc-backend-gitlab`** (**no deprecated bridges** — the server cannot alias classes that now live below it, and these were server-internal implementation, not published API; depend on `legend-sdlc-backend-gitlab` and update imports). `org.finos.legend.sdlc.server.gitlab.finos.FinosGitlabProjectStructureExtensionProvider` stays in `legend-sdlc-server` at its old FQN — it is deployment configuration, not backend code. GitLab4J leaves the server's dependency tree. |
| `GitLabAuthorizer.authorize(Session, GitLabAppInfo)` (implementations configured in the `gitlabAuthorizers` list) | `org.finos.legend.sdlc.backend.gitlab.auth.GitLabAuthorizer.authorize(BackendSessionContext, GitLabAppInfo)` — a **breaking signature change**. Configured class names in YAML keep resolving; re-target the implementation: identity via `BackendSessionContext.getUserId()`, the Kerberos `Subject` via `getService(javax.security.auth.Subject.class)`, OIDC/personal-access-token material via `getService(OidcAuthMaterial.class)` / `getService(PersonalAccessTokenAuthMaterial.class)`, per-user persistence via `getStateStore()`. |
| The GitLab-specific auth machinery in the server: `GitLabBundle`, `GitLabServerHealthCheck`, the GitLab session classes (`GitLabSession` and implementations, `GitLabSessionBuilder`, `GitLabWebFilter`), the servlet-bound `GitLabUserContext`, the GitLab `/auth` resources | Gone, no bridges. The server's session filter is backend-independent (registered as **"LegendSDLCSession"**; a `filterPriorities` entry under the old name **"GitLab"** is honored as an alias), sessions are generic state-carrying sessions, one generic `/auth` surface serves every backend (same routes and wire behavior), and the GitLab token life cycle lives in the backend over the SPI's session state store. Consequences: the `gitLabServer` health-check entry disappears from `/healthcheck`; session cookies from earlier releases decode without token state, so users re-authorize once after upgrade (silently for OIDC-, personal-access-token-, and Kerberos-authenticated users); the `"gitlab retryable exception"` prometheus counter is no longer emitted. |
| `org.finos.legend.sdlc.server.tools.CallUntil` / `ThrowingRunnable` / `ThrowingSupplier` in `legend-sdlc-server` | `org.finos.legend.sdlc.backend.api.tools.*` in **`legend-sdlc-backend-api`** (joining `BackgroundTaskProcessor`); deprecated bridges remain at the old FQNs in `legend-sdlc-server`. |
| `legend-sdlc-server` bundles GitLab on its compile classpath | The standard server distribution ships all three backends (`legend-sdlc-backend-gitlab`, `-fs`, `-inmemory`) as **runtime** dependencies; a deployment selects one by the `backend:` configuration (a legacy top-level `gitLab:` section still selects the GitLab backend, including the deprecated `uat`/`prod` mode forms). Assemblies that construct their own classpath must add the backend jar(s) they deploy. `DepotServerException.getDetail()` no longer reads a GitLab auth exception cause's detail; the deprecated GitLab-specific `configureProjectInWorkspace` is gone from the `ProjectApi` bridge (its `GitLabProjectId` parameter now lives below the server). |

## If you deploy the file-system SDLC server

The standalone file-system server — the `legend-sdlc-server-fs` shaded jar, its
`org.finos.legend.sdlc.server.startup.LegendSDLCServerFS` main class, and the
`finos/legend-sdlc-server-fs` Docker image — no longer exists. A file-system deployment
now runs the **standard** server:

1. Run `org.finos.legend.sdlc.server.LegendSDLCServer` (the standard `legend-sdlc-server`
   distribution) with `legend-sdlc-backend-fs` and its dependencies on the classpath.
2. Replace the old top-level `fileSystem:` configuration section with the polymorphic
   backend selection:

   ```yaml
   backend:
     type: fileSystem
     rootDirectory: /path/under/which/project/repositories/live
   ```

   Everything else in the configuration (server connectors, filters, `projectStructure:`,
   …) is standard-server configuration and keeps its shape.
3. An existing root directory keeps working: projects are discovered by scanning it, and
   workspace branches created by the old server (`workspace/local_user/…`) remain
   addressable — sessions without an authenticated user id map to the old server's fixed
   `local_user`.
4. The standard server creates its per-request session from the pac4j authentication
   profiles (the old file-system server needed no session at all), so the deployment's
   `pac4j:` section must configure a client that yields a profile — e.g.
   `LocalKerberosClient` for a single-user local setup. Requests without an
   authentication profile get no session, and session-bound routes fail.

Behavior differences to be aware of: routes for features the file-system backend does not
declare (reviews other than listing, versions, workflows, patches, builds, backup,
conflict resolution, issues) now return **501 with a structured body** naming the missing
capability, instead of the old stubs' empty responses and 500s; review **listing** reports
an empty list (a compatibility affordance for Legend Studio, retained temporarily until
Studio consumes `GET /configuration/capabilities`). The capability set is discoverable at
`GET /configuration/capabilities`.

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
