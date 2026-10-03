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

import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.tck.BackendScenarioTestSuite;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The file-system backend running the full backend TCK — the capability contract and the end-to-end scenarios —
 * over real git repositories in a temporary directory. Together with {@link TestFileSystemLayoutInvariants},
 * this is the certification the Phase 5 refit owed: the pre-refit provider could not even evaluate the suite
 * (its standard-context enumeration was broken; see the Phase 3 characterization record).
 */
public class TestFileSystemBackendContract extends BackendScenarioTestSuite
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected Backend newBackend()
    {
        try
        {
            return new FileSystemBackend(this.tempFolder.getRoot().getCanonicalFile().getAbsolutePath(), new TestFileSystemBackendEnvironment());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
