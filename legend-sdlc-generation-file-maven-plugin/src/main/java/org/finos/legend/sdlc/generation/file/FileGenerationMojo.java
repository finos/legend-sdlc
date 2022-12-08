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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.extension.ArtifactGenerationExtension;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.artifact.ArtifactGenerationFactory;
import org.finos.legend.sdlc.generation.artifact.ArtifactGenerationResult;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mojo(name = "generate-file-generations", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class FileGenerationMojo extends AbstractMojo
{

    @Parameter
    private PackageableElementFilter inclusions;

    @Parameter
    private PackageableElementFilter exclusions;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException
    {
        long generateStart = System.nanoTime();
        if (this.inclusions != null)
        {
            getLog().info("include generation specification paths: " + this.inclusions.paths);
            getLog().info("include generation specification packages: " + this.inclusions.packages);
            getLog().info("include generation specification directories: " + Arrays.toString(this.inclusions.directories));
        }
        if (this.exclusions != null)
        {
            getLog().info("exclude generation specification paths: " + this.exclusions.paths);
            getLog().info("exclude generation specification packages: " + this.exclusions.packages);
            getLog().info("exclude generation specification directories: " + Arrays.toString(this.exclusions.directories));
        }
        getLog().info("Output directory: " + this.outputDirectory);

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

        Set<String> fileOutputPaths = Sets.mutable.empty();
        // Generation Specification
        MutableMap<String, GenerationSpecification> generationSpecificationMap = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), GenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath, Maps.mutable.empty());
        filterPackageableElementsByIncludes(generationSpecificationMap);
        filterPackageableElementsByExcludes(generationSpecificationMap);
        if (generationSpecificationMap.size() > 1)
        {
            throw new MojoExecutionException(Iterate.toSortedList(generationSpecificationMap.keySet()).makeString("Only 1 generation specification allowed, found " + generationSpecificationMap.size() + ": ", ", ", ""));
        }
        if (generationSpecificationMap.size() == 1)
        {
            try
            {
                GenerationSpecification generationSpecification = generationSpecificationMap.valuesView().getAny();
                getLog().info(String.format("Start generating file generations for generation specification '%s', %,d file generations found", generationSpecification.getPath(), generationSpecification.fileGenerations.size()));
                FileGenerationFactory fileGenerationFactory = FileGenerationFactory.newFactory(generationSpecification, pureModelContextData, pureModel);
                MutableMap<FileGenerationSpecification, List<GenerationOutput>> outputs = fileGenerationFactory.generateFiles();
                serializeOutput(outputs, fileOutputPaths);
                getLog().info(String.format("Done (%.9fs)", (System.nanoTime() - generateStart) / 1_000_000_000.0));
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error generating files: " + e.getMessage(), e);
            }
        }
        else
        {
            getLog().info("No generation specification found.");
        }

        // Artifact Generations
        Map<String, PackageableElement> elementsMap = LazyIterate.adapt(pureModelContextData.getElements()).groupByUniqueKey(PackageableElement::getPath, Maps.mutable.empty());
        filterPackageableElementsByIncludes(elementsMap);
        filterPackageableElementsByExcludes(elementsMap);
        try
        {
            List<PackageableElement> elements = Lists.mutable.withAll(elementsMap.values());
            ArtifactGenerationFactory factory = ArtifactGenerationFactory.newFactory(pureModel, pureModelContextData, elements);
            MutableMap<ArtifactGenerationExtension, List<ArtifactGenerationResult>> results = factory.generate();
            serializeArtifacts(results, fileOutputPaths);
            getLog().info(String.format("Done (%.9fs)", (System.nanoTime() - generateStart) / 1_000_000_000.0));
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error generating files: " + e.getMessage(), e);
        }
    }

    protected void serializeOutput(MutableMap<FileGenerationSpecification, List<GenerationOutput>> generationGenerationOutputMap, Set<String> fileOutputPaths) throws MojoExecutionException, IOException
    {
        long serializeStart = System.nanoTime();
        getLog().info("Start serializing file generations");
        Path outputDirPath = this.outputDirectory.toPath();
        String fileSeparator = outputDirPath.getFileSystem().getSeparator();
        Pattern pkgSepPattern = Pattern.compile("::", Pattern.LITERAL);
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
                String fileName = rootFolder + fileSeparator + output.getFileName();
                if (!fileOutputPaths.add(fileName))
                {
                    throw new MojoExecutionException("Duplicate file paths found when serializing file generations outputs : '" + fileName + "'");
                }
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


    protected void serializeArtifacts(MutableMap<ArtifactGenerationExtension, List<ArtifactGenerationResult>> results, Set<String> fileOutputPaths) throws IOException, MojoExecutionException
    {
        long serializeStart = System.nanoTime();
        getLog().info("Start serializing artifact extension generations");
        Path outputDirPath = this.outputDirectory.toPath();
        String fileSeparator = outputDirPath.getFileSystem().getSeparator();
        Pattern pkgSepPattern = Pattern.compile("::", Pattern.LITERAL);
        String replacement = Matcher.quoteReplacement(fileSeparator);
        for (Map.Entry<ArtifactGenerationExtension, List<ArtifactGenerationResult>> resultByExtension : results.entrySet())
        {
            ArtifactGenerationExtension extension = resultByExtension.getKey();
            List<ArtifactGenerationResult> extensionResults = resultByExtension.getValue();
            for (ArtifactGenerationResult result : extensionResults)
            {
                org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement generator = result.getElement();
                String elementFolder = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(generator, fileSeparator);
                List<GenerationOutput> generatorOutputs = result.getResults();
                String rootExtensionFolder = extension.getKey();
                for (GenerationOutput output : generatorOutputs)
                {
                    String fileName = elementFolder + fileSeparator + rootExtensionFolder + fileSeparator + output.getFileName();
                    if (!fileOutputPaths.add(fileName))
                    {
                        throw new MojoExecutionException("Duplicate file path found when serializing artifact generation extension  '" + extension.getClass() + "' output: '" + fileName + "'");
                    }
                    String resolver = pkgSepPattern.matcher(fileName).replaceAll(replacement);
                    Path entityFilePath = outputDirPath.resolve(resolver);
                    Files.createDirectories(entityFilePath.getParent());
                    try (OutputStream stream = Files.newOutputStream(entityFilePath))
                    {
                        stream.write(output.extractFileContent().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            getLog().info("Done serializing files for extension: '" + extension.getClass() + "'");
        }
        getLog().info(String.format("Done serializing %,d artifact generation extension results to %s (%.9fs)", results.size(), this.outputDirectory, (System.nanoTime() - serializeStart) / 1_000_000_000.0));
    }

    private <T extends PackageableElement> void filterPackageableElementsByIncludes(Map<String, T> elementsByPath) throws MojoExecutionException
    {
        if (this.inclusions != null)
        {
            ResolvedPackageableElementFilter filter;
            try
            {
                filter = resolvePackageableElementFilter(this.inclusions, elementsByPath.keySet());
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving included PackageableElements", e);
            }
            elementsByPath.keySet().removeIf(filter.negate());
        }
    }

    private <T extends PackageableElement> void filterPackageableElementsByExcludes(Map<String, T> elementsByPath) throws MojoExecutionException
    {
        if ((this.exclusions != null) && !elementsByPath.isEmpty())
        {
            ResolvedPackageableElementFilter filter;
            try
            {
                filter = resolvePackageableElementFilter(this.exclusions, elementsByPath.keySet());
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving excluded PackageableElements", e);
            }
            elementsByPath.keySet().removeIf(filter);
        }
    }

    private static ResolvedPackageableElementFilter resolvePackageableElementFilter(PackageableElementFilter elementFilter, Set<String> elementsByPath) throws Exception
    {
        Set<String> resolvedElementsByPath;
        if (elementFilter.directories != null)
        {
            try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(elementFilter.directories))
            {

                resolvedElementsByPath = directoriesLoader.getAllEntities().filter(e -> elementsByPath.contains(e.getPath())).map(Entity::getPath).collect(Collectors.toCollection(Sets.mutable::empty));
            }
            if (elementFilter.paths != null)
            {
                resolvedElementsByPath.addAll(elementFilter.paths);
            }
        }
        else
        {
            resolvedElementsByPath = elementFilter.paths;
        }
        return new ResolvedPackageableElementFilter(resolvedElementsByPath, elementFilter.packages);
    }


    public static class PackageableElementFilter
    {

        public File[] directories;
        public Set<String> paths;
        public Set<String> packages;
    }

    private static class ResolvedPackageableElementFilter implements Predicate<String>
    {

        private final Set<String> elementPaths;
        private final ListIterable<String> packages;

        private ResolvedPackageableElementFilter(Set<String> elementPaths, Set<String> packages)
        {
            this.elementPaths = elementPaths;
            if (packages == null)
            {
                this.packages = null;
            }
            else
            {
                this.packages = Iterate.collectWith(packages, String::concat, "::", Lists.mutable.ofInitialCapacity(packages.size()))
                        .sortThis(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
            }
        }

        @Override
        public boolean test(String elementPath)
        {
            if (this.elementPaths == null)
            {
                return (this.packages == null) || inSomePackage(elementPath);
            }

            if (this.elementPaths.contains(elementPath))
            {
                return true;
            }

            return (this.packages != null) && inSomePackage(elementPath);
        }

        private boolean inSomePackage(String path)
        {
            return this.packages.anySatisfy(path::startsWith);
        }
    }
}
