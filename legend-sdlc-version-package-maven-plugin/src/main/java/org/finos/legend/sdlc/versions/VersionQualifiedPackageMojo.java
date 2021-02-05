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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;

@Mojo(name = "version-qualify-packages", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class VersionQualifiedPackageMojo extends AbstractMojo
{
    @Parameter(required = true)
    private File[] entitySourceDirectories;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter
    private String versionAlias;

    @Parameter(defaultValue = "true")
    private boolean useParentInfoIfPresent;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        long start = System.nanoTime();

        getLog().info("entity input directories: " + Arrays.toString(this.entitySourceDirectories));
        getLog().info("output directory: " + this.outputDirectory);
        getLog().info("use parent info if present: " + this.useParentInfoIfPresent);
        getLog().info("groupId: " + findGroupId());
        getLog().info("artifactId: " + findArtifactId());
        getLog().info("version: " + findVersion());
        if (this.versionAlias != null)
        {
            getLog().info("version alias: \"" + this.versionAlias + "\"");
        }

        try
        {
            validateProjectInfo();
        }
        catch (MojoFailureException e)
        {
            getLog().error(String.format("Error generating entities with version qualified packages: %s (%.9fs)", e.getMessage(), (System.nanoTime() - start) / 1_000_000_000.0), e);
            throw e;
        }

        try
        {
            getLog().info("Generating entities with version qualified packages");
            List<Entity> transformedEntities = transformEntities();
            serializeEntities(transformedEntities);
            getLog().info(String.format("Done (%.9fs)", (System.nanoTime() - start) / 1_000_000_000.0));
        }
        catch (MojoExecutionException | MojoFailureException e)
        {
            getLog().error(String.format("Error generating entities with version qualified packages: %s (%.9fs)", e.getMessage(), (System.nanoTime() - start) / 1_000_000_000.0), e);
            throw e;
        }
        catch (Exception e)
        {
            String message = "Error generating entities with version qualified packages: " + e.getMessage();
            getLog().error(String.format("%s (%.9fs)", message, (System.nanoTime() - start) / 1_000_000_000.0), e);
            throw new MojoExecutionException(message, e);
        }
    }

    private void validateProjectInfo() throws MojoFailureException
    {
        if (this.versionAlias == null)
        {
            String version = findVersion();
            if ((version == null) || version.isEmpty())
            {
                throw new MojoFailureException("A non-empty version is required");
            }
        }
        else if (!this.versionAlias.matches("^\\w++$"))
        {
            throw new MojoFailureException("Invalid version alias: \"" + this.versionAlias + "\"");
        }

        String groupId = findGroupId();
        if ((groupId == null) || groupId.isEmpty() || !SourceVersion.isName(groupId))
        {
            StringBuilder builder = new StringBuilder("A valid groupId is required, found: ");
            if (groupId == null)
            {
                builder.append("null");
            }
            else
            {
                builder.append('"').append(groupId).append('"');
            }
            throw new MojoFailureException(builder.toString());
        }

        String artifactId = findArtifactId();
        if ((artifactId == null) || artifactId.isEmpty() || !artifactId.matches("^\\w++(-\\w++)*+$"))
        {
            StringBuilder builder = new StringBuilder("A valid artifactId is required, found: ");
            if (artifactId == null)
            {
                builder.append("null");
            }
            else
            {
                builder.append('"').append(artifactId).append('"');
            }
            throw new MojoFailureException(builder.toString());
        }
    }

    private String findVersion()
    {
        return findMavenProjectInfo(MavenProject::getVersion);
    }

    private String findGroupId()
    {
        return findMavenProjectInfo(MavenProject::getGroupId);
    }

    private String findArtifactId()
    {
        return findMavenProjectInfo(MavenProject::getArtifactId);
    }

    private <T> T findMavenProjectInfo(Function<? super MavenProject, T> infoAccessor)
    {
        if (this.useParentInfoIfPresent)
        {
            MavenProject parent = this.mavenProject.getParent();
            if (parent != null)
            {
                T value = infoAccessor.apply(parent);
                if (value != null)
                {
                    return value;
                }
            }
        }
        return infoAccessor.apply(this.mavenProject);
    }

    private List<Entity> transformEntities() throws Exception
    {
        long transformStart = System.nanoTime();
        getLog().info("Qualifying entity packages by version");

        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(getPathTransformationFunction());
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(this.entitySourceDirectories))
        {
            entityLoader.getAllEntities().forEach(transformer::addEntity);
        }
        List<Entity> transformedEntities = transformer.transformEntities();
        getLog().info(String.format("Done qualifying packages by version for %,d entities (%.9fs)", transformedEntities.size(), (System.nanoTime() - transformStart) / 1_000_000_000.0));
        return transformedEntities;
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

    private Function<String, String> getPathTransformationFunction() throws Exception
    {
        Map<String, String> pathMap = new HashMap<>();

        // main project
        String projectPrefix = getPackagePrefix(findGroupId(), findArtifactId(), findVersion(), this.versionAlias);
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(this.entitySourceDirectories))
        {
            entityLoader.getAllEntities().forEach(e -> forEachPackageableElementPath(e, path -> pathMap.computeIfAbsent(path, projectPrefix::concat)));
        }

        // dependencies
        List<Dependency> dependencies = this.mavenProject.getDependencies();
        if ((dependencies != null) && !dependencies.isEmpty())
        {
            Set<DependencyArtifactKey> dependencyKeys = dependencies.stream().map(DependencyArtifactKey::new).collect(Collectors.toSet());
            this.mavenProject.getArtifacts()
                    .stream()
                    .filter(a -> dependencyKeys.contains(new DependencyArtifactKey(a)))
                    .forEach(artifact ->
                    {
                        Pattern prefixPattern = Pattern.compile(appendVersionPackage(appendGroupIdPackage(new StringBuilder("^\\Q"), artifact.getGroupId()).append("::\\E\\w+\\Q::"), artifact.getVersion()).append("::\\E").toString());
                        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(artifact.getFile()))
                        {
                            entityLoader.getAllEntities().forEach(e -> forEachPackageableElementPath(e, path ->
                            {
                                Matcher matcher = prefixPattern.matcher(path);
                                if (matcher.find())
                                {
                                    String pathWithoutPrefix = path.substring(matcher.end());
                                    pathMap.putIfAbsent(pathWithoutPrefix, path);
                                }
                            }));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException("Error reading from artifact " + artifact.getId(), e);
                        }
                    });
        }

        return getLog().isWarnEnabled() ?
                path ->
                {
                    String transformed = pathMap.get(path);
                    if (transformed == null)
                    {
                        getLog().warn("No transformation found for: " + path);
                        return path;
                    }
                    return transformed;
                } :
                path -> pathMap.getOrDefault(path, path);
    }

    private static void forEachPackageableElementPath(Entity entity, Consumer<? super String> pathConsumer)
    {
        forEachPackageableElementPath(entity.getContent(), pathConsumer);
    }

    private static void forEachPackageableElementPath(Object value, Consumer<? super String> pathConsumer)
    {
        if (value instanceof Map)
        {
            Map<?, ?> map = (Map<?, ?>) value;
            Object pkg = map.get("package");
            if (pkg instanceof String)
            {
                Object name = map.get("name");
                if (name instanceof String)
                {
                    String path = pkg + "::" + name;
                    pathConsumer.accept(path);
                }
            }
            map.values().forEach(v -> forEachPackageableElementPath(v, pathConsumer));
        }
        else if (value instanceof Iterable)
        {
            ((Iterable<?>) value).forEach(v -> forEachPackageableElementPath(v, pathConsumer));
        }
    }

    private static String getPackagePrefix(String groupId, String artifactId, String version)
    {
        return getPackagePrefix(groupId, artifactId, version, null);
    }

    private static String getPackagePrefix(String groupId, String artifactId, String version, String versionAlias)
    {
        StringBuilder builder = new StringBuilder(groupId.length() + artifactId.length() + ((versionAlias == null) ? version : versionAlias).length() + 16);

        // groupId
        appendGroupIdPackage(builder, groupId).append("::");

        // artifactId
        appendArtifactIdPackage(builder, artifactId).append("::");

        // version/versionAlias
        if (versionAlias == null)
        {
            appendVersionPackage(builder, version);
        }
        else
        {
            builder.append(versionAlias);
        }
        builder.append("::");

        return builder.toString();
    }

    private static StringBuilder appendGroupIdPackage(StringBuilder builder, String groupId)
    {
        int start = 0;
        for (int end = groupId.indexOf('.'); end != -1; start = end + 1, end = groupId.indexOf('.', start))
        {
            builder.append(groupId, start, end).append("::");
        }
        return builder.append(groupId, start, groupId.length());
    }

    private static StringBuilder appendArtifactIdPackage(StringBuilder builder, String artifactId)
    {
        builder.append(artifactId);
        for (int i = builder.length() - artifactId.length(); i < builder.length(); i++)
        {
            if (!isValidPackageNameCharacter(builder.charAt(i)))
            {
                builder.setCharAt(i, '_');
            }
        }
        return builder;
    }

    private static StringBuilder appendVersionPackage(StringBuilder builder, String version)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(version);
        }
        catch (IllegalArgumentException ignore)
        {
            versionId = null;
        }

        if (versionId == null)
        {
            builder.append("vX_X_X");
        }
        else
        {
            builder.append('v');
            versionId.appendVersionIdString(builder, '_');
        }
        return builder;
    }

    private static boolean isValidPackageNameCharacter(char c)
    {
        return (c == '_') ||
                ((c >= 'a') && (c <= 'z')) ||
                ((c >= 'A') && (c <= 'Z')) ||
                ((c >= '0') && (c <= '9'));
    }

    private static final class DependencyArtifactKey
    {
        private final String groupId;
        private final String artifactId;

        private DependencyArtifactKey(String groupId, String artifactId)
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        private DependencyArtifactKey(Dependency dependency)
        {
            this(dependency.getGroupId(), dependency.getArtifactId());
        }

        private DependencyArtifactKey(Artifact artifact)
        {
            this(artifact.getGroupId(), artifact.getArtifactId());
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if (!(other instanceof DependencyArtifactKey))
            {
                return false;
            }

            DependencyArtifactKey otherKey = (DependencyArtifactKey) other;
            return Objects.equals(this.groupId, otherKey.groupId) && Objects.equals(this.artifactId, otherKey.artifactId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.groupId) + (827 * Objects.hashCode(this.artifactId));
        }

        @Override
        public String toString()
        {
            return this.groupId + ":" + this.artifactId;
        }
    }
}
