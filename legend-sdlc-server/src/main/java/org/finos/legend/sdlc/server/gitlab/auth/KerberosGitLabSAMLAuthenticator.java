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

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.AuthenticationTools;

import javax.security.auth.Subject;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;

public class KerberosGitLabSAMLAuthenticator extends GitLabSAMLAuthenticator
{
    private static final String SAML_AUTH_PATH = "/users/auth/saml";
    private final Subject subject;

    KerberosGitLabSAMLAuthenticator(GitLabModeInfo modeInfo, Subject subject)
    {
        super(modeInfo);
        this.subject = subject;
    }

    public Cookie authenticateAndGetSessionCookie()
    {
        try
        {
            return Subject.doAs(this.subject, (PrivilegedAction<Cookie>) super::authenticateAndGetSessionCookie);
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserId(), getGitLabMode(), "Error getting GitLab session token", e);
        }
    }

    @Override
    protected URI buildAuthURI()
    {
        URIBuilder builder = this.getModeInfo().getServerInfo().newURIBuilder().setPath(SAML_AUTH_PATH);
        try
        {
            return builder.build();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Error building SAML authentication URI: " + builder.toString(), e);
        }
    }

    @Override
    protected String getUserId()
    {
        return AuthenticationTools.getKerberosIdFromSubject(this.subject);
    }
}
