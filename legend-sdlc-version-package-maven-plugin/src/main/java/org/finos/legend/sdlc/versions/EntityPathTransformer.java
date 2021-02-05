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

package org.finos.legend.sdlc.versions;

import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class EntityPathTransformer
{
    private static final Pattern PACKAGE_PATH_PATTERN = Pattern.compile("^(\\w[\\w$]*+::)++\\w[\\w$]*+$");
    private static final String PACKAGE_KEY = "package";
    private static final String NAME_KEY = "name";

    private final Function<? super String, ? extends String> pathTransformer;
    private final List<Entity> entities = new ArrayList<>();

    private EntityPathTransformer(Function<? super String, ? extends String> pathTransformer)
    {
        this.pathTransformer = pathTransformer;
    }

    EntityPathTransformer addEntity(Entity entity)
    {
        this.entities.add(entity);
        return this;
    }

    EntityPathTransformer addEntities(Iterable<? extends Entity> entities)
    {
        entities.forEach(this::addEntity);
        return this;
    }

    EntityPathTransformer addEntities(Stream<? extends Entity> entities)
    {
        entities.forEach(this::addEntity);
        return this;
    }

    EntityPathTransformer addEntities(Entity... entities)
    {
        if (entities != null)
        {
            for (int i = 0; i < entities.length; i++)
            {
                addEntity(entities[i]);
            }
        }
        return this;
    }

    List<Entity> getEntities()
    {
        return Collections.unmodifiableList(this.entities);
    }

    int getEntityCount()
    {
        return this.entities.size();
    }

    List<Entity> transformEntities()
    {
        List<Entity> newEntities = new ArrayList<>(this.entities.size());
        this.entities.stream().map(this::transformEntity).forEach(newEntities::add);
        return newEntities;
    }

    private Entity transformEntity(Entity entity)
    {
        String newPath = applyPathTransformer(entity.getPath());
        if (newPath == null)
        {
            throw new IllegalStateException("Could not find transformation for entity path: " + entity.getPath());
        }
        return Entity.newEntity(newPath, entity.getClassifierPath(), transformPackageableElement(newPath, entity.getContent()));
    }

    private Object transformValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof String)
        {
            String string = (String)value;
            return isValidPath(string) ? applyPathTransformer(string) : string;
        }
        if (value instanceof List)
        {
            List<?> list = (List<?>)value;
            List<Object> newList = new ArrayList<>(list.size());
            list.stream().map(this::transformValue).forEach(newList::add);
            return newList;
        }
        if (value instanceof Map)
        {
            Map<?, ?> map = (Map<?, ?>)value;
            if (isPackageableElement(map))
            {
                return transformPackageableElement(map);
            }
            Map<Object, Object> newMap = new HashMap<>(map.size());
            map.forEach((k, v) -> newMap.put(k, transformValue(v)));
            return newMap;
        }
        return value;
    }

    private boolean isPackageableElement(Map<?, ?> map)
    {
        return map.containsKey(PACKAGE_KEY) && map.containsKey(NAME_KEY);
    }

    private Map<?, ?> transformPackageableElement(Map<?, ?> packageableElement)
    {
        String oldPath = packageableElement.get(PACKAGE_KEY) + "::" + packageableElement.get(NAME_KEY);
        String newPath = applyPathTransformer(oldPath);
        if (newPath == null)
        {
            throw new IllegalStateException("Could not find transformation for entity path: " + oldPath);
        }
        return transformPackageableElement(newPath, packageableElement);
    }

    private <K> Map<K, ?> transformPackageableElement(String newPath, Map<K, ?> packageableElement)
    {
        int lastColon = newPath.lastIndexOf(':');
        String newPackage = (lastColon == -1) ? "::" : newPath.substring(0, lastColon - 1);
        String newName = (lastColon == -1) ? newPath : newPath.substring(lastColon + 1);

        Map<K, Object> transformed = new HashMap<>(packageableElement.size());
        packageableElement.forEach((k, v) ->
        {
            Object newValue = PACKAGE_KEY.equals(k) ? newPackage : (NAME_KEY.equals(k) ? newName : transformValue(v));
            transformed.put(k, newValue);
        });
        return transformed;
    }

    private String applyPathTransformer(String path)
    {
        String transformedPath = this.pathTransformer.apply(path);
        if (!Objects.equals(path, transformedPath) && !isValidPath(transformedPath))
        {
            StringBuilder builder = new StringBuilder("Invalid transformation for \"").append(path).append("\": ");
            if (transformedPath == null)
            {
                builder.append((String)null);
            }
            else
            {
                builder.append('"').append(transformedPath).append('"');
            }
            throw new RuntimeException(builder.toString());
        }
        return transformedPath;
    }

    static EntityPathTransformer newTransformer(Function<? super String, ? extends String> pathTransformer)
    {
        if (pathTransformer == null)
        {
            throw new IllegalArgumentException("path transformer function may not be null");
        }
        return new EntityPathTransformer(pathTransformer);
    }

    private static boolean isValidPath(String path)
    {
        return (path != null) && !path.startsWith("meta::") && PACKAGE_PATH_PATTERN.matcher(path).matches();
    }
}
