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

package org.finos.legend.sdlc.generation.model;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationTreeNode;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.protocol.pure.v1.PureToEntityConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "generate-model-generations", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ModelGenerationMojo extends AbstractMojo
{
    @Parameter(required = true)
    private IncludedElementsSpecification inclusions;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException
    {
        long generateStart = System.nanoTime();
        if (inclusions != null)
        {
            getLog().info("Included elements directories: " + Arrays.toString(this.inclusions.directories));
        }
        getLog().info("Output directory: " + this.outputDirectory);

        getLog().info("Start loading included elements");
        ResolvedIncludedGenerationElement resolvedIncludedGenerationElement;
        if (this.inclusions == null)
        {
            resolvedIncludedGenerationElement = null;
        }
        else
        {
            try
            {
                resolvedIncludedGenerationElement = resolveIncludedGenerationSpec(this.inclusions);
                getLog().info("Resolved included elements: " + resolvedIncludedGenerationElement.allIncludedElements.size());
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error loading entities from model", e);
            }
        }
        // Load Model
        long modelStart = System.nanoTime();
        getLog().info("Start loading model");
        PureModelBuilder pureModelBuilder = PureModelBuilder.newBuilder();
        try (EntityLoader allEntities = EntityLoader.newEntityLoader(Thread.currentThread().getContextClassLoader()))
        {
            pureModelBuilder.addEntitiesIfPossible(allEntities.getAllEntities());
            int entityCount = pureModelBuilder.getElementCount();
            getLog().info("Found " + entityCount + " entities");
            if (entityCount == 0)
            {
                long modelEnd = System.nanoTime();
                getLog().info(String.format("Finished loading model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));
                getLog().info("No elements found to generate");
                return;
            }
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error loading entities from model", e);
        }
        PureModelBuilder.PureModelWithContextData pureModelWithContextData = pureModelBuilder.build();
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        PureModel pureModel = pureModelWithContextData.getPureModel();
        long modelEnd = System.nanoTime();
        getLog().info(String.format("Finished loading model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));
        // Checks/Filters on generation specifications
        Map<String, GenerationSpecification> generationSpecificationMap = Iterate.groupByUniqueKey(pureModelContextData.getElementsOfType(GenerationSpecification.class), PackageableElement::getPath);
        if (resolvedIncludedGenerationElement != null)
        {
            generationSpecificationMap.keySet().removeIf(path -> resolvedIncludedGenerationElement.notMatches(path, GenerationSpecification.class));
        }
        if (generationSpecificationMap.isEmpty())
        {
            getLog().info("No generation specification found, nothing to generate");
            return;
        }
        if (generationSpecificationMap.size() > 1)
        {
            throw new MojoExecutionException("Only one generation specification allowed per project, found: " + generationSpecificationMap.size());
        }
        try
        {
            // Start generating
            GenerationSpecification generationSpecification = generationSpecificationMap.values().iterator().next();
            validModelGenerationSpecification(generationSpecification, resolvedIncludedGenerationElement);
            ModelGenerationFactory modelGenerationFactory = ModelGenerationFactory.newFactory(generationSpecification, pureModelContextData, pureModel);
            PureModelContextData fullGeneratedModel = modelGenerationFactory.generate();
            this.serializePureModelContextData(fullGeneratedModel);
            getLog().info(String.format("Done (%.9fs)", (System.nanoTime() - generateStart) / 1_000_000_000.0));
        }
        catch (Exception e)

        {
            throw new MojoExecutionException("Error generating model generation: " + e.getMessage(), e);
        }
    }

    private void serializePureModelContextData(PureModelContextData pureModelContextData) throws Exception
    {
        PureToEntityConverter converter = new PureToEntityConverter();
        List<Entity> entities = ListIterate.collect(pureModelContextData.getAllElements(), converter::toEntity);
        this.serializeEntities(entities);
    }

    private void serializeEntities(List<Entity> entities) throws IOException
    {
        long serializeStart = System.nanoTime();
        getLog().info(String.format("Serializing %,d entities to %s", entities.size(), this.outputDirectory));
        Path outputDirPath = this.outputDirectory.toPath();
        Path entitiesDir = outputDirPath.resolve("entities");
        Pattern pkgSepPattern = Pattern.compile("::", Pattern.LITERAL);
        String replacement = Matcher.quoteReplacement(outputDirPath.getFileSystem().getSeparator());
        EntitySerializer entitySerializer = EntitySerializers.getDefaultJsonSerializer();
        for (Entity entity : entities)
        {
            Path entityFilePath = entitiesDir.resolve(pkgSepPattern.matcher(entity.getPath()).replaceAll(replacement) + "." + entitySerializer.getDefaultFileExtension());
            Files.createDirectories(entityFilePath.getParent());
            try (OutputStream stream = Files.newOutputStream(entityFilePath))
            {
                entitySerializer.serialize(entity, stream);
            }
        }
        getLog().info(String.format("Done serializing %,d entities to %s (%.9fs)", entities.size(), this.outputDirectory, (System.nanoTime() - serializeStart) / 1_000_000_000.0));
    }

    public static class IncludedElementsSpecification
    {
        File[] directories;
        List<Entity> includedGenerationEntities;

        public List<Entity> getIncludedGenerationEntities()
        {
            return includedGenerationEntities;
        }

        public File[] getDirectories()
        {
            return directories;
        }

        public void setDirectories(File[] directories)
        {
            this.directories = directories;
        }

        public void setIncludedGenerationEntities(List<Entity> includedGenerationEntities)
        {
            this.includedGenerationEntities = includedGenerationEntities;
        }
    }


    public static class ResolvedIncludedGenerationElement
    {
        private final Map<String, PackageableElement> allIncludedElements;

        ResolvedIncludedGenerationElement(Map<String, PackageableElement> allIncludedElements)
        {
            this.allIncludedElements = allIncludedElements;
        }

        boolean matches(String fullPath, Class<? extends PackageableElement> elementClass)
        {
            return elementClass.isInstance(this.allIncludedElements.get(fullPath));
        }

        boolean notMatches(String fullPath, Class<? extends PackageableElement> elementClass)
        {
            return !matches(fullPath, elementClass);
        }

        boolean matches(String fullPath)
        {
            return this.allIncludedElements.get(fullPath) != null;
        }

        boolean notMatches(String fullPath)
        {
            return !matches(fullPath);
        }

        public Map<String, PackageableElement> getAllIncludedElements()
        {
            return allIncludedElements;
        }
    }

    private static ResolvedIncludedGenerationElement resolveIncludedGenerationSpec(IncludedElementsSpecification includedGenerationElementSpecification) throws Exception
    {
        Map<String, PackageableElement> allIncludedElements = Maps.mutable.empty();
        EntityToPureConverter converter = new EntityToPureConverter();
        if (includedGenerationElementSpecification.includedGenerationEntities != null)
        {
            includedGenerationElementSpecification.includedGenerationEntities.stream()
                    .map(converter::fromEntityIfPossible)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(e -> allIncludedElements.put(e.getPath(), e));
        }
        if (includedGenerationElementSpecification.directories != null)
        {
            try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(includedGenerationElementSpecification.directories))
            {
                directoriesLoader.getAllEntities()
                        .map(converter::fromEntityIfPossible)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(e -> allIncludedElements.put(e.getPath(), e));
            }
        }
        return new ResolvedIncludedGenerationElement(allIncludedElements);
    }


    private void validModelGenerationSpecification(GenerationSpecification specification, ResolvedIncludedGenerationElement resolvedIncludedGenerationElement)
    {
        for (GenerationTreeNode nodes : specification.generationNodes)
        {
            String fullPath = nodes.generationElement;
            if (resolvedIncludedGenerationElement != null)
            {
                if (resolvedIncludedGenerationElement.notMatches(fullPath))
                {
                    throw new RuntimeException("Model generation element '" + fullPath + "' not in current project");
                }
            }
        }
    }

}
