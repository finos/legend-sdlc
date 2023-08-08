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

package org.finos.legend.sdlc.server.startup;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

public class FSConfiguration
{
    public final String rootDirectory;

    private FSConfiguration(String rootDirectory)
    {
        this.rootDirectory = rootDirectory;
    }

    public String getRootDirectory()
    {
        return rootDirectory;
    }

    @JsonCreator
    public static FSConfiguration newConfiguration(@JsonProperty("rootDirectory") String rootDirectory)
    {
        // Check if rootDirectory exists, and create if not
        File localFile = new File(rootDirectory);
        if (!localFile.exists() && !localFile.mkdirs())
        {
            throw new RuntimeException("Failed to create directories for rootDirectory");
        }
        return new FSConfiguration(rootDirectory);
    }
}
