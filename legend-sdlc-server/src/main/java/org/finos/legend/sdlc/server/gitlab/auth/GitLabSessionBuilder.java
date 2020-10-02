// Copyright 2020 Goldman Sachs
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

import org.finos.legend.sdlc.server.auth.SessionBuilder;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;
import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.oidc.profile.OidcProfile;

import java.time.Instant;
import java.util.Objects;

class GitLabSessionBuilder extends SessionBuilder<GitLabSession>
{
    private CommonProfile profile;
    private final GitLabTokenManager tokenManager;

    private GitLabSessionBuilder(GitLabModeInfos modeInfos)
    {
        this.tokenManager = GitLabTokenManager.newTokenManager(modeInfos);
    }

    public CommonProfile getProfile()
    {
        return this.profile;
    }

    public GitLabSessionBuilder withProfile(CommonProfile profile)
    {
        this.profile = profile;
        if ((profile != null) && (getUserId() == null))
        {
            withUserId(profile.getId());
        }
        return this;
    }

    @Override
    public GitLabSessionBuilder withUserId(String userId)
    {
        super.withUserId(userId);
        return this;
    }

    public GitLabSessionBuilder withCreationTime(Instant creationTime)
    {
        super.withCreationTime(creationTime);
        return this;
    }

    @Override
    public GitLabSessionBuilder fromToken(String tokenString)
    {
        super.fromToken(tokenString);
        return this;
    }

    @Override
    public GitLabSessionBuilder fromToken(Token.TokenReader reader)
    {
        super.fromToken(reader);
        this.tokenManager.putAllFromToken(reader);
        return this;
    }

    @Override
    public GitLabSessionBuilder reset()
    {
        super.reset();
        this.profile = null;
        return this;
    }

    @Override
    public void validate()
    {
        super.validate();
        CommonProfile profile = getProfile();
        if (profile == null)
        {
            throw new IllegalStateException("profile may not be null");
        }
        if (!Objects.equals(profile.getId(), getUserId()))
        {
            throw new IllegalStateException("User id (" + getUserId() + ") does not match profile: " + profile);
        }
        if (!isSupportedProfile(profile))
        {
            throw new IllegalStateException("Unsupported profile type: " + profile);
        }
    }

    @Override
    protected GitLabSession newSession()
    {
        if (this.profile instanceof KerberosProfile)
        {
            return new GitLabKerberosSession((KerberosProfile)this.profile, getUserId(), getCreationTime(), this.tokenManager);
        }
        if (this.profile instanceof OidcProfile)
        {
            return new GitLabOidcSession((OidcProfile)this.profile, getUserId(), getCreationTime(), this.tokenManager);
        }
        throw new IllegalStateException("Unsupported profile type: " + profile);
    }

    static boolean isSupportedProfile(CommonProfile profile)
    {
        return (profile instanceof KerberosProfile) || (profile instanceof OidcProfile);
    }

    static GitLabSessionBuilder newBuilder(GitLabModeInfos gitLabModeInfos)
    {
        return new GitLabSessionBuilder(gitLabModeInfos);
    }
}
