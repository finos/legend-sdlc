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

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;

import java.util.Collections;
import java.util.Map;

public class SimpleArtifactGeneration implements ArtifactGeneration
{
    private String name;
    private ArtifactType type;
    private Map<String, Object> parameters = Collections.emptyMap();

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public ArtifactType getType()
    {
        return type;
    }

    @Override
    public Map<String, Object> getParameters()
    {
        return parameters;
    }

    public SimpleArtifactGeneration withName(String name)
    {
        this.name = name;
        return this;
    }

    public SimpleArtifactGeneration withType(ArtifactType type)
    {
        this.type = type;
        return this;
    }

    public SimpleArtifactGeneration withParameters(Map<String, Object> parameters)
    {
        this.parameters = parameters;
        return this;
    }
}
