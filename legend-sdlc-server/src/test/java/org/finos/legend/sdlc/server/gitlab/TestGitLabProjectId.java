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

public class TestGitLabProjectId
{
    @Test
    public void testNewProjectId()
    {
        for (int i : getGitLabIdsForTest())
        {
            testNewProjectId("SOMEPREFIX", i);
            testNewProjectId(null, i);
        }
    }

    private void testNewProjectId(String prefix, int i)
    {
        GitLabProjectId projectId = GitLabProjectId.newProjectId(prefix, i);
        String projectIdString = projectId.toString();
        Assert.assertEquals((prefix == null) ? Integer.toString(i) : (prefix + "-" + i), projectIdString);
        Assert.assertEquals(projectIdString, i, projectId.getGitLabId());
        Assert.assertEquals(projectIdString, prefix, projectId.getPrefix());
    }

    @Test
    public void testEquality()
    {
        String prefix = "SOMEPREFIX";
        for (int i : getGitLabIdsForTest())
        {
            GitLabProjectId projectId = GitLabProjectId.newProjectId(prefix, i);
            Assert.assertEquals(projectId, projectId);
            Assert.assertEquals(projectId, GitLabProjectId.newProjectId(prefix, i));
            Assert.assertEquals(GitLabProjectId.newProjectId(null, i), GitLabProjectId.newProjectId(null, i));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId(prefix, i + 1));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId(prefix, i - 1));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId("ANOTHERPREFIX", i));
            Assert.assertNotEquals(projectId, GitLabProjectId.newProjectId(null, i));
        }
    }

    @Test
    public void testGetProjectIdString()
    {
        String prefix = "SOMEPREFIX";
        for (int i : getGitLabIdsForTest())
        {
            Project project = new Project().withId(i);
            Assert.assertEquals(prefix + "-" + i, GitLabProjectId.getProjectIdString(prefix, project));
            Assert.assertEquals(Integer.toString(i), GitLabProjectId.getProjectIdString(null, project));
        }
    }

    @Test
    public void testParseProjectId()
    {
        Assert.assertEquals(GitLabProjectId.newProjectId("SOMEPREFIX", 521), GitLabProjectId.parseProjectId("SOMEPREFIX-521"));
        Assert.assertEquals(GitLabProjectId.newProjectId("ANOTHER_PREFIX", 1923521), GitLabProjectId.parseProjectId("ANOTHER_PREFIX-1923521"));
        Assert.assertEquals(GitLabProjectId.newProjectId("54321", 12345), GitLabProjectId.parseProjectId("54321-12345"));
        Assert.assertEquals(GitLabProjectId.newProjectId("", 3332), GitLabProjectId.parseProjectId("-3332"));
        Assert.assertEquals(GitLabProjectId.newProjectId(null, 99412), GitLabProjectId.parseProjectId("99412"));

        for (String invalidProjectId : new String []{null, "", "not-a-project-id", "NoDigits", "SeparatorAtEnd-", "UAT521", "UAT/521", "UAT*521", "UAT-&&&"})
        {
            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> GitLabProjectId.parseProjectId(invalidProjectId));
            String expectedMessage = (invalidProjectId == null) ? "Invalid project id: null" : ("Invalid project id: \"" + invalidProjectId + "\"");
            Assert.assertEquals(expectedMessage, e.getMessage());
        }
    }

    private int[] getGitLabIdsForTest()
    {
        return new int[]{0, 1, 2, 3, 4, 5, 64, 127, 511, 1024, 17_560_438, 1_030_991_200, Integer.MAX_VALUE};
    }
}
