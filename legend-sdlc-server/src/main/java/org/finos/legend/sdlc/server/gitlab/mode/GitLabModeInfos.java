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

package org.finos.legend.sdlc.server.gitlab.mode;

import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class GitLabModeInfos implements Serializable
{
    private static final long serialVersionUID = 5478794417406325731L;

    private final Map<GitLabMode, GitLabModeInfo> modeInfos;

    private GitLabModeInfos(Map<GitLabMode, GitLabModeInfo> modeConfigs)
    {
        this.modeInfos = modeConfigs;
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other) || ((other instanceof GitLabModeInfos) && this.modeInfos.equals(((GitLabModeInfos)other).modeInfos));
    }

    @Override
    public int hashCode()
    {
        return this.modeInfos.hashCode();
    }

    public boolean hasModeInfo(GitLabMode mode)
    {
        return this.modeInfos.containsKey(mode);
    }

    public GitLabModeInfo getModeInfo(GitLabMode mode)
    {
        return this.modeInfos.get(mode);
    }

    public Set<GitLabMode> getModes()
    {
        return Collections.unmodifiableSet(this.modeInfos.keySet());
    }

    public boolean isEmpty()
    {
        return this.modeInfos.isEmpty();
    }

    public static GitLabModeInfos fromConfig(GitLabConfiguration config)
    {
        Map<GitLabMode, GitLabModeInfo> modeInfos = new EnumMap<>(GitLabMode.class);
        GitLabConfiguration.ModeConfiguration uatConfig = config.getUATConfiguration();
        if (uatConfig != null)
        {
            modeInfos.put(GitLabMode.UAT, GitLabModeInfo.newModeInfo(GitLabMode.UAT, uatConfig));
        }
        GitLabConfiguration.ModeConfiguration prodConfig = config.getProdConfiguration();
        if (prodConfig != null)
        {
            modeInfos.put(GitLabMode.PROD, GitLabModeInfo.newModeInfo(GitLabMode.PROD, prodConfig));
        }
        return new GitLabModeInfos(modeInfos);
    }

    public static GitLabModeInfos fromInfos(GitLabModeInfo... infos)
    {
        Map<GitLabMode, GitLabModeInfo> modeInfos = new EnumMap<>(GitLabMode.class);
        for (GitLabModeInfo info : infos)
        {
            GitLabModeInfo old = modeInfos.put(info.getMode(), info);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple infos for mode: " + info.getMode());
            }
        }
        return new GitLabModeInfos(modeInfos);
    }
}
