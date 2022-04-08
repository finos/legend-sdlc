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

package org.finos.legend.sdlc.server.gitlab;

import org.gitlab4j.api.models.Project;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestGitLabProjectId
{
    @Test
    public void testNewProjectId()
    {
        String prefix = "SOMEPREFIX";
        for (int i = 0; i < 1024; i++)
        {
            GitLabProjectId projectId = GitLabProjectId.newProjectId(prefix, i);
            Assert.assertEquals(i, projectId.getGitLabId());
            Assert.assertEquals(prefix + "-" + i, projectId.toString());
        }
    }

    @Test
    public void testEquality()
    {
        String prefix = "SOMEPREIFX";
        for (int i = 0; i < 1024; i++)
        {
            GitLabProjectId projectId = GitLabProjectId.newProjectId(prefix, i);
            Assert.assertEquals(projectId, projectId);
            Assert.assertEquals(projectId, GitLabProjectId.newProjectId(prefix, i));
            Assert.assertEquals(GitLabProjectId.newProjectId(null, i), GitLabProjectId.newProjectId(null, i));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId(prefix, i + 1));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId(prefix, i - 1));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId("ANOTHERPREFIX", i));
        }
    }

    @Test
    public void testGetProjectIdString()
    {
        String prefix = "SOMEPREFIX";
        for (int i = 0; i < 1024; i++)
        {
            Project project = new Project().withId(i);
            Assert.assertEquals(prefix + "-" + i, GitLabProjectId.getProjectIdString(prefix, project));
            Assert.assertEquals("" + i, GitLabProjectId.getProjectIdString(null, project));
        }
    }

    @Test
    public void testParseProjectId()
    {
        Assert.assertNull(GitLabProjectId.parseProjectId(null));
        Assert.assertEquals(GitLabProjectId.newProjectId("SOMEPREFIX", 521), GitLabProjectId.parseProjectId("SOMEPREFIX-521"));
        Assert.assertEquals(GitLabProjectId.newProjectId(null, 99412), GitLabProjectId.parseProjectId("99412"));

        for (String invalidProjectId : Arrays.asList("not-a-project-id", "NoSeparator", "UAT521", "UAT/521", "UAT*521", "UAT-&&&"))
        {
            try
            {
                GitLabProjectId.parseProjectId(invalidProjectId);
                Assert.fail("Expected exception parsing: " + invalidProjectId);
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("Invalid project id: " + invalidProjectId, e.getMessage());
            }
        }
    }
}
