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

import org.finos.legend.sdlc.backend.api.backup.BackupApi;
import org.finos.legend.sdlc.backend.api.build.BuildApi;
import org.finos.legend.sdlc.backend.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi;
import org.finos.legend.sdlc.backend.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.issue.IssueApi;
import org.finos.legend.sdlc.backend.api.patch.PatchApi;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.version.VersionApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.error.LegendSDLCException;

import java.util.Collections;
import java.util.Set;

/**
 * A backend's per-user view: the domain APIs, bound to the user of the {@link BackendSessionContext} the
 * session was created with. The domain APIs hang off the session — not the {@link Backend} — because most
 * backends' operations are meaningless without a user.
 * <p>
 * Contract:
 * <ul>
 * <li>Sessions are cheap: the host may create one per request and never caches them across requests.</li>
 * <li>No object-identity guarantees: a backend whose APIs are user-independent may return one shared,
 * stateless instance from every {@code newSession} call.</li>
 * <li>Accessors for the APIs of an undeclared optional {@link BackendCapability} throw
 * {@link UnsupportedCapabilityException}; accessors for core APIs never do. Which accessor is gated by which
 * capability is documented on {@link BackendCapability}.</li>
 * <li>A {@code WorkspaceSpecification} with a null user id is resolved against this session's user.</li>
 * </ul>
 */
public interface BackendSession
{
    /**
     * The user this session is bound to (from the creating {@link BackendSessionContext}); null if none.
     *
     * @return user id or null
     */
    String getUserId();

    /**
     * Whether this session is authorized against the backend — its credentials are present or obtainable
     * without user interaction. Trivially true for backends without interactive authorization.
     *
     * @return whether the session is authorized
     */
    default boolean isAuthorized()
    {
        return true;
    }

    /**
     * Ensure this session is authorized, acquiring credentials where possible. If user interaction is required,
     * throws {@link AuthorizationRequiredException} carrying the authorization URI. A no-op for backends without
     * interactive authorization.
     */
    default void authorize()
    {
    }

    /**
     * Complete an interactive authorization flow with the parameters the authorization server sent to the
     * host's callback endpoint.
     *
     * @param code  authorization code
     * @param state flow state (may be null)
     */
    default void handleAuthorizationCallback(String code, String state)
    {
        throw new LegendSDLCException("Authorization callbacks are not supported by this backend", 400);
    }

    /**
     * Terms-of-service the backend requires the user to accept before it can be used, as URLs where acceptance
     * can be given; empty if none are pending.
     *
     * @return pending terms-of-service URLs
     */
    default Set<String> getUnacceptedTermsOfService()
    {
        return Collections.emptySet();
    }

    ProjectApi getProjectApi();

    ProjectConfigurationApi getProjectConfigurationApi();

    WorkspaceApi getWorkspaceApi();

    RevisionApi getRevisionApi();

    EntityApi getEntityApi();

    ComparisonApi getComparisonApi();

    DependenciesApi getDependenciesApi();

    UserApi getUserApi();

    ReviewApi getReviewApi();

    VersionApi getVersionApi();

    PatchApi getPatchApi();

    WorkflowApi getWorkflowApi();

    WorkflowJobApi getWorkflowJobApi();

    BuildApi getBuildApi();

    BackupApi getBackupApi();

    ConflictResolutionApi getConflictResolutionApi();

    IssueApi getIssueApi();
}
