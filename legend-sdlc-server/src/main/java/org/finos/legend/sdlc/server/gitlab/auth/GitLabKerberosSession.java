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

import org.finos.legend.sdlc.server.auth.BaseKerberosSession;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.server.pac4j.kerberos.KerberosProfile;

import java.time.Instant;
import java.util.Objects;

public class GitLabKerberosSession extends BaseKerberosSession<KerberosProfile> implements GitLabSession
{
    private static final long serialVersionUID = 7521009570390907467L;

    private final GitLabTokenManager tokenManager;

    GitLabKerberosSession(KerberosProfile profile, String kerberosId, Instant creationTime, GitLabTokenManager tokenManager)
    {
        super(profile, kerberosId, creationTime);
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabKerberosSession))
        {
            return false;
        }

        GitLabKerberosSession that = (GitLabKerberosSession) other;
        return Objects.equals(this.getUserId(), that.getUserId()) &&
            Objects.equals(this.getProfile(), that.getProfile()) &&
            this.getCreationTime().equals(that.getCreationTime()) &&
            this.tokenManager.equals(that.tokenManager);
    }

    @Override
    public int hashCode()
    {
        return getCreationTime().hashCode() ^ getUserId().hashCode() ^ this.tokenManager.hashCode();
    }

    @Override
    public boolean gitLabOAuthCallback(String code)
    {
        return this.tokenManager.gitLabOAuthCallback(code);
    }

    @Override
    public GitLabToken getGitLabToken()
    {
        return this.tokenManager.getGitLabToken();
    }

    @Override
    public void clearGitLabToken()
    {
        this.tokenManager.clearGitLabToken();
    }

    @Override
    public void setGitLabToken(GitLabToken token)
    {
        this.tokenManager.setGitLabToken(token);
    }

    @Override
    public void setRefreshToken(String refreshToken)
    {
        this.tokenManager.setRefreshToken(refreshToken);
    }

    @Override
    public String getRefreshToken()
    {
        return this.tokenManager.getRefreshToken();
    }

    @Override
    public void setTokenExpiry(long expiryIn)
    {
        this.tokenManager.setTokenExpiry(expiryIn);
    }

    @Override
    public boolean shouldRefreshToken()
    {
        return this.tokenManager.shouldRefreshToken();
    }

    @Override
    public Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        return this.tokenManager.encode(super.encode(builder));
    }

    @Override
    protected void writeToStringInfo(StringBuilder builder)
    {
        this.tokenManager.appendGitLabTokenInfo(builder.append(' '));
    }
}
