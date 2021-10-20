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

package org.finos.legend.sdlc.server.gitlab.auth;

import org.apache.http.cookie.Cookie;
import org.finos.legend.sdlc.server.auth.KerberosSession;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.gitlab4j.api.Constants;

public class KerberosGitLabAuthorizer implements GitLabAuthorizer
{
    @Override
    public GitLabToken authorize(Session session, GitLabModeInfo modeInfo)
    {
        if (session instanceof KerberosSession)
        {
            KerberosSession kerberosSession = (KerberosSession) session;
            KerberosGitLabSAMLAuthenticator kerberosGitLabSAMLAuthenticator = new KerberosGitLabSAMLAuthenticator(modeInfo, kerberosSession.getSubject());
            Cookie sessionCookie = kerberosGitLabSAMLAuthenticator.authenticateAndGetSessionCookie();
            String oAuthToken = GitLabOAuthAuthenticator.newAuthenticator(modeInfo).getOAuthTokenFromSessionCookie(sessionCookie);
            return GitLabToken.newGitLabToken(Constants.TokenType.OAUTH2_ACCESS, oAuthToken);
        }
        else
        {
            return null;
        }
    }
}
