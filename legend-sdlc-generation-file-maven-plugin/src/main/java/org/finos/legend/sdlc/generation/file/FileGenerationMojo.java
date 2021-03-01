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

package org.finos.legend.sdlc.generation.file;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "generate-file-generations", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class FileGenerationMojo extends AbstractMojo
{
    public static final String PATH_SEPARATOR = "::";

    @Parameter(required = true)
    private IncludedElementsSpecification inclusions;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException
    {
        long generateStart = System.nanoTime();
        if (this.inclusions != null)
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
        getLog().info("Compiling model");
        PureModelBuilder.PureModelWithContextData pureModelWithContextData = pureModelBuilder.build();
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        PureModel pureModel = pureModelWithContextData.getPureModel();
        long modelEnd = System.nanoTime();
        getLog().info(String.format("Finished loading and compiling model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));
        Map<String, GenerationSpecification> generationSpecificationMap = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), GenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath, Maps.mutable.empty());
        if (resolvedIncludedGenerationElement != null)
        {
            generationSpecificationMap.keySet().removeIf(path -> resolvedIncludedGenerationElement.notMatches(path, GenerationSpecification.class));
        }
        // Checks on generation specifications
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
            validateGenerationSpecification(generationSpecification, resolvedIncludedGenerationElement);
            getLog().info(String.format("Start generating file generations for generation specification '%s', %,d file generations found", generationSpecification.getPath(), generationSpecification.fileGenerations.size()));
            FileGenerationFactory fileGenerationFactory = FileGenerationFactory.newFactory(generationSpecification, pureModelContextData, pureModel);
            MutableMap<FileGenerationSpecification, List<GenerationOutput>> outputs = fileGenerationFactory.generateFiles();
            this.serializeOutput(outputs);
            getLog().info(String.format("Done (%.9fs)", (System.nanoTime() - generateStart) / 1_000_000_000.0));
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error generating files: " + e.getMessage(), e);
        }
    }

    private void validateGenerationSpecification(GenerationSpecification generationSpecification, ResolvedIncludedGenerationElement resolvedIncludedGenerationElement)
    {
        for (PackageableElementPointer fileGenerationPointer : generationSpecification.fileGenerations)
        {
            String fullPath = fileGenerationPointer.path;
            if ((resolvedIncludedGenerationElement != null) && resolvedIncludedGenerationElement.notMatches(fullPath, FileGenerationSpecification.class))
            {
                throw new RuntimeException("File Generation '" + fullPath + "' not in current project");
            }
        }
    }

    protected void serializeOutput(MutableMap<FileGenerationSpecification, List<GenerationOutput>> generationGenerationOutputMap) throws IOException
    {
        long serializeStart = System.nanoTime();
        getLog().info("Start serializing file generations");
        Path outputDirPath = this.outputDirectory.toPath();
        Pattern pkgSepPattern = Pattern.compile(PATH_SEPARATOR, Pattern.LITERAL);
        String replacement = Matcher.quoteReplacement(outputDirPath.getFileSystem().getSeparator());
        for (Map.Entry<FileGenerationSpecification, List<GenerationOutput>> fileOutputPair : generationGenerationOutputMap.entrySet())
        {
            FileGenerationSpecification fileGenerationSpecification = fileOutputPair.getKey();
            List<GenerationOutput> generationOutputs = fileOutputPair.getValue();
            String generationOutPath = fileGenerationSpecification.generationOutputPath;
            String rootFolder = (generationOutPath != null && !generationOutPath.isEmpty()) ? generationOutPath : fileGenerationSpecification.getPath().replaceAll("::", "_");
            getLog().info(String.format("Serializing %,d files for '%s'", generationOutputs.size(), fileGenerationSpecification.getPath()));
            for (GenerationOutput output : generationOutputs)
            {
                String fileName = rootFolder + '/' + output.getFileName();
                String resolver = pkgSepPattern.matcher(fileName).replaceAll(replacement);
                Path entityFilePath = outputDirPath.resolve(resolver);
                Files.createDirectories(entityFilePath.getParent());
                try (OutputStream stream = Files.newOutputStream(entityFilePath))
                {
                    stream.write(output.extractFileContent().getBytes(StandardCharsets.UTF_8));
                }
            }
            getLog().info("Done serializing files for'" + fileGenerationSpecification.getPath() + "'");
        }
        getLog().info(String.format("Done serializing %,d file generations' output to %s (%.9fs)", generationGenerationOutputMap.size(), this.outputDirectory, (System.nanoTime() - serializeStart) / 1_000_000_000.0));
    }


    public static class IncludedElementsSpecification
    {
        File[] directories;
        List<Entity> includedGenerationEntities;
    }

    public static class ResolvedIncludedGenerationElement
    {
        Map<String, PackageableElement> allIncludedElements;

        public ResolvedIncludedGenerationElement(Map<String, PackageableElement> allIncludedElements)
        {
            this.allIncludedElements = allIncludedElements;
        }

        boolean matches(String fullPath, Class<? extends PackageableElement> elementClass)
        {
            PackageableElement packageableElement = allIncludedElements.get(fullPath);
            if (packageableElement == null)
            {
                return false;
            }
            return packageableElement.getClass().equals(elementClass);
        }

        boolean notMatches(String fullPath, Class<? extends PackageableElement> elementClass)
        {
            return !matches(fullPath, elementClass);
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
}
