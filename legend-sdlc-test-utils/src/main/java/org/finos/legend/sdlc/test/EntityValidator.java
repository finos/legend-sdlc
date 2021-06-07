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

package org.finos.legend.sdlc.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class EntityValidator
{
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^\\w++$");
    private static final Pattern VALID_PACKAGE_PATTERN = Pattern.compile("^\\w++(::\\w++)*+$");
    private static final Pattern VALID_CLASSIFIER_PATH_PATTERN = Pattern.compile("^meta(::\\w++)++$");

    private static final EntitySerializer ENTITY_SERIALIZER = EntitySerializers.getDefaultJsonSerializer();
    private static final String ENTITY_DIRECTORY = "entities";
    private static final String ENTITY_EXTENSION = "." + ENTITY_SERIALIZER.getDefaultFileExtension();

    public static ValidationReport validateEntities(Path directory) throws IOException
    {
        return validateEntities(Collections.singletonList(directory));
    }

    public static ValidationReport validateEntities(Path... directories) throws IOException
    {
        return validateEntities(Arrays.asList(directories));
    }

    public static ValidationReport validateEntities(Iterable<? extends Path> directories) throws IOException
    {
        List<String> violations = new ArrayList<>();
        int entityCount = forEachEntity(directories, EntityValidator::validateEntity, violations::add);
        return new ValidationReport(entityCount, violations);
    }

    public static String formatViolationMessage(List<String> violations)
    {
        switch (violations.size())
        {
            case 0:
            {
                return "There are no violations";
            }
            case 1:
            {
                return violations.get(0);
            }
            default:
            {
                StringBuilder builder = new StringBuilder(violations.size() * 64);
                builder.append("There are ");
                try (Formatter formatter = new Formatter(builder))
                {
                    formatter.format("%,d", violations.size());
                }
                builder.append(" violations:");
                violations.forEach(v -> builder.append("\n\t").append(v));
                return builder.toString();
            }
        }
    }

    private static void validateEntity(Entity entity, Consumer<String> violationConsumer)
    {
        validateEntityPath(entity, violationConsumer);
        validateClassifierPath(entity, violationConsumer);
    }

    private static void validateEntityPath(Entity entity, Consumer<String> violationConsumer)
    {
        boolean packageAndNameValid = true;

        Map<String, ?> content = entity.getContent();
        Object pkg = content.get("package");
        if (pkg == null)
        {
            violationConsumer.accept("invalid package: null");
            packageAndNameValid = false;
        }
        else if (!(pkg instanceof String))
        {
            violationConsumer.accept("invalid package: " + pkg + " (instance of " + pkg.getClass() + ")");
            packageAndNameValid = false;
        }
        else if (!isValidEntityPackage((String)pkg))
        {
            violationConsumer.accept("invalid package: \"" + pkg + "\"");
            packageAndNameValid = false;
        }

        Object name = content.get("name");
        if (name == null)
        {
            violationConsumer.accept("invalid name: null");
            packageAndNameValid = false;
        }
        else if (!(name instanceof String))
        {
            violationConsumer.accept("invalid name: " + name + " (instance of " + name.getClass() + ")");
            packageAndNameValid = false;
        }
        else if (!isValidEntityName((String)name))
        {
            violationConsumer.accept("invalid name: \"" + name + "\"");
            packageAndNameValid = false;
        }

        if (packageAndNameValid)
        {
            String expectedEntityPath = pkg + "::" + name;
            if (!expectedEntityPath.equals(entity.getPath()))
            {
                violationConsumer.accept("mismatch between entity path (" + entity.getPath() + ") and package (" + pkg + ") and name (" + name + ") properties");
            }
        }
    }

    private static void validateClassifierPath(Entity entity, Consumer<String> violationConsumer)
    {
        String classifierPath = entity.getClassifierPath();
        if (classifierPath == null)
        {
            violationConsumer.accept("invalid classifier path: null");
        }
        else if (!isValidClassifierPath(classifierPath))
        {
            violationConsumer.accept("invalid classifier path: \"" + classifierPath + "\"");
        }
    }

    private static boolean isValidEntityPackage(String pkg)
    {
        return !"meta".equals(pkg) && !pkg.startsWith("meta::") && VALID_PACKAGE_PATTERN.matcher(pkg).matches();
    }

    private static boolean isValidEntityName(String name)
    {
        return VALID_NAME_PATTERN.matcher(name).matches();
    }

    private static boolean isValidClassifierPath(String classifierPath)
    {
        return VALID_CLASSIFIER_PATH_PATTERN.matcher(classifierPath).matches();
    }

    private static int forEachEntity(Iterable<? extends Path> directories, BiConsumer<? super Entity, Consumer<String>> validator, Consumer<String> violationConsumer) throws IOException
    {
        int violationCount = 0;
        for (Path directory : directories)
        {
            violationCount += forEachEntity(directory, validator, violationConsumer);
        }
        return violationCount;
    }

    private static int forEachEntity(Path directory, BiConsumer<? super Entity, Consumer<String>> validator, Consumer<String> violationConsumer) throws IOException
    {
        Path entitiesDirectory = directory.resolve(ENTITY_DIRECTORY);
        return Files.isDirectory(entitiesDirectory) ? forEachEntityInDirectory(entitiesDirectory, entitiesDirectory, validator, violationConsumer) : 0;
    }

    private static int forEachEntityInDirectory(Path directory, Path root, BiConsumer<? super Entity, Consumer<String>> validator, Consumer<String> violationConsumer) throws IOException
    {
        int count = 0;
        List<Path> subdirectories = new ArrayList<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory))
        {
            for (Path entry : dirStream)
            {
                if (Files.isDirectory(entry))
                {
                    subdirectories.add(entry);
                }
                else if (entry.toString().endsWith(ENTITY_EXTENSION))
                {
                    Path relativePath = root.relativize(entry);
                    String relativePathString = relativePath.toString();
                    String entityPath = relativePathString.substring(0, relativePathString.length() - ENTITY_EXTENSION.length()).replace(relativePath.getFileSystem().getSeparator(), "::");
                    String violationPrefix = "Entity " + entityPath + " - ";
                    Entity entity;
                    try (InputStream stream = Files.newInputStream(entry))
                    {
                        entity = ENTITY_SERIALIZER.deserialize(stream);
                    }
                    catch (JsonProcessingException e)
                    {
                        StringBuilder builder = new StringBuilder(violationPrefix).append(" error deserializing from ").append(entry.toAbsolutePath());
                        String message = e.getMessage();
                        if (message != null)
                        {
                            builder.append(": ").append(message);
                        }
                        violationConsumer.accept(builder.toString());
                        continue;
                    }
                    validator.accept(entity, v -> violationConsumer.accept(violationPrefix + v));
                    count++;
                }
            }
        }
        if (!subdirectories.isEmpty())
        {
            for (Path subdirectory : subdirectories)
            {
                count += forEachEntityInDirectory(subdirectory, root, validator, violationConsumer);
            }
        }
        return count;
    }

    public static class ValidationReport
    {
        private final int entityCount;
        private final List<String> violationMessages;

        private ValidationReport(int entityCount, List<String> violationMessages)
        {
            this.entityCount = entityCount;
            this.violationMessages = (violationMessages == null) ? Collections.emptyList() : Collections.unmodifiableList(violationMessages);
        }

        public int getEntityCount()
        {
            return this.entityCount;
        }

        public boolean hasViolations()
        {
            return !this.violationMessages.isEmpty();
        }

        public List<String> getViolationMessages()
        {
            return this.violationMessages;
        }

        public String getFormattedViolationMessage()
        {
            return formatViolationMessage(this.violationMessages);
        }
    }
}
