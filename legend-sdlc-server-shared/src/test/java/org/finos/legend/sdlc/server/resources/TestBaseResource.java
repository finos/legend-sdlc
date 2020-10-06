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

package org.finos.legend.sdlc.server.resources;

import org.junit.Assert;
import org.junit.Test;

public class TestBaseResource
{
    @Test
    public void testSanitizeForLogging_SafeStrings()
    {
        String[] safeStrings = {
                "",
                "the quick brown fox jumped over the lazy dog",
                "Tom, Tom the piper's son, stole a pig and away did run!",
                " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~",
                "\\n\\t\\r\\f"
        };
        for (String string : safeStrings)
        {
            Assert.assertSame(string, string, BaseResource.sanitizeForLogging(string, "_"));
        }
    }

    @Test
    public void testSanitizeForLogging_UnsafeStrings()
    {
        Assert.assertEquals(
                "",
                BaseResource.sanitizeForLogging(null, "_"));
        Assert.assertEquals(
                "Tom, Tom the piper's son,*stole a pig and away did run!",
                BaseResource.sanitizeForLogging("Tom, Tom the piper's son,\nstole a pig and away did run!", "*"));
        Assert.assertEquals(
                "Charley, Charley stole the barley,%%from the baker's shop!",
                BaseResource.sanitizeForLogging("Charley, Charley stole the barley,\r\nfrom the baker's shop!", "%"));
        Assert.assertEquals(
                "Hot cross buns! Hot cross buns!",
                BaseResource.sanitizeForLogging("Hot\tcross\tbuns!\tHot\tcross\tbuns!", " "));
        Assert.assertEquals(
                "",
                BaseResource.sanitizeForLogging("\n\f\r\u2028\u2029\t", ""));
    }
}
