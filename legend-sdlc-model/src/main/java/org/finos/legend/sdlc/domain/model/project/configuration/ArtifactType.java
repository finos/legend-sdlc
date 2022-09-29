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

package org.finos.legend.sdlc.domain.model.project.configuration;

public enum ArtifactType
{
    @Deprecated
    avro("Avro"),

    @Deprecated
    java("Java"),

    @Deprecated
    jsonSchema("JSON Schema"),

    @Deprecated
    protobuf("Protobuf"),

    @Deprecated
    slang("Slang"),

    @Deprecated
    javaCode("Java Code"),

    @Deprecated
    cdm("Rosetta"),

    entities("Entity"),
    file_generation("File Generation"),
    versioned_entities("Versioned Entity"),
    service_execution("Service Execution");

    private final String label;

    ArtifactType(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return this.label;
    }
}
