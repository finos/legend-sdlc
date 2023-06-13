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

package org.finos.legend.sdlc.generation.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

@Mojo(name = "generate-service-executions", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = false)
public class ServicesGenerationMojo extends AbstractMojo
{
    @Parameter
    private ServicesSpecification inclusions;

    @Parameter
    private ServicesSpecification exclusions;

    @Parameter(defaultValue = "")
    private String packagePrefix;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File javaSourceOutputDirectory;

    @Parameter(defaultValue = "true")
    private boolean addJavaSourceOutputDirectoryAsSource;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File resourceOutputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${org.finos.legend.sdlc.generation.service.parallel}")
    private String parallel;

    @Override
    public void execute() throws MojoExecutionException
    {
        if (this.inclusions != null)
        {
            getLog().info("include service paths: " + this.inclusions.servicePaths);
            getLog().info("include service packages: " + this.inclusions.packages);
            getLog().info("include service directories: " + Arrays.toString(this.inclusions.directories));
        }
        if (this.exclusions != null)
        {
            getLog().info("exclude service paths: " + this.exclusions.servicePaths);
            getLog().info("exclude service packages: " + this.exclusions.packages);
            getLog().info("exclude service directories: " + Arrays.toString(this.exclusions.directories));
        }
        getLog().info("package prefix: " + ((this.packagePrefix == null) ? null : ('"' + this.packagePrefix + '"')));
        getLog().info("Java source output directory: " + this.javaSourceOutputDirectory);
        getLog().info("resource output directory: " + this.resourceOutputDirectory);

        if ((this.packagePrefix != null) && !SourceVersion.isName(this.packagePrefix))
        {
            throw new MojoExecutionException("Invalid package prefix: " + this.packagePrefix);
        }

        int parallelism = getParallelism();
        getLog().info("parallelism: " + parallelism);

        getLog().info("Loading model");
        long modelStart = System.nanoTime();

        PureModelBuilder pureModelBuilder = PureModelBuilder.newBuilder();
        try (EntityLoader allEntities = EntityLoader.newEntityLoader(Thread.currentThread().getContextClassLoader()))
        {
            pureModelBuilder.addEntitiesIfPossible(allEntities.getAllEntities());
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error loading entities from model", e);
        }
        int elementCount = pureModelBuilder.getElementCount();
        getLog().info("Found " + elementCount + " elements in the model");
        if (elementCount == 0)
        {
            long modelEnd = System.nanoTime();
            getLog().info(String.format("Finished loading model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));
            getLog().info("No execution artifacts to generate");
            return;
        }

        PureModelBuilder.PureModelWithContextData pureModelWithContextData;
        try
        {
            pureModelWithContextData = pureModelBuilder.build();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error building Pure model", e);
        }
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        PureModel pureModel = pureModelWithContextData.getPureModel();
        long modelEnd = System.nanoTime();
        getLog().info(String.format("Finished loading model (%.9fs)", (modelEnd - modelStart) / 1_000_000_000.0));

        long start = System.nanoTime();
        MutableMap<String, Service> servicesByPath = Maps.mutable.empty();
        pureModelContextData.getElements().forEach(e ->
        {
            if (e instanceof Service)
            {
                String path = e.getPath();
                Service old = servicesByPath.put(path, (Service) e);
                if (old != null)
                {
                    throw new RuntimeException("Multiple services for path '" + path + "'");
                }
            }
        });
        filterServicesByIncludes(servicesByPath);
        filterServicesByExcludes(servicesByPath);

        generateServices(servicesByPath, pureModel, parallelism);

        if (this.addJavaSourceOutputDirectoryAsSource)
        {
            String newSourceDirectory = this.javaSourceOutputDirectory.getAbsolutePath();
            this.project.addCompileSourceRoot(newSourceDirectory);
            getLog().info("Added source directory: " + newSourceDirectory);
        }

        long end = System.nanoTime();
        getLog().info(String.format("Finished generating execution artifacts for %d services (%.9fs)", servicesByPath.size(), (end - start) / 1_000_000_000.0));
    }

    private void filterServicesByIncludes(MutableMap<String, Service> servicesByPath) throws MojoExecutionException
    {
        if ((this.inclusions != null) && servicesByPath.notEmpty())
        {
            ResolvedServicesSpecification resolvedIncluded;
            try
            {
                resolvedIncluded = resolveServicesSpecification(this.inclusions);
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving included services", e);
            }
            servicesByPath.removeIf((path, service) -> resolvedIncluded.notMatches(path));
            if ((resolvedIncluded.servicePaths != null) && Iterate.anySatisfy(resolvedIncluded.servicePaths, p -> !servicesByPath.containsKey(p)))
            {
                throw new MojoExecutionException(Iterate.reject(resolvedIncluded.servicePaths, servicesByPath::containsKey, Lists.mutable.empty()).sortThis().makeString(", ", "Could not find included services: ", ""));
            }
        }
    }

    private void filterServicesByExcludes(MutableMap<String, Service> servicesByPath) throws MojoExecutionException
    {
        if ((this.exclusions != null) && servicesByPath.notEmpty())
        {
            ResolvedServicesSpecification resolvedExcluded;
            try
            {
                resolvedExcluded = resolveServicesSpecification(this.exclusions);
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error resolving excluded services", e);
            }
            servicesByPath.removeIf((path, service) -> resolvedExcluded.matches(path));
        }
    }

    private void generateServices(MutableMap<String, Service> servicesByPath, PureModel pureModel, int parallelism)
    {
        if (servicesByPath.isEmpty())
        {
            getLog().info("Found 0 services for generation");
            return;
        }

        if (getLog().isInfoEnabled())
        {
            getLog().info(Lists.mutable.withAll(servicesByPath.keySet()).toSortedList().makeString("Found " + servicesByPath.size() + " services for generation: ", ", ", ""));
        }

        JsonMapper jsonMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build());

        int effectiveParallelism = Math.min(parallelism, servicesByPath.size());
        ForkJoinPool pool;
        if (effectiveParallelism > 1)
        {
            getLog().info("Generating services in parallel with parallelism level " + effectiveParallelism);
            pool = createForkJoinPool(effectiveParallelism);
        }
        else
        {
            pool = null;
        }
        try
        {
            ServiceExecutionGenerator.newBuilder()
                    .withServices(servicesByPath.values())
                    .withPureModel(pureModel)
                    .withPackagePrefix(this.packagePrefix)
                    .withOutputDirectories(this.javaSourceOutputDirectory.toPath(), this.resourceOutputDirectory.toPath())
                    .withJsonMapper(jsonMapper)
                    .withPlanGeneratorExtensions(ServiceLoader.load(PlanGeneratorExtension.class))
                    .withExecutorService(pool)
                    .build()
                    .generate();
        }
        finally
        {
            if (pool != null)
            {
                pool.shutdown();
            }
        }
    }

    private int getParallelism()
    {
        int parallelism = parseParallel(this.parallel);
        if (parallelism < 1)
        {
            getLog().warn("Specified parallelism is less than 1 (" + parallelism + "), effective parallelism will be 1");
            return 1;
        }
        return parallelism;
    }

    private ForkJoinPool createForkJoinPool(int parallelism)
    {
        // We have to create a custom fork join thread worker factory to ensure the worker threads use this thread's
        // context class loader. This is why we cannot use the common pool.
        return new ForkJoinPool(
                parallelism,
                pool -> new ForkJoinWorkerThread(pool)
                {
                },
                null,
                false);
    }

    private static ResolvedServicesSpecification resolveServicesSpecification(ServicesSpecification servicesSpec) throws Exception
    {
        Set<String> servicePaths = null;
        if (servicesSpec.directories != null)
        {
            try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(servicesSpec.directories))
            {
                EntityToPureConverter converter = new EntityToPureConverter();
                servicePaths = directoriesLoader.getAllEntities()
                        .filter(e -> converter.fromEntityIfPossible(e).filter(s -> s instanceof Service).isPresent())
                        .map(Entity::getPath)
                        .collect(Collectors.toSet());
            }
        }
        if (servicesSpec.servicePaths != null)
        {
            if (servicePaths == null)
            {
                servicePaths = servicesSpec.servicePaths;
            }
            else
            {
                servicePaths.addAll(servicesSpec.servicePaths);
            }
        }

        return new ResolvedServicesSpecification(servicePaths, servicesSpec.packages);
    }

