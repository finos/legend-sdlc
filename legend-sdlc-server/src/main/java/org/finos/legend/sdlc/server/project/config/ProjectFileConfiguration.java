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
import org.finos.legend.sdlc.server.tools.IOTools;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
            return IOTools.readAllToString(Paths.get(this.path), resolveCharset(charset));
        }

        if (this.resourceName != null)
        {
            URL resourceUrl = ((classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader).getResource(this.resourceName);
            if (resourceUrl == null)
            {
                throw new IOException("resource not found: " + this.resourceName);
            }
            return IOTools.readAllToString(resourceUrl, resolveCharset(charset));
        }

        if (this.url != null)
        {
            return IOTools.readAllToString(new URL(this.url), resolveCharset(charset));
        }

        return null;
    }

    private Charset resolveCharset(Charset charset)
    {
        return (charset == null) ? StandardCharsets.UTF_8 : charset;
    }

    @JsonCreator
    public static ProjectFileConfiguration newProjectFileConfiguration(@JsonProperty("content") String content, @JsonProperty("path") String path, @JsonProperty("resourceName") String resourceName, @JsonProperty("url") String url)
    {
        return new ProjectFileConfiguration(content, path, resourceName, url);
    }

    public static ProjectFileConfiguration newContent(String content)
    {
        return newProjectFileConfiguration(content, null, null, null);
    }

    public static ProjectFileConfiguration newPath(String path)
    {
        return newProjectFileConfiguration(null, path, null, null);
    }

    public static ProjectFileConfiguration newResourceName(String resourceName)
    {
        return newProjectFileConfiguration(null, null, resourceName, null);
    }

    public static ProjectFileConfiguration newUrl(String url)
    {
        return newProjectFileConfiguration(null, null, null, url);
    }
}
