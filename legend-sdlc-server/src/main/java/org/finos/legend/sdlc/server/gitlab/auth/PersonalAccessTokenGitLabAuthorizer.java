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

package org.finos.legend.sdlc.server.gitlab.auth;

import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.spi.PersonalAccessTokenAuthMaterial;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.gitlab4j.api.Constants.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizes a user who authenticated to the host with a personal access token for this GitLab instance: if the
 * host publishes {@link PersonalAccessTokenAuthMaterial} whose host matches, the token is used as a private
 * GitLab token. This is the former {@code GitLabPersonalAccessTokenSession} constructor harvest, relocated to
 * where the host knowledge lives.
 */
public class PersonalAccessTokenGitLabAuthorizer implements GitLabAuthorizer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PersonalAccessTokenGitLabAuthorizer.class);

    @Override
    public GitLabTokenResponse authorize(BackendSessionContext sessionContext, GitLabAppInfo appInfo)
    {
        PersonalAccessTokenAuthMaterial material = sessionContext.getService(PersonalAccessTokenAuthMaterial.class);
        if (material == null)
        {
            return null;
        }
        if ((material.getHost() == null) || !appInfo.getServerInfo().getHost().equals(material.getHost()))
        {
            return null;
        }
        LOGGER.debug("Using private access token from GitLab Personal Access Token (PAT) authentication");
        return new GitLabTokenResponse(GitLabToken.newGitLabToken(TokenType.PRIVATE, material.getToken()), null, null, null);
    }
}
