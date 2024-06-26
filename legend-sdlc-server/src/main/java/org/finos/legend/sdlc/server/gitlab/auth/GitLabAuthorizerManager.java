// Copyright 2021 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;

import java.util.Arrays;

public class GitLabAuthorizerManager
{
    private final ListIterable<? extends GitLabAuthorizer> gitLabAuthorizers;

    private GitLabAuthorizerManager(Iterable<? extends GitLabAuthorizer> gitLabAuthorizers)
    {
        MutableList<GitLabAuthorizer> authorizers = Lists.mutable.ofAll(gitLabAuthorizers);
        if (authorizers.isEmpty())
        {
            authorizers.add(new KerberosGitLabAuthorizer());
        }
        this.gitLabAuthorizers = authorizers.toImmutable();
    }

    public static GitLabAuthorizerManager newManager(GitLabAuthorizer... gitLabAuthorizers)
    {
        return newManager(Arrays.asList(gitLabAuthorizers));
    }

    public static GitLabAuthorizerManager newManager(Iterable<? extends GitLabAuthorizer> gitLabAuthorizers)
    {
        return new GitLabAuthorizerManager(gitLabAuthorizers);
    }

    public GitLabTokenResponse authorize(Session session, GitLabAppInfo appInfo)
    {
        for (GitLabAuthorizer gitLabAuthorizer : this.gitLabAuthorizers)
        {
            GitLabTokenResponse token = gitLabAuthorizer.authorize(session, appInfo);
            if (token != null)
            {
                return token;
            }
        }
        return null;
    }
}
