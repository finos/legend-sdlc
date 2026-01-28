// Copyright 2023 Goldman Sachs
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

import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.oidc.profile.OidcProfile;

import javax.security.auth.Subject;
import java.util.Collections;

/**
 * Unit tests for GitLabSessionBuilder.isSupportedProfile() method.
 */
public class TestGitLabSessionBuilder
{
    @Test
    public void testIsSupportedProfile_OidcProfile()
    {
        OidcProfile profile = new OidcProfile();
        profile.setId("oidc-user");

        Assert.assertTrue("OidcProfile should be supported", GitLabSessionBuilder.isSupportedProfile(profile));
    }

    @Test
    public void testIsSupportedProfile_KerberosProfile()
    {
        Subject subject = new Subject(true, Collections.singleton(() -> "kerberos-user"), Collections.emptySet(), Collections.emptySet());
        KerberosProfile profile = new KerberosProfile(subject, null);
        profile.setId("kerberos-user");

        Assert.assertTrue("KerberosProfile should be supported", GitLabSessionBuilder.isSupportedProfile(profile));
    }

    @Test
    public void testIsSupportedProfile_GitlabPATProfile()
    {
        GitlabPersonalAccessTokenProfile profile = new GitlabPersonalAccessTokenProfile("token", "user-id", "username", "gitlab.example.com");

        Assert.assertTrue("GitlabPersonalAccessTokenProfile should be supported", GitLabSessionBuilder.isSupportedProfile(profile));
    }

    @Test
    public void testIsSupportedProfile_CommonProfile()
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("common-user");

        Assert.assertFalse("CommonProfile should not be supported", GitLabSessionBuilder.isSupportedProfile(profile));
    }

    @Test
    public void testIsSupportedProfile_Null()
    {
        Assert.assertFalse("Null profile should not be supported", GitLabSessionBuilder.isSupportedProfile(null));
    }
}
