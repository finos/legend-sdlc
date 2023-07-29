// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

public class FSConfiguration
{
    private static String rootDirectory;

    static
    {
        try
        {
            InputStream inputStream = new FileInputStream("legend-sdlc-server-fs/src/test/resources/config.yml");
            Yaml yaml = new Yaml();
            Map<String, String> configData = yaml.load(inputStream);
            rootDirectory = configData.get("fileSystemRootDirectory");

            // Check if rootDirectory exists, and create if not
            File localFile = new File(rootDirectory);
            if (!localFile.exists() && !localFile.mkdirs())
            {
                throw new RuntimeException("Failed to create directories for rootDirectory");
            }
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException("Error loading config.yaml: " + e.getMessage());
        }
    }

    public static String getRootDirectory()
    {
        return rootDirectory;
    }
}
