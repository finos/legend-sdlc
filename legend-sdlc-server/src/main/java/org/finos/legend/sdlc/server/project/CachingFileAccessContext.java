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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;

import java.util.Map;
import java.util.stream.Stream;

public class CachingFileAccessContext extends AbstractFileAccessContext
{
    private final FileAccessContext delegate;
    private final MutableMap<String, byte[]> cache = Maps.mutable.empty();
    private boolean isCacheFull = false;

    private CachingFileAccessContext(FileAccessContext delegate)
    {
        this.delegate = delegate;
    }

    @Override
    protected Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
    {
        fillCache();
        Stream<Map.Entry<String, byte[]>> stream = this.cache.entrySet().stream();
        if (directories.size() == 1)
        {
            String directory = directories.get(0);
            if (!ProjectPaths.ROOT_DIRECTORY.equals(directory))
            {
                stream = stream.filter(f -> f.getKey().startsWith(directory));
            }
        }
        else
        {
            stream = stream.filter(f ->
            {
                String path = f.getKey();
                return directories.anySatisfy(path::startsWith);
            });
        }
        return stream.map(e -> ProjectFiles.newByteArrayProjectFile(e.getKey(), e.getValue()));
    }

    @Override
    public ProjectFile getFile(String path)
    {
        String canonicalPath = canonicalizePath(path);
        byte[] bytes = getFileBytes(canonicalPath);
        return (bytes == null) ? null : ProjectFiles.newByteArrayProjectFile(canonicalPath, bytes);
    }

    private byte[] getFileBytes(String canonicalPath)
    {
        synchronized (this.cache)
        {
            return this.isCacheFull ?
                    this.cache.get(canonicalPath) :
                    this.cache.getIfAbsentPutWithKey(canonicalPath, this::getFileBytesFromDelegate);
        }
    }

    private byte[] getFileBytesFromDelegate(String canonicalPath)
    {
        ProjectFile file = this.delegate.getFile(canonicalPath);
        return (file == null) ? null : file.getContentAsBytes();
    }

    public void fillCache()
    {
        synchronized (this.cache)
        {
            if (!this.isCacheFull)
            {
                this.delegate.getFiles().forEach(pf -> this.cache.getIfAbsentPut(pf.getPath(), pf::getContentAsBytes));
                this.isCacheFull = true;
            }
        }
    }

    public static CachingFileAccessContext wrap(FileAccessContext fileAccessContext)
    {
        if (fileAccessContext == null)
        {
            return null;
        }
        if (fileAccessContext instanceof CachingFileAccessContext)
        {
            return (CachingFileAccessContext) fileAccessContext;
        }
        return new CachingFileAccessContext(fileAccessContext);
    }

    private static String canonicalizePath(String path)
    {
        int pathLength = path.length();
        switch (pathLength)
        {
            case 0:
            {
                return "/";
            }
            case 1:
            {
                return (path.charAt(0) == '/') ? path : ("/" + path);
            }
            default:
            {
                int lastIndex = pathLength - 1;
                return (path.charAt(0) == '/') ?
                        ((path.charAt(lastIndex) == '/') ? path.substring(0, lastIndex) : path) :
                        ((path.charAt(lastIndex) == '/') ? ("/" + path.substring(0, lastIndex)) : ("/" + path));
            }
        }
    }
}
