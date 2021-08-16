// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.inmemory.domain.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryEntity implements Entity
{
    private String path;
    private String classifierPath;
    private Map<String, ?> content;

    @Inject
    public InMemoryEntity()
    {
    }

    public InMemoryEntity(String path, String classifierPath, Map<String, ?> content)
    {
        this.path = path;
        this.classifierPath = classifierPath;
        this.content = content;
    }

    @Override
    public String getPath()
    {
        return this.path;
    }

    @Override
    public String getClassifierPath()
    {
        return this.classifierPath;
    }

    @Override
    public Map<String, ?> getContent()
    {
        return this.content;
    }

    @JsonIgnore
    public static Entity newEntity(String name, String pkg)
    {
        return newEntity(name, pkg, null, null);
    }

    @JsonIgnore
    public static Entity newEntity(String name, String pkg, Map<String, ?> property)
    {
        return newEntity(name, pkg, Collections.singletonList(property), null);
    }

    @JsonIgnore
    public static Entity newEntity(String name, String pkg, List<? extends Map<String, ?>> properties)
    {
        return newEntity(name, pkg, properties, null);
    }

    @JsonIgnore
    public static Entity newEntity(String name, String pkg, List<? extends Map<String, ?>> properties, List<String> superTypes)
    {
        Map<String, Object> content = new HashMap<>(5);
        content.put("_type", "class");
        content.put("name", name);
        content.put("package", pkg);
        content.put("properties", (properties == null) ? Collections.emptyList() : properties);
        content.put("superTypes", ((superTypes == null) || superTypes.isEmpty()) ? Collections.singletonList("meta::pure::metamodel::type::Any") : superTypes);
        return new InMemoryEntity(
                pkg + "::" + name,
                "meta::pure::metamodel::type::Class",
                content
        );
    }
}