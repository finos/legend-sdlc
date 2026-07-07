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

package org.finos.legend.sdlc.server.project.extension;

/**
 * @deprecated Use {@link org.finos.legend.sdlc.structure.extension.ProjectStructureExtension}, which this interface
 * merely extends. Existing implementations of this interface remain valid — they are implementations of the
 * relocated interface — but should re-implement the relocated interface directly. This bridge is retained
 * temporarily for compatibility and will then be removed.
 */
@Deprecated
public interface ProjectStructureExtension extends org.finos.legend.sdlc.structure.extension.ProjectStructureExtension
{
}
