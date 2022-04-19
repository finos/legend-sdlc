// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.finos;

import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFinosGitlabProjectStructureExtension
{

    private int latestProjectStructureVersion;
    protected final ImmutableIntSet unpublishedVersion = IntSets.immutable.with(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    protected final ImmutableIntSet noProviderVersion = IntSets.immutable.with(0);

    @Before
    public void setUp()
    {
        this.latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
    }

    @Test
    public void testLatestVersionForProjectStructureVersion()
    {
        FinosGitlabProjectStructureExtensionProvider provider = new FinosGitlabProjectStructureExtensionProvider();
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            if (!unpublishedVersion.contains(i))
            {
                Integer latestVersionForProjectStructureVersion = provider.getLatestVersionForProjectStructureVersion(i);
                if (noProviderVersion.contains(i))
                {
                    Assert.assertNull(latestVersionForProjectStructureVersion);
                }
                else
                {
                    Assert.assertNotNull(latestVersionForProjectStructureVersion);
                    int gitlabVersion = provider.getGitLabCIFileVersion(i, latestVersionForProjectStructureVersion);
                    Assert.assertTrue(gitlabVersion > 0);
                }
            }
        }
    }
}