    public static class ServicesSpecification
    {
        public File[] directories;
        public Set<String> servicePaths;
        public Set<String> packages;
    }

    private static class ResolvedServicesSpecification
    {
        private final Set<String> servicePaths;
        private final RichIterable<String> packages;

        private ResolvedServicesSpecification(Set<String> servicePaths, Set<String> packages)
        {
            this.servicePaths = servicePaths;
            this.packages = (packages == null) ? null : Iterate.collect(packages, p -> p.endsWith(EntityPaths.PACKAGE_SEPARATOR) ? p : (p + EntityPaths.PACKAGE_SEPARATOR), Lists.mutable.ofInitialCapacity(packages.size()))
                    .sortThis(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        }

        boolean matches(String servicePath)
        {
            if (this.servicePaths == null)
            {
                return (this.packages == null) || inSomePackage(servicePath);
            }

            if (this.servicePaths.contains(servicePath))
            {
                return true;
            }

            return (this.packages != null) && inSomePackage(servicePath);
        }

        private boolean inSomePackage(String servicePath)
        {
            int lastSeparator = servicePath.lastIndexOf(EntityPaths.PACKAGE_SEPARATOR);
            int pkgLen = (lastSeparator == -1) ? 0 : lastSeparator + EntityPaths.PACKAGE_SEPARATOR.length();
            for (String pkg : this.packages)
            {
                if (servicePath.startsWith(pkg))
                {
                    return true;
                }
                if (pkg.length() > pkgLen)
                {
                    return false;
                }
            }
            return false;
        }

        boolean notMatches(String servicePath)
        {
            return !matches(servicePath);
        }
    }

