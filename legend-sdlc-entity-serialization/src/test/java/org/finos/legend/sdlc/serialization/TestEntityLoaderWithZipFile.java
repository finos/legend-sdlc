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

package org.finos.legend.sdlc.serialization;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class TestEntityLoaderWithZipFile extends TestEntityLoader
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected Path writeToTempZipFile(Map<String, byte[]> fileContentByPath, boolean jar) throws IOException
    {
        Path tempFile = Files.createTempFile(this.tempFolder.getRoot().toPath(), "junit", jar ? ".jar" : ".zip");
        writeToZipFile(fileContentByPath, tempFile, jar);
        return tempFile;
    }

    protected void writeToZipFile(Map<String, byte[]> fileContentByPath, Path path, boolean jar) throws IOException
    {
        try (OutputStream outStream = Files.newOutputStream(path);
             ZipOutputStream zipStream = jar ? new JarOutputStream(outStream) : new ZipOutputStream(outStream))
        {
            for (Map.Entry<String, byte[]> entry : fileContentByPath.entrySet())
            {
                zipStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipStream.write(entry.getValue());
                zipStream.closeEntry();
            }
        }
    }
}
