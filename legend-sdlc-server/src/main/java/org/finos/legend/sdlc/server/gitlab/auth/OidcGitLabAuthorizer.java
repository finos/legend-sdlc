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
import org.finos.legend.sdlc.backend.api.spi.OidcAuthMaterial;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.gitlab4j.api.Constants.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Authorizes a user whose OIDC authentication to the host was issued by the GitLab server itself: if the host
 * publishes {@link OidcAuthMaterial} whose issuer is this GitLab instance and whose access token carries the
 * {@code api} scope, that token is the GitLab token. This is the former
 * {@code GitLabOidcSession} constructor harvest, relocated to where the issuer knowledge lives.
 */
public class OidcGitLabAuthorizer implements GitLabAuthorizer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcGitLabAuthorizer.class);

    @Override
    public GitLabTokenResponse authorize(BackendSessionContext sessionContext, GitLabAppInfo appInfo)
    {
        OidcAuthMaterial material = sessionContext.getService(OidcAuthMaterial.class);
        if (material == null)
        {
            return null;
        }
        if (!material.getScopes().contains("api"))
        {
            return null;
        }
        String issuer = material.getIssuer();
        if ((issuer == null) || !issuer.equals(appInfo.getServerInfo().getGitLabURLString()))
        {
            return null;
        }
        LOGGER.debug("Using access token from OpenID Connect (OIDC) profile issued by {}", issuer);
        LocalDateTime tokenExpiry = (material.getExpiration() == null) ? null : LocalDateTime.ofInstant(material.getExpiration(), ZoneId.systemDefault());
        return new GitLabTokenResponse(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, material.getAccessToken()), material.getRefreshToken(), null, tokenExpiry);
    }
}