    // package private for testing
    static int parseParallel(String parallel)
    {
        if ((parallel == null) || parallel.isEmpty())
        {
            return 1;
        }

        Pattern pattern = Pattern.compile("\\s*((?<true>true)|(?<false>false)|(?<integer>[+-]?\\d+)|(?<cpu>((?<cpux>\\d+(\\.\\d+)?)\\s*)?C(\\s*(?<cpupm>[+-])\\s*(?<cpua>\\d+))?))?\\s*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(parallel);
        if (!matcher.matches())
        {
            throw new RuntimeException("Could not parse parallel value: \"" + parallel + "\"");
        }
        if (matcher.group("true") != null)
        {
            // by default, we use the number of available processors minus 1
            return Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        }
        if (matcher.group("false") != null)
        {
            return 1;
        }
        String integer = matcher.group("integer");
        if (integer != null)
        {
            try
            {
                return Integer.parseInt(integer);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Could not parse parallel value: \"" + parallel + "\"", e);
            }
        }
        if (matcher.group("cpu") != null)
        {
            int parallelism = Runtime.getRuntime().availableProcessors();
            try
            {
                String multiplier = matcher.group("cpux");
                if (multiplier != null)
                {
                    parallelism = Math.round(Float.parseFloat(multiplier) * parallelism);
                }

                String addendum = matcher.group("cpua");
                if (addendum != null)
                {
                    int toAdd = Integer.parseInt(addendum);
                    parallelism += "-".equals(matcher.group("cpupm")) ? -toAdd : toAdd;
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("Could not parse parallel value: \"" + parallel + "\"", e);
            }
            return parallelism;
        }

        // only whitespace
        return 1;
    }
}
