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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.finos.legend.sdlc.server.project.EmptyFileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.tools.IOTools;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class TestFinosGitlabProjectStructureExtension
{
    private final ImmutableIntSet unpublishedVersion = IntSets.immutable.with(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    private int latestProjectStructureVersion;
    private FinosGitlabProjectStructureExtensionProvider provider;

    @Before
    public void setUp()
    {
        this.latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
        this.provider = new FinosGitlabProjectStructureExtensionProvider();
    }

    @Test
    public void testLatestVersionForProjectStructureVersion()
    {
        Assert.assertNull(this.provider.getLatestVersionForProjectStructureVersion(0));

        for (int i = 1; i <= this.latestProjectStructureVersion; i++)
        {
            if (this.unpublishedVersion.contains(i))
            {
                Assert.assertNull(Integer.toString(i), this.provider.getLatestVersionForProjectStructureVersion(i));
            }
            else
            {
                Assert.assertNotNull(Integer.toString(i), this.provider.getLatestVersionForProjectStructureVersion(i));
            }
        }
    }

    @Test
    public void testProjectStructureVersion11()
    {
        Assert.assertEquals(Integer.valueOf(2), this.provider.getLatestVersionForProjectStructureVersion(11));

        ProjectStructureExtension ext1 = this.provider.getProjectStructureExtension(11, 1);
        assertFiles(Maps.mutable.with("/.gitlab-ci.yml", loadTextResource("org/finos/legend/sdlc/server/gitlab/finos/gitlab-ci-1.yml")), ext1);

        ProjectStructureExtension ext2 = this.provider.getProjectStructureExtension(11, 2);
        assertFiles(Maps.mutable.with("/.gitlab-ci.yml", loadTextResource("org/finos/legend/sdlc/server/gitlab/finos/gitlab-ci-2.yml")), ext2);
    }

    @Test
    public void testProjectStructureVersion12()
    {
        Assert.assertEquals(Integer.valueOf(2), this.provider.getLatestVersionForProjectStructureVersion(12));

        ProjectStructureExtension ext1 = this.provider.getProjectStructureExtension(12, 1);
        assertFiles(Maps.mutable.with("/.gitlab-ci.yml", loadTextResource("org/finos/legend/sdlc/server/gitlab/finos/gitlab-ci-2.yml")), ext1);

        ProjectStructureExtension ext2 = this.provider.getProjectStructureExtension(12, 2);
        assertFiles(Maps.mutable.with("/.gitlab-ci.yml", loadTextResource("org/finos/legend/sdlc/server/gitlab/finos/gitlab-ci-2.yml")), ext2);
    }

    @Test
    public void testProjectStructureVersion13()
    {
        Assert.assertEquals(Integer.valueOf(1), this.provider.getLatestVersionForProjectStructureVersion(13));

        ProjectStructureExtension ext1 = this.provider.getProjectStructureExtension(13, 1);
        assertFiles(Maps.mutable.with("/.gitlab-ci.yml", loadTextResource("org/finos/legend/sdlc/server/gitlab/finos/gitlab-ci-2.yml")), ext1);
    }

    @Test
    public void testNoUntestedProjectStructureVersions()
    {
        int expectedLatest = 13;
        if (this.latestProjectStructureVersion > expectedLatest)
        {
            Assert.fail(IntInterval.fromTo(expectedLatest + 1, this.latestProjectStructureVersion).makeString("Add tests for project structure version(s): ", ", ", ""));
        }
    }

    private void assertFiles(MutableMap<String, String> expectedFiles, ProjectStructureExtension extension)
    {
        MutableMap<String, String> actualFiles = Maps.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, new EmptyFileAccessContext(), op ->
        {
            if (!(op instanceof ProjectFileOperation.AddFile))
            {
                throw new RuntimeException("Expected AddFile operation, got: " + op);
            }
            String content = new String(((ProjectFileOperation.AddFile) op).getContent(), StandardCharsets.UTF_8);
            actualFiles.put(op.getPath(), content);
        });

        Assert.assertEquals(expectedFiles.keySet(), actualFiles.keySet());
        expectedFiles.forEachKeyValue((fileName, expectedContent) ->
        {
            String actualContent = actualFiles.get(fileName);
            Assert.assertEquals(fileName, expectedContent, actualContent);
        });
    }

    private String loadTextResource(String resource)
    {
        URL url = Objects.requireNonNull(getClass().getClassLoader().getResource(resource), resource);
        try
        {
            return IOTools.readAllToString(url, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
