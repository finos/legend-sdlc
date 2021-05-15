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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import javax.lang.model.SourceVersion;

@Mojo(name = "generate-service-executions", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
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

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File resourceOutputDirectory;

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

        ObjectMapper objectMapper = ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.CLOSE_CLOSEABLE, false);

        long start = System.nanoTime();
        Map<String, Service> servicesByPath = pureModelContextData.getElementsOfType(Service.class).stream().collect(Collectors.toMap(PackageableElement::getPath, el -> el));
        filterServicesByIncludes(servicesByPath);
        filterServicesByExcludes(servicesByPath);
        if (servicesByPath.isEmpty())
        {
            getLog().info("Found 0 services for generation");
        }
        else if (getLog().isInfoEnabled())
        {
            getLog().info(servicesByPath.keySet().stream().sorted().collect(Collectors.joining(", ", "Found " + servicesByPath.size() + " services for generation: ", "")));
        }

        for (Service service : servicesByPath.values())
        {
            getLog().info("Generating execution artifacts for " + service.getPath());
            long serviceStart = System.nanoTime();
            try
            {
                MutableList<PlanGeneratorExtension> extensions = Lists.mutable.withAll(ServiceLoader.load(PlanGeneratorExtension.class));
                RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> routerExtensions = extensions.flatCollect(e -> e.getExtraRouterExtensions(pureModel));
                MutableList<PlanTransformer> planTransformers = extensions.flatCollect(PlanGeneratorExtension::getExtraPlanTransformers);
                ServiceExecutionGenerator.newGenerator(service, pureModel, this.packagePrefix, this.javaSourceOutputDirectory.toPath(), this.resourceOutputDirectory.toPath(), objectMapper, routerExtensions, planTransformers, null).generate();
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error generating execution artifacts for " + service.getPath(), e);
            }
            long serviceEnd = System.nanoTime();
            getLog().info(String.format("Finished generating execution artifacts for %s (%.9fs)", service.getPath(), (serviceEnd - serviceStart) / 1_000_000_000.0));
        }
        long end = System.nanoTime();
        getLog().info(String.format("Finished generating execution artifacts for %d services (%.9fs)", servicesByPath.size(), (end - start) / 1_000_000_000.0));
    }

    private void filterServicesByIncludes(Map<String, Service> servicesByPath) throws MojoExecutionException
    {
        if (this.inclusions != null)
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
            servicesByPath.keySet().removeIf(resolvedIncluded::notMatches);
            if ((resolvedIncluded.servicePaths != null) && resolvedIncluded.servicePaths.stream().anyMatch(p -> !servicesByPath.containsKey(p)))
            {
                throw new MojoExecutionException(resolvedIncluded.servicePaths.stream().filter(p -> !servicesByPath.containsKey(p)).sorted().collect(Collectors.joining(", ", "Could not find included services: ", "")));
            }
        }
    }

    private void filterServicesByExcludes(Map<String, Service> servicesByPath) throws MojoExecutionException
    {
        if ((this.exclusions != null) && !servicesByPath.isEmpty())
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
            servicesByPath.keySet().removeIf(resolvedExcluded::matches);
        }
    }

    private static ResolvedServicesSpecification resolveServicesSpecification(ServicesSpecification servicesSpec) throws Exception
    {
        Set<String> servicePaths = null;
        if (servicesSpec.directories != null)
        {
            try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(servicesSpec.directories))
            {
                servicePaths = directoriesLoader.getAllEntities()
                        .filter(e -> "meta::legend::service::metamodel::Service".equals(e.getClassifierPath()))
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
        private final List<String> packages;

        private ResolvedServicesSpecification(Set<String> servicePaths, Set<String> packages)
        {
            this.servicePaths = servicePaths;
            if (packages == null)
            {
                this.packages = null;
            }
            else
            {
                this.packages = new ArrayList<>(packages);
                this.packages.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
            }
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
            return this.packages.stream().anyMatch(pkg -> servicePath.startsWith(pkg) && servicePath.startsWith("::", pkg.length()));
        }

        boolean notMatches(String servicePath)
        {
            return !matches(servicePath);
        }
    }
}
