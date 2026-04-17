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

package org.finos.legend.sdlc.server.project;

import org.finos.legend.sdlc.server.tools.IOTools;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class TestProjectFiles
{
    @Test
    public void testStringProjectFile()
    {
        String path = "/some/file/path.txt";
        String string = "the quick brown fox jumps over the lazy dog";
        assertProjectFile(ProjectFiles.newStringProjectFile(path, string), path, string);
    }

    @Test
    public void testLazyStringProjectFile()
    {
        String path = "/another/file/path.txt";
        String string = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG";
        assertProjectFile(ProjectFiles.newStringProjectFile(path, new NonRepeatingGetter<>(string)), path, string);
    }

    @Test
    public void testByteArrayProjectFile()
    {
        String path = "/yet/another/file/path.txt";
        String string = "the lazy brown fox jumps over the quick dog";
        assertProjectFile(ProjectFiles.newByteArrayProjectFile(path, string.getBytes(StandardCharsets.UTF_8)), path, string);
    }

    @Test
    public void testLazyByteArrayProjectFile()
    {
        String path = "/one/more/file/path.txt";
        String string = "the quick brown fox looks for the lazy dog";
        assertProjectFile(ProjectFiles.newByteArrayProjectFile(path, new NonRepeatingGetter<>(string.getBytes(StandardCharsets.UTF_8))), path, string);
    }

    @Test
    public void testLazyDelegatingProjectFile()
    {
        String path = "/some/file/path.txt";
        String string = "no foxes or dogs here, quick or lazy";
        assertProjectFile(ProjectFiles.newDelegatingProjectFile(path, new NonRepeatingGetter<>(ProjectFiles.newStringProjectFile(path, string))), path, string);
    }

    private void assertProjectFile(ProjectFileAccessProvider.ProjectFile projectFile, String expectedPath, String expectedContent)
    {
        Assert.assertEquals("path", expectedPath, projectFile.getPath());

        try
        {
            Assert.assertEquals("string", expectedContent, projectFile.getContentAsString());
            Assert.assertEquals("reader", expectedContent, readReader(projectFile.getContentAsReader()));

            byte[] expectedBytes = expectedContent.getBytes(StandardCharsets.UTF_8);
            Assert.assertArrayEquals("bytes", expectedBytes, projectFile.getContentAsBytes());
            Assert.assertArrayEquals("stream", expectedBytes, IOTools.readAllBytes(projectFile.getContentAsInputStream()));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String readReader(Reader reader) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[1024];
        for (int read = reader.read(buffer); read > -1; read = reader.read(buffer))
        {
            if (read > 0)
            {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private static class NonRepeatingGetter<T> implements Function<String, T>
    {
        private final T content;
        private int counter = 0;

        private NonRepeatingGetter(T content)
        {
            this.content = content;
        }

        @Override
        public T apply(String path)
        {
            if (this.counter++ > 0)
            {
                Assert.fail("Called multiple times (" + this.counter + ") for " + path);
            }
            return this.content;
        }
    }
}
