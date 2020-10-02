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

package org.finos.legend.sdlc.server.project.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ProjectFileConfiguration
{
    private final String content;
    private final String path;
    private final String resourceName;
    private final String url;

    private ProjectFileConfiguration(String content, String path, String resourceName, String url)
    {
        this.content = content;
        this.path = path;
        this.resourceName = resourceName;
        this.url = url;
    }

    public String getContent()
    {
        return this.content;
    }

    public String getPath()
    {
        return this.path;
    }

    public String getResourceName()
    {
        return this.resourceName;
    }

    public String getUrl()
    {
        return this.url;
    }

    public String resolveContent() throws IOException
    {
        return resolveContent(null, null);
    }

    public String resolveContent(Charset charset) throws IOException
    {
        return resolveContent(charset, null);
    }

    public String resolveContent(Charset charset, ClassLoader classLoader) throws IOException
    {
        if (this.content != null)
        {
            return this.content;
        }

        if (this.path != null)
        {
            try (InputStream stream = Files.newInputStream(Paths.get(this.path)))
            {
                return readFromStream(stream, charset);
            }
        }

        if (this.resourceName != null)
        {
            URL resourceUrl = ((classLoader == null) ? getClass().getClassLoader() : classLoader).getResource(this.resourceName);
            if (resourceUrl == null)
            {
                throw new IOException("resource not found: " + this.resourceName);
            }
            try (InputStream stream = resourceUrl.openStream())
            {
                return readFromStream(stream, charset);
            }
        }

        if (this.url != null)
        {
            try (InputStream stream = new URL(this.url).openStream())
            {
                return readFromStream(stream, charset);
            }
        }

        return null;
    }

    @JsonCreator
    public static ProjectFileConfiguration newProjectFileConfiguration(@JsonProperty("content") String content, @JsonProperty("path") String path, @JsonProperty("resourceName") String resourceName, @JsonProperty("url") String url)
    {
        return new ProjectFileConfiguration(content, path, resourceName, url);
    }

    private static String readFromStream(InputStream stream, Charset charset) throws IOException
    {
        int bufferSize = 4096;
        StringBuilder builder = new StringBuilder(bufferSize);
        try (Reader reader = new InputStreamReader(stream, (charset == null) ? StandardCharsets.UTF_8 : charset))
        {
            char[] buffer = new char[bufferSize];
            int read;
            while ((read = reader.read(buffer, 0, bufferSize)) >= 0)
            {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }
}
