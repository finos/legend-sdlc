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

package org.finos.legend.sdlc.server.project.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.tools.IOTools;
import org.finos.legend.sdlc.server.tools.StringTools;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class MavenProjectStructure extends ProjectStructure
{
    public static final String MAVEN_MODEL_PATH = "/pom.xml";
    public static final String JAR_PACKAGING = "jar";
    public static final String POM_PACKAGING = "pom";

    private static final ImmutableList<ArtifactType> DEFAULT_ARTIFACT_TYPES = Lists.immutable.with(ArtifactType.entities, ArtifactType.versioned_entities);

    private final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, List<EntitySourceDirectory> entitySourceDirectories, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, entitySourceDirectories);
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, EntitySourceDirectory entitySourceDirectory, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, entitySourceDirectory);
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory, EntitySerializer entitySerializer, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, entitiesDirectory, entitySerializer);
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, entitiesDirectory);
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    @Deprecated
    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, List<EntitySourceDirectory> entitySourceDirectories)
    {
        this(projectConfiguration, entitySourceDirectories, null);
    }

    @Deprecated
    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, EntitySourceDirectory entitySourceDirectory)
    {
        this(projectConfiguration, entitySourceDirectory, null);
    }

    @Deprecated
    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory, EntitySerializer entitySerializer)
    {
        this(projectConfiguration, entitiesDirectory, entitySerializer, null);
    }

    @Deprecated
    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory)
    {
        this(projectConfiguration, entitiesDirectory, (ProjectStructurePlatformExtensions) null);
    }

    @Override
    protected void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        super.collectUpdateProjectConfigurationOperations(oldStructure, fileAccessContext, operationConsumer);
        Model mavenModel = createAndConfigureMavenProjectModel();
        String serializedMavenModel = serializeMavenModel(mavenModel);
        addOrModifyFile(MAVEN_MODEL_PATH, serializedMavenModel, fileAccessContext, operationConsumer);
    }

    private Model createAndConfigureMavenProjectModel()
    {
        // Collect model configuration
        MavenModelConfiguration mavenModelConfig = new MavenModelConfiguration();
        configureMavenProjectModel(mavenModelConfig);

        // Call legacy methods for backward compatibility
        addMavenProjectProperties(mavenModelConfig::setProperty);
        addMavenProjectDependencyManagement(null, mavenModelConfig::addDependencyManagement);
        addMavenProjectDependencies(null, mavenModelConfig::addDependency);
        addMavenProjectPluginManagement(null, mavenModelConfig::addPluginManagement);
        addMavenProjectPlugins(null, mavenModelConfig::addPlugin);

        // Create maven model
        Model mavenModel = new Model();

        // Main attributes
        ProjectConfiguration configuration = getProjectConfiguration();
        mavenModel.setModelVersion(getMavenModelVersion());
        mavenModel.setModelEncoding(getMavenModelEncoding());
        mavenModel.setGroupId(configuration.getGroupId());
        mavenModel.setArtifactId(configuration.getArtifactId());
        mavenModel.setVersion(getMavenModelProjectVersion());
        mavenModel.setPackaging(getMavenProjectModelPackaging());

        // Configure model
        mavenModelConfig.configureModel(mavenModel);

        return mavenModel;
    }

    protected void configureMavenProjectModel(MavenModelConfiguration configuration)
    {
        // Properties
        configuration.setProperty("project.build.sourceEncoding", getMavenModelSourceEncoding());
        configuration.setProperty("maven.compiler.source", getMavenModelJavaVersion());
        configuration.setProperty("maven.compiler.target", getMavenModelJavaVersion());

        List<PlatformConfiguration> platformConfigurations = getProjectConfiguration().getPlatformConfigurations();
        if (platformConfigurations != null)
        {
            platformConfigurations.forEach(p -> configuration.setProperty(getPlatformPropertyName(p.getName()), p.getVersion()));
        }
        else if (this.projectStructurePlatformExtensions != null)
        {
            getPlatforms().forEach(platform ->
            {
                String version = platform.getPublicStructureVersion(getVersion());
                if (version != null)
                {
                    configuration.setProperty(getPlatformPropertyName(platform.getName()), version);
                }
            });
        }

        // Plugins
        addMavenSourcePlugin(configuration::addPlugin, true);
    }

    public ProjectStructurePlatformExtensions getProjectStructureExtensions()
    {
        return this.projectStructurePlatformExtensions;
    }

    public List<ProjectStructurePlatformExtensions.Platform> getPlatforms()
    {
        return Optional.ofNullable(this.projectStructurePlatformExtensions)
                .map(ProjectStructurePlatformExtensions::getPlatforms)
                .orElse(Lists.fixedSize.empty());
    }

    public String getPlatformPropertyName(String platform)
    {
        return "platform." + platform + ".version";
    }

    public String getPlatformPropertyReference(String platform)
    {
        return getPropertyReference(getPlatformPropertyName(platform));
    }

    @Deprecated
    protected void addMavenProjectProperties(BiConsumer<String, String> propertySetter)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addMavenProjectDependencyManagement(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addMavenProjectDependencies(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addMavenProjectPluginManagement(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addMavenProjectPlugins(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        // retained for backward compatibility
    }

    protected abstract String getMavenProjectModelPackaging();

    protected String getMavenModelVersion()
    {
        return "4.0.0";
    }

    protected String getMavenModelEncoding()
    {
        return StandardCharsets.UTF_8.name();
    }

    protected String getMavenModelSourceEncoding()
    {
        return StandardCharsets.UTF_8.name();
    }

    protected String getMavenModelJavaVersion()
    {
        return "1.8";
    }

    protected String getMavenModelProjectVersion()
    {
        return "0.0.1-SNAPSHOT";
    }

    protected String getMavenSourcePluginVersion()
    {
        return null;
    }

    public void addMavenSourcePlugin(Consumer<Plugin> pluginConsumer, boolean includeVersion)
    {
        String version = getMavenSourcePluginVersion();
        if (version != null)
        {
            Plugin plugin = MavenPluginTools.newPlugin(null, "maven-source-plugin", includeVersion ? version : null);
            plugin.addExecution(MavenPluginTools.newPluginExecution("attach-sources", "verify", "jar-no-fork"));
            pluginConsumer.accept(plugin);
        }
    }

    protected String getJunitVersion()
    {
        return "4.12";
    }

    protected void addJunitDependency(Consumer<Dependency> dependencyConsumer)
    {
        addJunitDependency(dependencyConsumer, true);
    }

    public void addJunitDependency(Consumer<Dependency> dependencyConsumer, boolean includeVersion)
    {
        String version = getJunitVersion();
        if (version != null)
        {
            dependencyConsumer.accept(newMavenTestDependency("junit", "junit", includeVersion ? version : null));
        }
    }

    protected String getJacksonVersion()
    {
        return "2.9.6";
    }

    protected void addJacksonDependency(Consumer<Dependency> dependencyConsumer)
    {
        addJacksonDependency(dependencyConsumer, true);
    }

    public void addJacksonDependency(Consumer<Dependency> dependencyConsumer, boolean includeVersion)
    {
        String version = getJacksonVersion();
        if (version != null)
        {
            // TODO not a test dependency
            dependencyConsumer.accept(newMavenTestDependency("com.fasterxml.jackson.core", "jackson-databind", includeVersion ? version : null));
        }
    }

    protected Stream<Dependency> getAllProjectDependenciesAsMavenDependencies(boolean withVersions)
    {
        return getProjectDependencies().stream().flatMap(pd -> projectDependencyToAllMavenDependencies(pd, withVersions));
    }

    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(ArtifactType artifactType, boolean withVersions)
    {
        return getProjectDependenciesAsMavenDependencies(Collections.singletonList(artifactType), withVersions);
    }

    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(Collection<? extends ArtifactType> artifactTypes, boolean withVersions)
    {
        return getProjectDependencies().stream().flatMap(pd -> projectDependencyToMavenDependencies(pd, artifactTypes, withVersions));
    }

    @Deprecated
    protected Stream<Dependency> getAllProjectDependenciesAsMavenDependencies(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getAllProjectDependenciesAsMavenDependencies(withVersions);
    }

    @Deprecated
    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(ArtifactType artifactType, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getProjectDependenciesAsMavenDependencies(artifactType, withVersions);
    }

    @Deprecated
    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(Collection<? extends ArtifactType> artifactTypes, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getProjectDependenciesAsMavenDependencies(artifactTypes, withVersions);
    }

    public static Stream<Dependency> projectDependencyToMavenDependenciesForType(ProjectDependency projectDependency, ArtifactType artifactType, boolean setVersion)
    {
        return projectDependencyToMavenDependencies(projectDependency, Collections.singletonList(artifactType), setVersion);
    }

    public static Stream<Dependency> projectDependencyToAllMavenDependencies(ProjectDependency projectDependency, boolean setVersion)
    {
        return projectDependencyToMavenDependencies(projectDependency, null, setVersion);
    }

    public static Stream<Dependency> projectDependencyToMavenDependencies(ProjectDependency projectDependency, Collection<? extends ArtifactType> artifactTypes, boolean setVersion)
    {
        String versionString = setVersion ? projectDependency.getVersionId() : null;
        Pair<String, String> mavenCoordinates = getGroupAndArtifactIdFromProjectDependency(projectDependency);
        Collection<? extends ArtifactType> resolvedArtifactTypes = ((artifactTypes == null) || artifactTypes.isEmpty()) ? DEFAULT_ARTIFACT_TYPES.castToCollection() : artifactTypes;
        return resolvedArtifactTypes.stream().map(artifactType -> newMavenDependency(mavenCoordinates.getOne(), mavenCoordinates.getTwo() + "-" + artifactType.name().replace('_', '-').toLowerCase(), versionString));
    }

    @Deprecated
    public static Stream<Dependency> projectDependencyToMavenDependenciesForType(ProjectDependency projectDependency, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, ArtifactType artifactType, boolean setVersion)
    {
        return projectDependencyToMavenDependenciesForType(projectDependency, artifactType, setVersion);
    }

    @Deprecated
    public static Stream<Dependency> projectDependencyToAllMavenDependencies(ProjectDependency projectDependency, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, boolean setVersion)
    {
        return projectDependencyToAllMavenDependencies(projectDependency, setVersion);
    }

    @Deprecated
    public static Stream<Dependency> projectDependencyToMavenDependencies(ProjectDependency projectDependency, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Collection<? extends ArtifactType> artifactTypes, boolean setVersion)
    {
        return projectDependencyToMavenDependencies(projectDependency, artifactTypes, setVersion);
    }

    private static Pair<String, String> getGroupAndArtifactIdFromProjectDependency(ProjectDependency projectDependency)
    {
        String projectId = Objects.requireNonNull(projectDependency.getProjectId(), "project dependency project id may not be null");
        int index = projectId.indexOf(':');
        if ((index <= 0) || (index == (projectId.length() - 1)))
        {
            throw new IllegalArgumentException("Could not get group and artifact id from \"" + projectId + "\"");
        }
        return Tuples.pair(projectId.substring(0, index), projectId.substring(index + 1));
    }

    public static Exclusion newMavenExclusion(String groupId, String artifactId)
    {
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId(groupId);
        exclusion.setArtifactId(artifactId);
        return exclusion;
    }

    public static Dependency newMavenDependency(String groupId, String artifactId, String version)
    {
        return newMavenDependency(groupId, artifactId, version, null);
    }

    protected static Dependency newMavenTestDependency(String groupId, String artifactId, String version)
    {
        return newMavenDependency(groupId, artifactId, version, "test");
    }

    protected static Dependency newMavenDependency(String groupId, String artifactId, String version, String scope)
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        if (version != null)
        {
            dependency.setVersion(version);
        }
        if (scope != null)
        {
            dependency.setScope(scope);
        }
        return dependency;
    }

    public static Model getProjectMavenModel(FileAccessContext accessContext)
    {
        return deserializeMavenModel(accessContext.getFile(MAVEN_MODEL_PATH));
    }

    public static Model deserializeMavenModel(ProjectFileAccessProvider.ProjectFile projectFile)
    {
        try (Reader reader = projectFile.getContentAsReader())
        {
            return new MavenXpp3Reader().read(reader);
        }
        catch (IOException | XmlPullParserException e)
        {
            throw new RuntimeException("Error reading project Maven model from: " + projectFile.getPath(), e);
        }
    }

    protected static String serializeMavenModel(Model model)
    {
        // Sort properties
        Properties properties = model.getProperties();
        if (!(properties instanceof SortedProperties))
        {
            SortedProperties sortedProperties = new SortedProperties();
            sortedProperties.putAll(properties);
            model.setProperties(sortedProperties);
        }

        // Sort dependencies
        model.getDependencies().sort(MavenProjectStructure::compareDependencies);

        // Write to string
        StringWriter writer = new StringWriter();
        try
        {
            new MavenXpp3Writer().write(writer, model);
        }
        catch (IOException e)
        {
            // this should not happen
            throw new RuntimeException("Error writing Maven model", e);
        }
        return writer.toString();
    }

    protected static String loadJavaTestCode(int version, String testName)
    {
        String resourceName = "project/tests/v" + version + "/" + testName + ".java";
        return loadTestResourceCode(resourceName, StandardCharsets.ISO_8859_1);
    }

    protected static String loadTestResourceCode(String resourceName, Charset charset)
    {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        try
        {
            return IOTools.readAllToString(url, charset);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(StringTools.appendThrowableMessageIfPresent(new StringBuilder("Error reading resource '").append(resourceName).append("'").append(" (").append(url).append(")"), e).toString(), e);
        }
    }

    protected static String getPropertyReference(String propertyName)
    {
        return "${" + propertyName + "}";
    }

    private static int compareDependencies(Dependency dep1, Dependency dep2)
    {
        if (dep1 == dep2)
        {
            return 0;
        }

        // Compare scope
        int cmp = compareScopes(dep1.getScope(), dep2.getScope());
        if (cmp != 0)
        {
            return cmp;
        }

        // Compare groupId
        cmp = comparePossiblyNull(dep1.getGroupId(), dep2.getGroupId());
        if (cmp != 0)
        {
            return cmp;
        }

        // Compare artifactId
        cmp = comparePossiblyNull(dep1.getArtifactId(), dep2.getArtifactId());
        if (cmp != 0)
        {
            return cmp;
        }

        return comparePossiblyNull(dep1.getVersion(), dep2.getVersion());
    }

    private static final String[] SCOPE_CMP_ORDER = {"compile", "runtime", "system", "provided", "test"};

    private static int compareScopes(String scope1, String scope2)
    {
        if (scope1 == null)
        {
            scope1 = SCOPE_CMP_ORDER[0];
        }
        if (scope2 == null)
        {
            scope2 = SCOPE_CMP_ORDER[0];
        }
        if (scope1.equals(scope2))
        {
            return 0;
        }
        for (String scope : SCOPE_CMP_ORDER)
        {
            if (scope1.equals(scope))
            {
                return -1;
            }
            if (scope2.equals(scope))
            {
                return 1;
            }
        }
        return scope1.compareTo(scope2);
    }

    private static <T extends Comparable<? super T>> int comparePossiblyNull(T obj1, T obj2)
    {
        if (obj1 == obj2)
        {
            return 0;
        }
        if (obj1 == null)
        {
            return 1;
        }
        if (obj2 == null)
        {
            return -1;
        }
        return obj1.compareTo(obj2);
    }

    protected static class MavenCoordinates
    {
        private final String groupId;
        private final String artifactId;
        private final String version;

        public MavenCoordinates(String groupdId, String artifactId, String version)
        {
            this.groupId = groupdId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getArtifactId()
        {
            return artifactId;
        }

        public String getVersion()
        {
            return version;
        }

        public String getGroupId()
        {
            return groupId;
        }
    }

    protected static class MavenModelConfiguration
    {
        private final SortedProperties properties = new SortedProperties();
        private final MutableList<String> modules = Lists.mutable.empty();
        private final MutableList<Dependency> dependencyManagement = Lists.mutable.empty();
        private final MutableList<Dependency> dependencies = Lists.mutable.empty();
        private final MutableList<Plugin> pluginManagement = Lists.mutable.empty();
        private final MutableList<Plugin> plugins = Lists.mutable.empty();

        public void setProperty(String property, String value)
        {
            this.properties.setProperty(property, value);
        }

        public void addModule(String module)
        {
            this.modules.add(module);
        }

        public void setPropertyIfAbsent(String property, String value)
        {
            this.properties.putIfAbsent(property, value);
        }

        public void addDependencyManagement(Dependency dependencyManagement)
        {
            this.dependencyManagement.add(dependencyManagement);
        }

        public void addDependency(Dependency dependency)
        {
            this.dependencies.add(dependency);
        }

        public void addPluginManagement(Plugin pluginManagement)
        {
            this.pluginManagement.add(pluginManagement);
        }

        public void addPlugin(Plugin plugin)
        {
            this.plugins.add(plugin);
        }

        protected void configureModel(Model model)
        {
            if (!this.properties.isEmpty())
            {
                model.setProperties(this.properties);
            }

            if (this.modules.notEmpty())
            {
                model.setModules(this.modules);
            }

            if (this.dependencyManagement.notEmpty())
            {
                DependencyManagement dm = new DependencyManagement();
                dm.setDependencies(this.dependencyManagement);
                model.setDependencyManagement(dm);
            }

            if (this.dependencies.notEmpty())
            {
                model.setDependencies(this.dependencies);
            }

            if (this.pluginManagement.notEmpty() || this.plugins.notEmpty())
            {
                Build build = new Build();
                if (this.pluginManagement.notEmpty())
                {
                    PluginManagement pluginManagement = new PluginManagement();
                    pluginManagement.setPlugins(this.pluginManagement);
                    build.setPluginManagement(pluginManagement);
                }
                if (this.plugins.notEmpty())
                {
                    build.setPlugins(this.plugins);
                }
                model.setBuild(build);
            }
        }
    }

    protected static class SortedProperties extends Properties
    {
        @Override
        public Enumeration<Object> keys()
        {
            return Collections.enumeration(sortKeys(super.keySet()));
        }

        @Override
        public Set<Object> keySet()
        {
            return withSortedIteration(super.keySet(), this::sortKeys);
        }

        @Override
        public Set<Map.Entry<Object, Object>> entrySet()
        {
            return withSortedIteration(super.entrySet(), this::sortEntries);
        }

        @Override
        public synchronized void forEach(BiConsumer<? super Object, ? super Object> action)
        {
            sortEntries(super.entrySet()).forEach(e -> action.accept(e.getKey(), e.getValue()));
        }

        private synchronized MutableList<Object> sortKeys(Set<Object> keySet)
        {
            return Lists.mutable.withAll(keySet).sortThis();
        }

        private synchronized MutableList<Map.Entry<Object, Object>> sortEntries(Set<Map.Entry<Object, Object>> entrySet)
        {
            return Lists.mutable.withAll(entrySet).sortThisBy(e -> (String) e.getKey());
        }

        private <T> Set<T> withSortedIteration(Set<T> set, Function<Set<T>, MutableList<T>> sort)
        {
            return new AbstractSet<T>()
            {
                @Override
                public int hashCode()
                {
                    return set.hashCode();
                }

                @Override
                public boolean equals(Object other)
                {
                    return set.equals(other);
                }

                @Override
                public Iterator<T> iterator()
                {
                    return sort.apply(set).iterator();
                }

                @Override
                public int size()
                {
                    return set.size();
                }

                @Override
                public boolean contains(Object o)
                {
                    return set.contains(o);
                }

                @Override
                public boolean remove(Object o)
                {
                    return set.remove(o);
                }

                @Override
                public boolean removeIf(Predicate<? super T> filter)
                {
                    return set.removeIf(filter);
                }

                @Override
                public void clear()
                {
                    set.clear();
                }
            };
        }
    }
}
