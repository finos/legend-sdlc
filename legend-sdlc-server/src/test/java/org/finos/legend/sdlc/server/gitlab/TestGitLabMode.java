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

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestGitLabMode
{
    @Test
    public void testGetMode_CaseSensitive()
    {
        for (GitLabMode mode : GitLabMode.values())
        {
            Assert.assertEquals(mode, GitLabMode.getMode(mode.name(), true));
            try
            {
                GitLabMode.getMode(mode.name().toLowerCase(), true);
                Assert.fail("Expected exception: " + mode.name().toLowerCase());
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("Unknown mode: " + mode.name().toLowerCase(), e.getMessage());
            }
        }
    }

    @Test
    public void testGetMode_CaseInsensitive()
    {
        for (GitLabMode mode : GitLabMode.values())
        {
            Assert.assertEquals(mode, GitLabMode.getMode(mode.name(), false));
            Assert.assertEquals(mode, GitLabMode.getMode(mode.name().toLowerCase(), false));
        }

        Assert.assertEquals(GitLabMode.PROD, GitLabMode.getMode("pRoD", false));
        Assert.assertEquals(GitLabMode.UAT, GitLabMode.getMode("UAt", false));
    }

    @Test
    public void testGetMode_NonMode()
    {
        try
        {
            GitLabMode.getMode(null, true);
            Assert.fail("Expected exception");
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertEquals("string may not be null", e.getMessage());
        }

        for (String invalidMode : Arrays.asList("not-a-mode", "NoMode", "MODE!"))
        {
            try
            {
                GitLabMode.getMode(invalidMode, false);
                Assert.fail("Expected exception for: " + invalidMode);
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("Unknown mode: " + invalidMode, e.getMessage());
            }
        }
    }

    @Test
    public void testGetMode_SubString()
    {
        Assert.assertEquals(GitLabMode.UAT, GitLabMode.getMode("1234UAT5678", true, 4, 7));
        Assert.assertEquals(GitLabMode.UAT, GitLabMode.getMode("123UaT456", false, 3, 6));
        Assert.assertEquals(GitLabMode.PROD, GitLabMode.getMode("UATPRODUAT", true, 3, 7));
        Assert.assertEquals(GitLabMode.PROD, GitLabMode.getMode("UATprodUAT", false, 3, 7));
    }
}
