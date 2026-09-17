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

import org.apache.http.cookie.Cookie;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.gitlab.GitLabAppInfo;

import javax.security.auth.Subject;

/**
 * Authorizes a Kerberos-authenticated user by running the SAML/SPNEGO dance against the GitLab server with the
 * user's {@link Subject} (published by the host through the session context) and exchanging the resulting
 * session cookie for an OAuth token.
 */
public class KerberosGitLabAuthorizer implements GitLabAuthorizer
{
    @Override
    public GitLabTokenResponse authorize(BackendSessionContext sessionContext, GitLabAppInfo appInfo)
    {
        Subject subject = sessionContext.getService(Subject.class);
        if (subject == null)
        {
            return null;
        }
        KerberosGitLabSAMLAuthenticator kerberosGitLabSAMLAuthenticator = new KerberosGitLabSAMLAuthenticator(appInfo, subject);
        Cookie sessionCookie = kerberosGitLabSAMLAuthenticator.authenticateAndGetSessionCookie();
        return GitLabOAuthAuthenticator.newAuthenticator(appInfo).getOAuthTokenResponseFromSessionCookie(sessionCookie);
    }
}
