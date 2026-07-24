// Copyright 2026 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.backend.api.spi;

/**
 * Optional capabilities a backend may declare. Capabilities are deployment-static: they describe what the backend
 * supports, not what the calling user is permitted to do (per-user permission failures remain authorization
 * errors from the individual APIs).
 * <p>
 * Everything not listed here is core and required of every backend: projects, workspaces (at least one of
 * {@link #USER_WORKSPACES}/{@link #GROUP_WORKSPACES} must be declared), revisions, entities, project
 * configuration, dependencies, and comparison.
 * <p>
 * This enumeration may grow. It is serialized by name on the capabilities discovery endpoint, so clients must
 * tolerate unknown values.
 */
public enum BackendCapability
{
    /**
     * Reviews of proposed changes (in Git terms, merge/pull requests): {@code ReviewApi}.
     */
    REVIEWS,

    /**
     * Workflows and workflow jobs (in Git-hosting terms, CI pipelines): {@code WorkflowApi} and
     * {@code WorkflowJobApi}.
     */
    WORKFLOWS,

    /**
     * Released versions (in Git terms, release tags): {@code VersionApi}, and the version-scoped access contexts
     * of the core APIs.
     */
    VERSIONS,

    /**
     * Patch lines of development for released versions: {@code PatchApi}, and the patch-scoped sources of the
     * other APIs.
     */
    PATCHES,

    /**
     * Builds (a legacy surface superseded by {@link #WORKFLOWS}): {@code BuildApi}.
     */
    BUILDS,

    /**
     * Backup workspaces: {@code BackupApi}, and the backup workspace access type.
     */
    BACKUP,

    /**
     * Issue tracking: {@code IssueApi}.
     */
    ISSUES,

    /**
     * Conflict-resolution workspaces: {@code ConflictResolutionApi}, and the conflict-resolution workspace
     * access type. The mechanics are generic; declaring this capability means the backend's storage supports
     * the conflict-resolution workspace access type.
     */
    CONFLICT_RESOLUTION,

    /**
     * Per-user workspaces.
     */
    USER_WORKSPACES,

    /**
     * Shared (group) workspaces.
     */
    GROUP_WORKSPACES
}
