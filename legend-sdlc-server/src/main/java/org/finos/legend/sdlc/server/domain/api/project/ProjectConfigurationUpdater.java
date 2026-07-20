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

package org.finos.legend.sdlc.server.domain.api.project;

/**
 * @deprecated Use {@link org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater}, which this class merely
 * extends (the implementation moved to the SDLC core module in re-architecture Phase 3). This bridge is retained
 * temporarily for compatibility and will then be removed. Note that the fluent {@code with*} methods return the
 * relocated type, not this one.
 */
@Deprecated
public class ProjectConfigurationUpdater extends org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater
{
    public static ProjectConfigurationUpdater newUpdater()
    {
        return new ProjectConfigurationUpdater();
    }
}
