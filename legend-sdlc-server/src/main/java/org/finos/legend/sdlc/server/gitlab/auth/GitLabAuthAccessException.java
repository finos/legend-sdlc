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

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;

/**
 * Exception indicating that there was an issue accessing the auth server.
 */
public class GitLabAuthAccessException extends GitLabAuthException
{
    GitLabAuthAccessException(String user, GitLabMode mode, String detail, Throwable cause)
    {
        super(user, mode, detail, cause);
    }

    GitLabAuthAccessException(String user, GitLabMode mode, String detail)
    {
        super(user, mode, detail);
    }

    GitLabAuthAccessException(String user, GitLabMode mode, Throwable cause)
    {
        super(user, mode, cause);
    }

    GitLabAuthAccessException(GitLabMode mode, String detail, Throwable cause)
    {
        super(mode, detail, cause);
    }

    GitLabAuthAccessException(String user, GitLabMode mode)
    {
        super(user, mode);
    }

    GitLabAuthAccessException(GitLabMode mode, String detail)
    {
        super(mode, detail);
    }

    GitLabAuthAccessException(String detail, Throwable cause)
    {
        super(detail, cause);
    }

    GitLabAuthAccessException(String detail)
    {
        super(detail);
    }
}
