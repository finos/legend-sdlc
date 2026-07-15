// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.fs;

import org.finos.legend.sdlc.backend.tck.LayoutInvariantsTestSuite;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;

public class TestFileSystemLayoutInvariants extends LayoutInvariantsTestSuite
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected ProjectFileAccessProvider newProviderWithProject(String projectId)
    {
        try
        {
            // a fresh root per provider: the suite reuses project ids across its update-source versions
            FileSystemProjectFileAccessProvider provider = new FileSystemProjectFileAccessProvider(this.tempFolder.newFolder().getCanonicalFile().getAbsolutePath(), "tck-user");
            provider.initRepository(projectId, projectId, "layout invariants test project");
            return provider;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
