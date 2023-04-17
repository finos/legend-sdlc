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

package org.finos.legend.sdlc.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class PathTools
{
    public static Path resourceToPath(String resourceName)
    {
        return resourceToPath(null, resourceName);
    }

    public static Path[] resourceToPaths(String resourceName)
    {
        return resourceToPaths(null, resourceName);
    }

    public static Path resourceToPath(ClassLoader classLoader, String resourceName)
    {
        URL url = resolveClassLoader(classLoader).getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        try
        {
            return Paths.get(url.toURI());
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error getting resource \"").append(resourceName).append("\" from URL ").append(url);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
        }
    }

    public static Path[] resourceToPaths(ClassLoader classLoader, String resourceName)
    {
        Enumeration<URL> urls;
        try
        {
            urls = resolveClassLoader(classLoader).getResources(resourceName);
        }
        catch (IOException e)
        {
            StringBuilder builder = new StringBuilder("Error getting resource \"").append(resourceName).append('"');
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new UncheckedIOException(builder.toString(), e);
        }
        if (urls == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        List<Path> paths = new ArrayList<>();
        while (urls.hasMoreElements())
        {
            URL url = urls.nextElement();
            try
            {
                paths.add(Paths.get(url.toURI()));
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error getting resource \"").append(resourceName).append("\" from URL ").append(url);
                String eMessage = e.getMessage();
                if (eMessage != null)
                {
                    builder.append(": ").append(eMessage);
                }
                throw new RuntimeException(builder.toString(), e);
            }
        }
        return paths.toArray(new Path[0]);
    }

    private static ClassLoader resolveClassLoader(ClassLoader classLoader)
    {
        return (classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader;
    }
}
