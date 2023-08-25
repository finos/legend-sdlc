// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;

public class JUnitTestGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JUnitTestGenerator.class);

    private final Set<String> testableClassifiers = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap().keySet();
    private final EntityToPureConverter converter = new EntityToPureConverter();
    private final String rootPackage;

    private JUnitTestGenerator(String rootPackage)
    {
        if ((rootPackage != null) && !SourceVersion.isName(rootPackage))
        {
            throw new IllegalArgumentException("Invalid root package: \"" + rootPackage + "\"");
        }
        this.rootPackage = rootPackage;
    }

    public List<Path> writeTestClasses(Path outputDirectory, Stream<? extends Entity> entities)
    {
        List<Path> paths = Lists.mutable.empty();
        entities.forEach(e -> paths.addAll(writeTestClasses(outputDirectory, e)));
        return paths;
    }

    public List<Path> writeTestClasses(Path outputDirectory, Entity entity)
    {
        try
        {
            String separator = outputDirectory.getFileSystem().getSeparator();
            List<GeneratedJavaCode> generatedClasses = generateTestClasses(entity);
            List<Path> paths = Lists.mutable.ofInitialCapacity(generatedClasses.size());
            generatedClasses.forEach(javaCode ->
            {
                Path filePath = outputDirectory.resolve(javaCode.getClassName().replace(".", separator) + JavaFileObject.Kind.SOURCE.extension);
                paths.add(filePath);
                LOGGER.debug("Writing {} to {} for {}", javaCode.getClassName(), filePath, entity.getPath());
                try
                {
                    Files.createDirectories(filePath.getParent());
                    try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                    {
                        writer.write(javaCode.getText());
                    }
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException("Error writing class " + javaCode.getClassName() + " to " + filePath, e);
                }
            });
            return paths;
        }
        catch (Exception e)
        {
            LOGGER.error("Error generating tests for {}", entity.getPath(), e);
            throw e;
        }
    }

    List<GeneratedJavaCode> generateTestClasses(Entity entity)
    {
        if (!this.testableClassifiers.contains(entity.getClassifierPath()))
        {
            return Collections.emptyList();
        }

        LOGGER.debug("Generating tests for {}", entity.getPath());
        PackageableElement protocolElement = this.converter.fromEntity(entity);
        List<GeneratedJavaCode> generated = generateTestClasses(protocolElement);
        LOGGER.debug("Generated {} test classes for {}", generated.size(), entity.getPath());
        return generated;
    }

    private List<GeneratedJavaCode> generateTestClasses(PackageableElement element)
    {
        if (element instanceof Mapping)
        {
            return Collections.singletonList(new MappingTestGenerator(this.rootPackage).withMapping((Mapping) element).generate());
        }
        if (element instanceof Service)
        {
            return Collections.singletonList(new ServiceTestGenerator(this.rootPackage).withService((Service) element).generate());
        }
        return Collections.singletonList(new TestableTestGenerator(this.rootPackage).withTestable(element).generate());
    }

    // Factory

    static JUnitTestGenerator newGenerator(String rootPackage)
    {
        return new JUnitTestGenerator(rootPackage);
    }
}
