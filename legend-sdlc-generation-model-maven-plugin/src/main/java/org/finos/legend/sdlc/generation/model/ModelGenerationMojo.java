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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mojo(name = "generate-model-generations", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ModelGenerationMojo extends AbstractMojo
{
    private static final String GENERATION_SPECIFICATION_CLASSIFIER_PATH = "meta::pure::generation::metamodel::GenerationSpecification";

    @Parameter
    private GenerationSpecificationFilter inclusions;

    @Parameter
    private GenerationSpecificationFilter exclusions;

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
        PureModelBuilder.PureModelWithContextData pureModelWithContextData = pureModelBuilder.build();
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        PureModel pureModel = pureModelWithContextData.getPureModel();
        long modelEnd = System.nanoTime();
        getLog().info(String.format("Finished loading model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));
        // Checks/Filters on generation specifications
        Map<String, GenerationSpecification> generationSpecificationMap = Iterate.groupByUniqueKey(pureModelContextData.getElementsOfType(GenerationSpecification.class), PackageableElement::getPath);
        filterGenerationSpecsByIncludes(generationSpecificationMap);
        filterGenerationSpecsByExcludes(generationSpecificationMap);
        if (generationSpecificationMap.isEmpty())
        {
            getLog().info("No generation specification found, nothing to generate");
            return;
        }
        if (generationSpecificationMap.size() > 1)
        {
            throw new MojoExecutionException("Only one generation specification allowed, found: " + generationSpecificationMap.size());
        }
        try
        {
            // Start generating
            GenerationSpecification generationSpecification = generationSpecificationMap.values().iterator().next();
            ModelGenerationFactory modelGenerationFactory = ModelGenerationFactory.newFactory(generationSpecification, pureModelContextData, pureModel);
            PureModelContextData fullGeneratedModel = modelGenerationFactory.generate();
            serializePureModelContextData(fullGeneratedModel);
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

    private void filterGenerationSpecsByIncludes(Map<String, GenerationSpecification> generationSpecsByPath) throws MojoExecutionException
    {
        if (this.inclusions != null)
        {
            ResolvedGenerationSpecificationFilter filter;
            try
            {
                filter = resolveGenerationSpecFilter(this.inclusions);
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving included GenerationSpecifications", e);
            }
            generationSpecsByPath.keySet().removeIf(filter.negate());
            if ((filter.generationSpecPaths != null) && Iterate.anySatisfy(filter.generationSpecPaths, p -> !generationSpecsByPath.containsKey(p)))
            {
                throw new MojoExecutionException(LazyIterate.reject(filter.generationSpecPaths, generationSpecsByPath::containsKey).makeString("Could not find included GenerationSpecifications: ", ", ", ""));
            }
        }
    }

    private void filterGenerationSpecsByExcludes(Map<String, GenerationSpecification> generationSpecsByPath) throws MojoExecutionException
    {
        if ((this.exclusions != null) && !generationSpecsByPath.isEmpty())
        {
            ResolvedGenerationSpecificationFilter filter;
            try
            {
                filter = resolveGenerationSpecFilter(this.exclusions);
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving excluded GenerationSpecifications", e);
            }
            generationSpecsByPath.keySet().removeIf(filter);
        }
    }

    private static ResolvedGenerationSpecificationFilter resolveGenerationSpecFilter(GenerationSpecificationFilter generationSpec) throws Exception
    {
        Set<String> generationSpecPaths;
        if (generationSpec.directories != null)
        {
            try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(generationSpec.directories))
            {
                generationSpecPaths = directoriesLoader.getAllEntities()
                        .filter(e -> GENERATION_SPECIFICATION_CLASSIFIER_PATH.equals(e.getClassifierPath()))
                        .map(Entity::getPath)
                        .collect(Collectors.toCollection(Sets.mutable::empty));
            }
            if (generationSpec.paths != null)
            {
                generationSpecPaths.addAll(generationSpec.paths);
            }
        }
        else
        {
            generationSpecPaths = generationSpec.paths;
        }

        return new ResolvedGenerationSpecificationFilter(generationSpecPaths, generationSpec.packages);
    }

    public static class GenerationSpecificationFilter
    {
        public File[] directories;
        public Set<String> paths;
        public Set<String> packages;
    }

    private static class ResolvedGenerationSpecificationFilter implements Predicate<String>
    {
        private final Set<String> generationSpecPaths;
        private final ListIterable<String> packages;

        private ResolvedGenerationSpecificationFilter(Set<String> generationSpecPaths, Set<String> packages)
        {
            this.generationSpecPaths = generationSpecPaths;
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
        public boolean test(String generationSpecPath)
        {
            if (this.generationSpecPaths == null)
            {
                return (this.packages == null) || inSomePackage(generationSpecPath);
            }

            if (this.generationSpecPaths.contains(generationSpecPath))
            {
                return true;
            }

            return (this.packages != null) && inSomePackage(generationSpecPath);
        }

        private boolean inSomePackage(String path)
        {
            return this.packages.anySatisfy(path::startsWith);
        }
    }
}
