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
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.IOTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class MavenProjectStructure extends ProjectStructure
{
    public static final String MAVEN_MODEL_PATH = "/pom.xml";
    public static final String JAR_PACKAGING = "jar";
    public static final String POM_PACKAGING = "pom";

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, List<EntitySourceDirectory> entitySourceDirectories)
    {
        super(projectConfiguration, entitySourceDirectories);
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, EntitySourceDirectory entitySourceDirectory)
    {
        super(projectConfiguration, entitySourceDirectory);
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory, EntitySerializer entitySerializer)
    {
        super(projectConfiguration, entitiesDirectory, entitySerializer);
    }

    protected MavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory)
    {
        super(projectConfiguration, entitiesDirectory);
    }

    @Override
    public void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, ProjectFileAccessProvider.FileAccessContext fileAccessContext, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<ProjectFileOperation> operationConsumer)
    {
        Model mavenModel = createMavenProjectModel(versionFileAccessContextProvider);
        String serializedMavenModel = serializeMavenModel(mavenModel);
        addOrModifyFile(MAVEN_MODEL_PATH, serializedMavenModel, fileAccessContext, operationConsumer);
    }

    protected Model createMavenProjectModel(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        ProjectConfiguration configuration = getProjectConfiguration();

        Model mavenModel = new Model();

        // Main attributes
        mavenModel.setModelVersion(getMavenModelVersion());
        mavenModel.setModelEncoding(getMavenModelEncoding());
        mavenModel.setGroupId(configuration.getGroupId());
        mavenModel.setArtifactId(configuration.getArtifactId());
        mavenModel.setVersion(getMavenModelProjectVersion());
        mavenModel.setPackaging(getMavenProjectModelPackaging());

        // Properties
        Properties properties = new SortedPropertiesForSerialization();
        addMavenProjectProperties(properties::setProperty);
        if (!properties.isEmpty())
        {
            mavenModel.setProperties(properties);
        }

        // Dependency management
        DependencyManagement dependencyManagement = new DependencyManagement();
        addMavenProjectDependencyManagement(versionFileAccessContextProvider, dependencyManagement::addDependency);
        if (!dependencyManagement.getDependencies().isEmpty())
        {
            mavenModel.setDependencyManagement(dependencyManagement);
        }


        // Dependencies
        addMavenProjectDependencies(versionFileAccessContextProvider, mavenModel::addDependency);

        // Plugins
        Build build = new Build();
        PluginManagement pluginManagement = new PluginManagement();
        addMavenProjectPluginManagement(versionFileAccessContextProvider, pluginManagement::addPlugin);
        if (!pluginManagement.getPlugins().isEmpty())
        {
            build.setPluginManagement(pluginManagement);
        }
        addMavenProjectPlugins(versionFileAccessContextProvider, build::addPlugin);
        if ((build.getPluginManagement() != null) || !build.getPlugins().isEmpty())
        {
            mavenModel.setBuild(build);
        }

        return mavenModel;
    }

    protected void addMavenProjectProperties(BiConsumer<String, String> propertySetter)
    {
        propertySetter.accept("project.build.sourceEncoding", getMavenModelSourceEncoding());
        propertySetter.accept("maven.compiler.source", getMavenModelJavaVersion());
        propertySetter.accept("maven.compiler.target", getMavenModelJavaVersion());
    }

    protected void addMavenProjectDependencyManagement(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        // None by default
    }

    protected void addMavenProjectDependencies(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        // None by default
    }

    protected void addMavenProjectPluginManagement(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        // None by default
    }

    protected void addMavenProjectPlugins(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        addMavenSourcePlugin(pluginConsumer, true);
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

    protected Stream<Dependency> getAllProjectDependenciesAsMavenDependencies(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getProjectDependencies().stream().flatMap(pd -> projectDependencyToAllMavenDependencies(pd, versionFileAccessContextProvider, withVersions));
    }

    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(ArtifactType artifactType, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getProjectDependenciesAsMavenDependencies(Collections.singletonList(artifactType), versionFileAccessContextProvider, withVersions);
    }

    public Stream<Dependency> getProjectDependenciesAsMavenDependencies(Collection<? extends ArtifactType> artifactTypes, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, boolean withVersions)
    {
        return getProjectDependencies().stream().flatMap(pd -> projectDependencyToMavenDependencies(pd, versionFileAccessContextProvider, artifactTypes, withVersions));
    }

    public static Stream<Dependency> projectDependencyToMavenDependenciesForType(ProjectDependency projectDependency, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, ArtifactType artifactType, boolean setVersion)
    {
        return projectDependencyToMavenDependencies(projectDependency, versionFileAccessContextProvider, Collections.singletonList(artifactType), setVersion);
    }

    public static Stream<Dependency> projectDependencyToAllMavenDependencies(ProjectDependency projectDependency, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, boolean setVersion)
    {
        return projectDependencyToMavenDependencies(projectDependency, versionFileAccessContextProvider, null, setVersion);
    }

    public static Stream<Dependency> projectDependencyToMavenDependencies(ProjectDependency projectDependency, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Collection<? extends ArtifactType> artifactTypes, boolean setVersion)
    {
        ProjectStructure versionStructure = getProjectStructureForProjectDependency(projectDependency, versionFileAccessContextProvider);
        ProjectConfiguration versionConfig = versionStructure.getProjectConfiguration();
        String groupId = versionConfig.getGroupId();
        String versionString = setVersion ? projectDependency.getVersionId().toVersionIdString() : null;
        Stream<String> stream = ((artifactTypes == null) || artifactTypes.isEmpty()) ? versionStructure.getAllArtifactIds() : versionStructure.getArtifactIds(artifactTypes);
        return stream.map(aid -> newMavenDependency(groupId, aid, versionString));
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

    public static Model getProjectMavenModel(ProjectFileAccessProvider.FileAccessContext accessContext)
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
        if (!(properties instanceof SortedPropertiesForSerialization))
        {
            SortedPropertiesForSerialization sortedProperties = new SortedPropertiesForSerialization();
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
        URL url = MavenProjectStructure.class.getClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        try (InputStream stream = url.openStream())
        {
            return IOTools.readAllToString(stream, charset);
        }
        catch (IOException e)
        {
            StringBuilder builder = new StringBuilder("Error reading resource '").append(resourceName).append("'");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
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

    protected static class SortedPropertiesForSerialization extends Properties
    {
        @Override
        public Set<Object> keySet()
        {
            Set<Object> ks = super.keySet();
            return new AbstractSet<Object>()
            {
                @Override
                public Iterator<Object> iterator()
                {
                    Object[] keys = ks.toArray();
                    Arrays.sort(keys);
                    return Arrays.asList(keys).iterator();
                }

                @Override
                public int size()
                {
                    return ks.size();
                }

                @Override
                public boolean contains(Object o)
                {
                    return ks.contains(o);
                }

                @Override
                public boolean remove(Object o)
                {
                    return ks.remove(o);
                }

                @Override
                public void clear()
                {
                    ks.clear();
                }
            };
        }
    }
}
