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

package org.finos.legend.sdlc.server.gitlab.finos;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.sdlc.server.tools.IOTools;
import org.finos.legend.sdlc.server.tools.StringTools;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

class FinosFileLoader
{
    private final MutableIntObjectMap<String> cache = IntObjectMaps.mutable.empty();
    private final ClassLoader classLoader;
    private final String fileExtension;
    private final String fileName;
    private static final String DEFAULT_FILE_EXTENSION = "yml";

    FinosFileLoader(ClassLoader classLoader, String fileName, String fileExtension)
    {
        this.classLoader = classLoader;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
    }

    FinosFileLoader(ClassLoader classLoader, String fileName)
    {
        this(classLoader, fileName, null);
    }

    String getCIResource(int version)
    {
        synchronized (this.cache)
        {
            return this.cache.getIfAbsentPutWithKey(version, this::loadCIResource);
        }
    }

    protected String loadCIResource(int version)
    {
        String resourceName = "org/finos/legend/sdlc/server/gitlab/finos/" + this.fileName + "-" + version + "." + (this.fileExtension != null ? this.fileExtension : DEFAULT_FILE_EXTENSION);
        URL url = this.classLoader.getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }

        try (InputStream stream = url.openStream())
        {
            return IOTools.readAllToString(stream, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new RuntimeException(StringTools.appendThrowableMessageIfPresent(new StringBuilder("Error reading resource ").append(resourceName).append(" (").append(url).append(')'), e).toString(), e);
        }
    }

}
