// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.backend.gitlab.auth;

import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.gitlab.GitLabAppInfo;

/**
 * A way of acquiring a GitLab token for a user without interactive authorization. Implementations read what they
 * need — identity, or auth material such as a Kerberos {@link javax.security.auth.Subject} or OIDC tokens — from
 * the {@link BackendSessionContext}. Additional authorizers can be configured in the GitLab configuration's
 * {@code gitlabAuthorizers} list (Jackson-polymorphic by class name). Returning null means "this authorizer
 * cannot authorize this session"; the next one in the chain is tried.
 */
public interface GitLabAuthorizer
{
    GitLabTokenResponse authorize(BackendSessionContext sessionContext, GitLabAppInfo appInfo);
}
