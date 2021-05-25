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
        if (classLoader == null)
        {
            classLoader = PathTools.class.getClassLoader();
        }
        URL url = classLoader.getResource(resourceName);
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
            throw new RuntimeException("Error getting resource \"" + resourceName + "\" from URL " + url, e);
        }
    }

    public static Path[] resourceToPaths(ClassLoader classLoader, String resourceName)
    {
        if (classLoader == null)
        {
            classLoader = PathTools.class.getClassLoader();
        }
        Enumeration<URL> urls;
        try
        {
            urls = classLoader.getResources(resourceName);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not get resource: " + resourceName, e);
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
                throw new RuntimeException("Error getting resource \"" + resourceName + "\" from URL " + url, e);
            }
        }
        return paths.toArray(new Path[0]);
    }
}
