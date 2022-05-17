// Copyright 2022 Goldman Sachs
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

import java.util.stream.Stream;

public class EmptyFileAccessContext implements ProjectFileAccessProvider.FileAccessContext
{
    public EmptyFileAccessContext()
    {
    }

    @Override
    public Stream<ProjectFileAccessProvider.ProjectFile> getFiles()
    {
        return Stream.empty();
    }

    @Override
    public Stream<ProjectFileAccessProvider.ProjectFile> getFilesInDirectory(String directory)
    {
        return Stream.empty();
    }

    @Override
    public Stream<ProjectFileAccessProvider.ProjectFile> getFilesInDirectories(Stream<? extends String> directories)
    {
        return Stream.empty();
    }

    @Override
    public Stream<ProjectFileAccessProvider.ProjectFile> getFilesInDirectories(Iterable<? extends String> directories)
    {
        return Stream.empty();
    }

    @Override
    public ProjectFileAccessProvider.ProjectFile getFile(String path)
    {
        return null;
    }

    @Override
    public boolean fileExists(String path)
    {
        return false;
    }
}
