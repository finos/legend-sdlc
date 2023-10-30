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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;

@Mojo(name = "generate-junit-tests", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class JUnitTestGenerationMojo extends AbstractMojo
{
    @Parameter
    private EntityFilterSpecification inclusions;

    @Parameter
    private EntityFilterSpecification exclusions;

    @Parameter
    private String packagePrefix;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources")
    private File outputDirectory;

    @Parameter(defaultValue = "true")
    private boolean addOutputDirectoryAsTestSource;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File entitiesDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "false")
    private boolean runDependencyTests;

    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().info("entities directory: " + this.entitiesDirectory);
        getLog().info("output directory: " + this.outputDirectory);
        getLog().info("add output directory as test source: " + this.addOutputDirectoryAsTestSource);
        if (this.inclusions != null)
        {
            getLog().info("include entity paths: " + this.inclusions.paths);
            getLog().info("include entity packages: " + this.inclusions.packages);
        }
        if (this.exclusions != null)
        {
            getLog().info("exclude entity paths: " + this.exclusions.paths);
            getLog().info("exclude entity packages: " + this.exclusions.packages);
        }
        getLog().info("package prefix: " + ((this.packagePrefix == null) ? null : ('"' + this.packagePrefix + '"')));

        if ((this.packagePrefix != null) && !SourceVersion.isName(this.packagePrefix))
        {
            throw new MojoExecutionException("Invalid package prefix: " + this.packagePrefix);
        }
        if (this.runDependencyTests)
        {
            getLog().info("running dependency tests flag set");
        }

        long start = System.nanoTime();
        try
        {
            JUnitTestGenerator generator = JUnitTestGenerator.newGenerator(this.packagePrefix);
            try (EntityLoader entityLoader = this.runDependencyTests ? EntityLoader.newEntityLoader(this.getClassLoader(this.project)) : EntityLoader.newEntityLoader(this.entitiesDirectory))
            {
                Stream<Entity> stream = entityLoader.getAllEntities();
                Predicate<Entity> includeFilter = resolveEntityFilter(this.inclusions);
                if (includeFilter != null)
                {
                    stream = stream.filter(includeFilter);
                }
                Predicate<Entity> excludeFilter = resolveEntityFilter(this.exclusions);
                if (excludeFilter != null)
                {
                    stream = stream.filter(excludeFilter.negate());
                }
                List<Path> paths = generator.writeTestClasses(this.outputDirectory.toPath(), stream);
                getLog().info("Generated " + paths.size() + " test files");
            }

            if (this.addOutputDirectoryAsTestSource)
            {
                String newTestSourceDirectory = this.outputDirectory.getAbsolutePath();
                this.project.addTestCompileSourceRoot(newTestSourceDirectory);
                getLog().info("Added test source directory: " + newTestSourceDirectory);
            }

            long end = System.nanoTime();
            getLog().info(String.format("Finished generating tests (%.9fs)", (end - start) / 1_000_000_000.0));
        }
        catch (Exception e)
        {
            long end = System.nanoTime();
            getLog().error(String.format("Error generating tess (%.9fs)", (end - start) / 1_000_000_000.0));
            throw new MojoExecutionException("Error generating tests", e);
        }
    }

    // requires requiresDependencyResolution set to compile for resolution of project to be complete
    // loads all classes from maven project see: https://maven.apache.org/guides/mini/guide-maven-classloading.html#plugin-classloaders and https://stackoverflow.com/questions/49737706/access-project-classes-from-a-maven-plugin
    private ClassLoader getClassLoader(MavenProject project) throws MojoExecutionException {
        try
        {
            List<String> classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i)
            {
                urls[i] = new File(classpathElements.get(i)).toURL();
            }
            return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        }
        catch (Exception e)
        {
            getLog().debug("Couldn't get the classloader.");
            throw new MojoExecutionException("Error getting class loader to run dependency tests", e);
        }
    }

    private static Predicate<Entity> resolveEntityFilter(EntityFilterSpecification spec)
    {
        if ((spec == null) || ((spec.paths == null) && (spec.packages == null)))
        {
            // No filter specified
            return null;
        }
        if ((spec.packages != null) && (spec.packages.contains("") || spec.packages.contains(EntityPaths.PACKAGE_SEPARATOR)))
        {
            // The packages spec includes the root package, so all entities match
            return e -> true;
        }

        Set<String> pathSet = (spec.paths == null) ? Collections.emptySet() : spec.paths;
        ListIterable<String> resolvedPackages = (spec.packages == null) ?
                Lists.fixedSize.empty() :
                Iterate.collect(spec.packages, p -> p.endsWith(EntityPaths.PACKAGE_SEPARATOR) ? p : (p + EntityPaths.PACKAGE_SEPARATOR), Lists.mutable.ofInitialCapacity(spec.packages.size()))
                        .sortThis(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        return resolvedPackages.isEmpty() ?
                (pathSet.isEmpty() ?
                        e -> false :
                        e -> pathSet.contains(e.getPath())) :
                (pathSet.isEmpty() ?
                        e -> inSomePackage(resolvedPackages, e.getPath()) :
                        e -> pathSet.contains(e.getPath()) || inSomePackage(resolvedPackages, e.getPath()));
    }

    private static boolean inSomePackage(ListIterable<String> packages, String path)
    {
        int lastSeparator = path.lastIndexOf(EntityPaths.PACKAGE_SEPARATOR);
        int pkgLen = (lastSeparator == -1) ? 0 : lastSeparator + EntityPaths.PACKAGE_SEPARATOR.length();
        for (String pkg : packages)
        {
            if (path.startsWith(pkg))
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

    public static class EntityFilterSpecification
    {
        public Set<String> paths;
        public Set<String> packages;
    }
}
