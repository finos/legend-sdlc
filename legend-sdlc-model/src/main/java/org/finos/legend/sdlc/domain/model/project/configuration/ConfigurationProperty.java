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

package org.finos.legend.sdlc.domain.model.project.configuration;

import java.util.List;

/**
 * Typed descriptor for a configuration option that a project structure version or project structure extension
 * declares (see the project-structure-configuration-options plan, which owns this schema; this interface is the
 * seam S2 landed by the re-architecture so that later option declarations are additive). Modeled on
 * {@link GenerationProperty}.
 */
public interface ConfigurationProperty
{
    String getName();

    String getDescription();

    ConfigurationPropertyType getType();

    Object getDefaultValue();

    boolean getRequired();

    /**
     * Allowed values, for {@link ConfigurationPropertyType#ENUM} properties; null or empty otherwise.
     *
     * @return allowed values or null
     */
    List<String> getAllowedValues();
}
