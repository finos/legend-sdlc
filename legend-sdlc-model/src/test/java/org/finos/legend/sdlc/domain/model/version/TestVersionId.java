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

package org.finos.legend.sdlc.domain.model.version;

import org.finos.legend.sdlc.domain.model.TestTools;
import org.junit.Assert;
import org.junit.Test;

public class TestVersionId
{
    @Test
    public void testEquals()
    {
        VersionId v1_0_0 = VersionId.newVersionId(1, 0, 0);
        Assert.assertEquals(v1_0_0, v1_0_0);
        Assert.assertEquals(v1_0_0, VersionId.newVersionId(1, 0, 0));
        Assert.assertNotEquals(v1_0_0, VersionId.newVersionId(1, 0, 1));
    }

    @Test
    public void testCompareTo()
    {
        VersionId v1_0_0 = VersionId.newVersionId(1, 0, 0);
        TestTools.assertCompareTo(0, v1_0_0, v1_0_0);
        TestTools.assertCompareTo(0, v1_0_0, VersionId.newVersionId(1, 0, 0));

        TestTools.assertCompareTo(1, v1_0_0, VersionId.newVersionId(0, 0, 0));
        TestTools.assertCompareTo(1, v1_0_0, VersionId.newVersionId(0, 0, 1239874123));
        TestTools.assertCompareTo(1, v1_0_0, VersionId.newVersionId(0, 912398, 1239874123));

        TestTools.assertCompareTo(-1, v1_0_0, VersionId.newVersionId(2, 0, 0));
        TestTools.assertCompareTo(-1, v1_0_0, VersionId.newVersionId(1, 1, 0));
        TestTools.assertCompareTo(-1, v1_0_0, VersionId.newVersionId(1, 0, 1));
    }

    @Test
    public void testParseVersionId()
    {
        Assert.assertEquals(VersionId.newVersionId(1, 2, 3), VersionId.parseVersionId("1.2.3"));
        Assert.assertEquals(VersionId.newVersionId(1, 2, 3), VersionId.parseVersionId("1_2_3", '_'));
        Assert.assertEquals(VersionId.newVersionId(912387, 0, 3123867), VersionId.parseVersionId("912387.0.3123867"));
        Assert.assertEquals(VersionId.newVersionId(912387, 0, 3123867), VersionId.parseVersionId("912387/0/3123867", '/'));
    }

    @Test
    public void testIsValidVersionIdString()
    {
        Assert.assertTrue(VersionId.isValidVersionIdString("0.0.0"));
        Assert.assertTrue(VersionId.isValidVersionIdString("0.0.1"));
        Assert.assertTrue(VersionId.isValidVersionIdString("1.2.3"));
        Assert.assertTrue(VersionId.isValidVersionIdString("0_0_0", '_'));
        Assert.assertTrue(VersionId.isValidVersionIdString("1/2/3", '/'));
        Assert.assertTrue(VersionId.isValidVersionIdString(Integer.MAX_VALUE + "." + Integer.MAX_VALUE + "." + Integer.MAX_VALUE));

        Assert.assertFalse(VersionId.isValidVersionIdString(null));
        Assert.assertFalse(VersionId.isValidVersionIdString(""));
        Assert.assertFalse(VersionId.isValidVersionIdString("0"));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.2"));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.2.3."));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.2.3.4"));
        Assert.assertFalse(VersionId.isValidVersionIdString("0.0.00"));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.02.3"));
        Assert.assertFalse(VersionId.isValidVersionIdString("2023.04.21"));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.-2.3"));
        Assert.assertFalse(VersionId.isValidVersionIdString((1L + (long) Integer.MAX_VALUE) + "." + Integer.MAX_VALUE + "." + Integer.MAX_VALUE));
        Assert.assertFalse(VersionId.isValidVersionIdString("1.2." + Long.MAX_VALUE));
        Assert.assertFalse(VersionId.isValidVersionIdString("a.b.c"));
        Assert.assertFalse(VersionId.isValidVersionIdString("1a3.45.6"));

        Assert.assertFalse(VersionId.isValidVersionIdString("release-99.23.23456"));
        Assert.assertFalse(VersionId.isValidVersionIdString("release-99.23.23456", 6, 19));
        Assert.assertFalse(VersionId.isValidVersionIdString("release-99.23.23456", 7, 19));
        Assert.assertFalse(VersionId.isValidVersionIdString("release-99.23.23456", 8, 13));
        Assert.assertFalse(VersionId.isValidVersionIdString("release-99.23.23456", 8, 14));
        Assert.assertTrue(VersionId.isValidVersionIdString("release-99.23.23456", 8, 19));
    }

    @Test
    public void testToVersionIdString()
    {
        Assert.assertEquals("1.2.3", VersionId.newVersionId(1, 2, 3).toVersionIdString());
        Assert.assertEquals("1_2_3", VersionId.newVersionId(1, 2, 3).toVersionIdString('_'));
        Assert.assertEquals("912387.0.3123867", VersionId.newVersionId(912387, 0, 3123867).toVersionIdString());
        Assert.assertEquals("912387/0/3123867", VersionId.newVersionId(912387, 0, 3123867).toVersionIdString('/'));
    }

    @Test
    public void testNextMajorVersion()
    {
        Assert.assertEquals(VersionId.newVersionId(1, 0, 0), VersionId.newVersionId(0, 0, 0).nextMajorVersion());
        Assert.assertEquals(VersionId.newVersionId(1, 0, 0), VersionId.newVersionId(0, 0, 1).nextMajorVersion());
        Assert.assertEquals(VersionId.newVersionId(1, 0, 0), VersionId.newVersionId(0, 1, 1).nextMajorVersion());
        Assert.assertEquals(VersionId.newVersionId(2, 0, 0), VersionId.newVersionId(1, 9, 17).nextMajorVersion());
    }

    @Test
    public void testNextMinorVersion()
    {
        Assert.assertEquals(VersionId.newVersionId(0, 1, 0), VersionId.newVersionId(0, 0, 0).nextMinorVersion());
        Assert.assertEquals(VersionId.newVersionId(0, 1, 0), VersionId.newVersionId(0, 0, 1).nextMinorVersion());
        Assert.assertEquals(VersionId.newVersionId(0, 2, 0), VersionId.newVersionId(0, 1, 1).nextMinorVersion());
        Assert.assertEquals(VersionId.newVersionId(1, 10, 0), VersionId.newVersionId(1, 9, 17).nextMinorVersion());
    }

    @Test
    public void testNextPatchVersion()
    {
        Assert.assertEquals(VersionId.newVersionId(0, 0, 1), VersionId.newVersionId(0, 0, 0).nextPatchVersion());
        Assert.assertEquals(VersionId.newVersionId(0, 0, 2), VersionId.newVersionId(0, 0, 1).nextPatchVersion());
        Assert.assertEquals(VersionId.newVersionId(0, 1, 2), VersionId.newVersionId(0, 1, 1).nextPatchVersion());
        Assert.assertEquals(VersionId.newVersionId(1, 9, 18), VersionId.newVersionId(1, 9, 17).nextPatchVersion());
    }
}
