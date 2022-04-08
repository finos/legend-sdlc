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

package org.finos.legend.sdlc.server.gitlab.api;

import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.server.domain.api.user.UserApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.stream.Collectors;

public class GitLabUserApi extends BaseGitLabApi implements UserApi
{
    @Inject
    public GitLabUserApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext)
    {
        super(gitLabConfiguration, userContext);
    }

    @Override
    public List<User> getUsers()
    {
        try
        {
            return PagerTools.stream(getGitLabApi().getUserApi().getUsers(ITEMS_PER_PAGE))
                .map(BaseGitLabApi::fromGitLabAbstractUser).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get users",
                null,
                () -> "Error getting users");
        }
    }

    @Override
    public User getUserById(String userId)
    {
        LegendSDLCServerException.validateNonNull(userId, "userId cannot be null");
        User user;
        try
        {
            user = fromGitLabAbstractUser(getGitLabApi().getUserApi().getUser(userId));
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get user " + userId,
                () -> "Unknown user: " + userId,
                () -> "Error getting user " + userId);
        }
        if (user != null)
        {
            return user;
        }
        throw new LegendSDLCServerException("Unknown user: " + userId, Status.NOT_FOUND);
    }

    @Override
    public List<User> findUsers(String search)
    {
        LegendSDLCServerException.validateNonNull(search, "search cannot be null");
        try
        {
            return PagerTools.stream(getGitLabApi().getUserApi().findUsers(search, ITEMS_PER_PAGE))
                .map(BaseGitLabApi::fromGitLabAbstractUser).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to search users: " + search,
                null,
                () -> "Error finding users with search string: " + search);
        }
    }

    @Override
    public User getCurrentUserInfo()
    {
        User user;
        try
        {
            user = fromGitLabAbstractUser(getGitLabApi().getUserApi().getCurrentUser());
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get current user information",
                null,
                () -> "Error getting current user information");
        }
        if (user != null)
        {
            return user;
        }
        throw new LegendSDLCServerException("Could not get current user information");
    }
}
