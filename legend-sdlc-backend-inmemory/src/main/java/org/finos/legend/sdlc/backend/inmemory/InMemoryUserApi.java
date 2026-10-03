// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.inmemory;

import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.error.LegendSDLCException;

import java.util.Collections;
import java.util.List;

/**
 * The in-memory backend has no user directory: the only known user is the session's. {@code getUserById} echoes
 * any requested id back as a user (ids are not validated against a directory that does not exist).
 */
public class InMemoryUserApi implements UserApi
{
    private final String userId;

    InMemoryUserApi(String userId)
    {
        this.userId = userId;
    }

    @Override
    public List<User> getUsers()
    {
        return (this.userId == null) ? Collections.emptyList() : Collections.singletonList(newUser(this.userId));
    }

    @Override
    public User getUserById(String id)
    {
        LegendSDLCException.validateNonNull(id, "id may not be null", 400);
        return newUser(id);
    }

    @Override
    public List<User> findUsers(String searchString)
    {
        LegendSDLCException.validateNonNull(searchString, "searchString may not be null", 400);
        return ((this.userId != null) && this.userId.contains(searchString)) ? Collections.singletonList(newUser(this.userId)) : Collections.emptyList();
    }

    @Override
    public User getCurrentUserInfo()
    {
        return (this.userId == null) ? null : newUser(this.userId);
    }

    private static User newUser(String id)
    {
        return new User()
        {
            @Override
            public String getUserId()
            {
                return id;
            }

            @Override
            public String getName()
            {
                return id;
            }
        };
    }
}
