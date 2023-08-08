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

package org.finos.legend.sdlc.server.api.user;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.server.domain.api.user.UserApi;
import org.finos.legend.sdlc.server.domain.model.user.FileSystemUser;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class FileSystemUserApi implements UserApi
{
    public static final FileSystemUser LOCAL_USER = new FileSystemUser("local_user", "local_user");
    public static final FileSystemUser DEMO_OWNER = new FileSystemUser("demo_owner", "demo_owner");

    private ImmutableMap<String, FileSystemUser> users = Maps.immutable.of(
                                                                                LOCAL_USER.getUserId(), LOCAL_USER,
                                                                                DEMO_OWNER.getUserId(), DEMO_OWNER
                                                                             );

    @Inject
    public FileSystemUserApi()
    {
    }

    @Override
    public List<User> getUsers()
    {
        return Lists.mutable.with(LOCAL_USER, DEMO_OWNER);
    }

    @Override
    public User getUserById(String userId)
    {
        return this.users.get(userId);
    }

    @Override
    public List<User> findUsers(String searchString)
    {
        return users.stream().filter(user -> user.getUserId().contains(searchString) || user.getName().contains(searchString)).collect(Collectors.toList());
    }

    @Override
    public User getCurrentUserInfo()
    {
        return LOCAL_USER;
    }
}