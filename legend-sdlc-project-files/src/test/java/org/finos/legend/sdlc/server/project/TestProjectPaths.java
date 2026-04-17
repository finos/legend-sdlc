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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.impl.factory.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.StreamSupport;

public class TestProjectPaths
{
    @Test
    public void testCanonicalizeDirectory()
    {
        Assert.assertEquals("/", ProjectPaths.canonicalizeDirectory(""));
        Assert.assertEquals("/", ProjectPaths.canonicalizeDirectory("/"));

        Assert.assertEquals("/a/", ProjectPaths.canonicalizeDirectory("a"));
        Assert.assertEquals("/a/", ProjectPaths.canonicalizeDirectory("/a"));
        Assert.assertEquals("/a/", ProjectPaths.canonicalizeDirectory("a/"));
        Assert.assertEquals("/a/", ProjectPaths.canonicalizeDirectory("/a/"));

        Assert.assertEquals("/abc/", ProjectPaths.canonicalizeDirectory("abc"));
        Assert.assertEquals("/abc/", ProjectPaths.canonicalizeDirectory("/abc"));
        Assert.assertEquals("/abc/", ProjectPaths.canonicalizeDirectory("abc/"));
        Assert.assertEquals("/abc/", ProjectPaths.canonicalizeDirectory("/abc/"));

        Assert.assertEquals("/abc/def/ghi/", ProjectPaths.canonicalizeDirectory("abc/def/ghi"));
        Assert.assertEquals("/abc/def/ghi/", ProjectPaths.canonicalizeDirectory("/abc/def/ghi"));
        Assert.assertEquals("/abc/def/ghi/", ProjectPaths.canonicalizeDirectory("abc/def/ghi/"));
        Assert.assertEquals("/abc/def/ghi/", ProjectPaths.canonicalizeDirectory("/abc/def/ghi/"));
    }

    @Test
    public void testCanonicalizeAndReduceDirectories()
    {
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with(), Lists.mutable.empty());

        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/"), Lists.mutable.with("/"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/"), Lists.mutable.with(""));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/"), Lists.mutable.with("/abc/def/ghi", "/"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/"), Lists.mutable.with("/abc/def/ghi", "abc/def/jkl/mnop", ""));

        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/"), Lists.mutable.with("abc/def"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/"), Lists.mutable.with("abc/def", "/abc/def", "abc/def/"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/"), Lists.mutable.with("abc/def", "/abc/def/ghi"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/"), Lists.mutable.with("/abc/def/ghi", "abc/def"));

        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/", "/xyz/qrs/", "/xyz/tuv/"), Lists.mutable.with("/abc/def/ghi", "abc/def", "/xyz/qrs", "/xyz/qrs/tuv", "xyz/tuv/qrs", "/xyz/tuv/"));
        assertCanonicalizeAndReduceDirectories(Lists.mutable.with("/abc/def/", "/xyz/qrs/", "/xyz/tuv/", "/xyz/tuvw/qrs/"), Lists.mutable.with("/abc/def/ghi", "abc/def", "/xyz/qrs", "/xyz/qrs/tuv", "xyz/tuvw/qrs", "/xyz/tuv/"));
    }

    private void assertCanonicalizeAndReduceDirectories(List<String> expected, Iterable<? extends String> directories)
    {
        Assert.assertEquals("iterable", expected, ProjectPaths.canonicalizeAndReduceDirectories(directories));
        Assert.assertEquals("stream", expected, ProjectPaths.canonicalizeAndReduceDirectories(StreamSupport.stream(directories.spliterator(), false)));
    }
}
