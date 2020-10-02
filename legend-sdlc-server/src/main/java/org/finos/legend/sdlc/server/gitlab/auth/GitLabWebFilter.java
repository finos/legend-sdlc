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

import org.finos.legend.sdlc.server.auth.MetadataWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;
import org.pac4j.core.profile.CommonProfile;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import java.util.List;

public class GitLabWebFilter extends MetadataWebFilter<CommonProfile>
{
    private final GitLabModeInfos gitLabModeInfos;

    private GitLabWebFilter(GitLabModeInfos gitLabModeInfos)
    {
        this.gitLabModeInfos = gitLabModeInfos;
    }

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void destroy()
    {
    }

    @Override
    protected Session newSession(List<CommonProfile> profiles, Cookie sessionCookie)
    {
        GitLabSession session = null;
        if (sessionCookie != null)
        {
            // Try to find a profile that works with the cookie
            session = newSessionFromProfilesAndToken(profiles, sessionCookie.getValue());
        }
        if (session == null)
        {
            // Try to find a profile that works without a cookie
            session = newSessionFromProfiles(profiles);
        }
        return session;
    }

    private GitLabSession newSessionFromProfilesAndToken(List<CommonProfile> profiles, String sessionToken)
    {
        GitLabSessionBuilder builder;
        try
        {
            builder = GitLabSessionBuilder.newBuilder(this.gitLabModeInfos).fromToken(sessionToken);
        }
        catch (Exception e)
        {
            LOGGER.debug("invalid session cookie: {}", sessionToken);
            return null;
        }

        String userId = builder.getUserId();
        if (userId == null)
        {
            return null;
        }
        for (CommonProfile profile : profiles)
        {
            if (userId.equals(profile.getId()) && GitLabSessionBuilder.isSupportedProfile(profile))
            {
                try
                {
                    GitLabSession session = builder.withProfile(profile).build();
                    LOGGER.debug("session created from cookie and profile: {} / {}", sessionToken, profile);
                    return session;
                }
                catch (Exception e)
                {
                    LOGGER.error("error creation session from cookie and profile: {} / {}", sessionToken, profile);
                    builder.reset().fromToken(sessionToken);
                }
            }
        }
        LOGGER.debug("no suitable profile for cookie: {}", sessionToken);
        return null;
    }

    private GitLabSession newSessionFromProfiles(List<CommonProfile> profiles)
    {
        for (CommonProfile profile : profiles)
        {
            if (GitLabSessionBuilder.isSupportedProfile(profile))
            {
                try
                {
                    GitLabSession session = GitLabSessionBuilder.newBuilder(this.gitLabModeInfos).withProfile(profile).build();
                    LOGGER.debug("session created from profile: {}", profile);
                    return session;
                }
                catch (Exception e)
                {
                    LOGGER.error("error creation session from profile: {}", profile);
                }
            }
        }
        return null;
    }

    public static Filter fromConfig(GitLabConfiguration config)
    {
        GitLabModeInfos modeInfos = GitLabModeInfos.fromConfig(config);
        return new GitLabWebFilter(modeInfos);
    }
}
