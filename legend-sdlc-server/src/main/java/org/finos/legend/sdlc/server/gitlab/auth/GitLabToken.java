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

import org.gitlab4j.api.Constants.TokenType;

public class GitLabToken
{
    private final TokenType tokenType;
    private final String token;

    private GitLabToken(TokenType tokenType, String token)
    {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabToken))
        {
            return false;
        }

        GitLabToken that = (GitLabToken)other;
        return this.tokenType.equals(that.tokenType) && this.token.equals(that.token);
    }

    @Override
    public int hashCode()
    {
        return this.tokenType.hashCode() +  61 * this.token.hashCode();
    }

    @Override
    public String toString()
    {
        return "<GitLabToken " + this.tokenType + '=' + this.token + '>';
    }

    public TokenType getTokenType()
    {
        return this.tokenType;
    }

    public String getToken()
    {
        return this.token;
    }

    public static GitLabToken newGitLabToken(TokenType type, String token)
    {
        if (token == null)
        {
            throw new IllegalArgumentException("Cannot create GitLabToken when token value is empty");
        }
        if (type == null)
        {
            throw new IllegalArgumentException("Cannot create GitLabToken when token type is empty");
        }
        return new GitLabToken(type, token);
    }
}
