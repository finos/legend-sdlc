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
 * @deprecated Use {@link org.finos.legend.sdlc.project.structure.extension.UpdateProjectStructureExtension}, which this
 * interface merely extends. Note that this extension is discovered via {@code ServiceLoader} under the relocated
 * interface: implementations must re-key their {@code META-INF/services} registration to the relocated name, or
 * they will not be loaded. This bridge is retained temporarily for compatibility and will then be removed.
 */
@Deprecated
public interface UpdateProjectStructureExtension extends org.finos.legend.sdlc.project.structure.extension.UpdateProjectStructureExtension
{
}
