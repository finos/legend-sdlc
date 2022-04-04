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

public abstract class GitLabAuthException extends RuntimeException
{
    private static final String BASE_MESSAGE = "GitLab auth error";

    private final String user;
    private final String detail;

    GitLabAuthException(String user, String detail, Throwable cause)
    {
        super(detail, cause);
        this.user = user;
        this.detail = detail;
    }

    GitLabAuthException(String user, String detail)
    {
        super(detail);
        this.user = user;
        this.detail = detail;
    }

    public String getUser()
    {
        String localUser = this.user;
        if (localUser == null)
        {
            Throwable cause = getCause();
            if (cause instanceof GitLabAuthException)
            {
                localUser = ((GitLabAuthException)cause).getUser();
            }
        }
        return localUser;
    }

    public String getDetail()
    {
        String localDetail = this.detail;
        if (localDetail == null)
        {
            Throwable cause = getCause();
            if (cause instanceof GitLabAuthException)
            {
                localDetail = ((GitLabAuthException)cause).getDetail();
            }
        }
        return localDetail;
    }

    @Override
    public String getMessage()
    {
        return buildMessage(getUser(), getDetail());
    }

    private static String buildMessage(String user, String detail)
    {
        if ((detail == null) && (user == null))
        {
            return BASE_MESSAGE;
        }

        StringBuilder message = new StringBuilder(BASE_MESSAGE);
        if (user != null)
        {
            message.append(" (user='").append(user).append("')");
        }
        if (detail != null)
        {
            message.append(": ").append(detail);
        }
        return message.toString();
    }
}
