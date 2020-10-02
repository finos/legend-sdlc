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

import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.server.domain.api.user.UserApi;
import org.finos.legend.sdlc.server.error.MetadataException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitLabUserApi extends BaseGitLabApi implements UserApi
{
    private static final Comparator<User> USER_COMPARATOR = (user1, user2) ->
    {
        if (user1 == user2)
        {
            return 0;
        }
        String user1Id = user1.getUserId();
        String user2Id = user2.getUserId();
        return (user1Id == null) ? ((user2Id == null) ? 0 : 1) : ((user2Id == null) ? -1 : user1Id.compareTo(user2Id));
    };

    @Inject
    public GitLabUserApi(GitLabUserContext userContext)
    {
        super(userContext);
    }

    @Override
    public List<User> getUsers()
    {
        try
        {
            Map<String, User> usersById = Maps.mutable.empty();
            for (GitLabMode mode : getValidGitLabModes())
            {
                PagerTools.stream(getGitLabApi(mode).getUserApi().getUsers(ITEMS_PER_PAGE))
                        .map(BaseGitLabApi::fromGitLabAbstractUser)
                        .forEach(user -> usersById.putIfAbsent(user.getUserId(), user));
            }
            return usersById.values().stream().sorted(USER_COMPARATOR).collect(Collectors.toList());
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
        MetadataException.validateNonNull(userId, "userId cannot be null");
        Exception exception = null;
        for (GitLabMode mode : getValidGitLabModes())
        {
            User user;
            try
            {
                user = fromGitLabAbstractUser(getGitLabApi(mode).getUserApi().getUser(userId));
            }
            catch (Exception e)
            {
                exception = e;
                user = null;
            }
            if (user != null)
            {
                return user;
            }
        }
        if (exception != null)
        {
            throw buildException(exception,
                    () -> "User " + getCurrentUser() + " is not allowed to get user " + userId,
                    () -> "Unknown user: " + userId,
                    () -> "Error getting user " + userId);
        }
        throw new MetadataException("Unknown user: " + userId, Status.NOT_FOUND);
    }

    @Override
    public List<User> findUsers(String search)
    {
        MetadataException.validateNonNull(search, "search cannot be null");
        try
        {
            Map<String, User> usersById = Maps.mutable.empty();
            for (GitLabMode mode : getValidGitLabModes())
            {
                PagerTools.stream(getGitLabApi(mode).getUserApi().findUsers(search, ITEMS_PER_PAGE))
                        .map(BaseGitLabApi::fromGitLabAbstractUser)
                        .forEach(user -> usersById.putIfAbsent(user.getUserId(), user));
            }
            return usersById.values().stream().sorted(USER_COMPARATOR).collect(Collectors.toList());
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
        try
        {
            User user = null;
            for (GitLabMode mode : getValidGitLabModes())
            {
                user = fromGitLabAbstractUser(getGitLabApi(mode).getUserApi().getCurrentUser());
                break;
            }
            if (user == null)
            {
                throw new MetadataException("Error getting current user information");
            }
            return user;
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get current user information",
                    null,
                    () -> "Error getting current user information");
        }
    }
}
