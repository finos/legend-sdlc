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
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectPaths;
import org.finos.legend.sdlc.server.project.ProjectStructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public abstract class MultiModuleMavenProjectStructure extends MavenProjectStructure
{
    private static final Pattern VALID_MODULE_NAME = Pattern.compile("\\w++(-\\w++)*+");

    public static final String ENTITY_VALIDATION_TEST_FILE_PATH = "/src/test/java/org/finos/legend/sdlc/EntityValidationTest.java";
    public static final String ENTITY_TEST_SUITE_FILE_PATH = "/src/test/java/org/finos/legend/sdlc/EntityTestSuite.java";

    private final String entitiesModuleName;
    private final Map<String, ArtifactType> otherModules;
    private final Map<String, Map<ModuleConfigType, Method>> moduleConfigMethods;
    private final boolean useDependencyManagement;

    protected MultiModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesModuleName, List<EntitySourceDirectory> sourceDirectories, Map<String, ArtifactType> otherModules, boolean useDependencyManagement)
    {
        super(projectConfiguration, validateEntitySourceDirectories(sourceDirectories, projectConfiguration, entitiesModuleName));
        this.entitiesModuleName = entitiesModuleName;
        this.otherModules = otherModules;
        this.moduleConfigMethods = getModuleConfigMethods();
        this.useDependencyManagement = useDependencyManagement;
    }

    protected MultiModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesModuleName, Map<String, ArtifactType> otherModules, boolean useDependencyManagement)
    {
        this(projectConfiguration, entitiesModuleName, Lists.fixedSize.with(getDefaultEntitySourceDirectory(projectConfiguration, entitiesModuleName)), otherModules, useDependencyManagement);
    }

    protected Stream<String> getGenerationModuleNames(ArtifactType type)
    {
        return getProjectConfiguration().getArtifactGenerations().stream().filter(art -> type == art.getType()).map(ArtifactGeneration::getName);
    }

    public Model getModuleMavenModel(String moduleName, ProjectFileAccessProvider.FileAccessContext accessContext)
    {
        String path = getModuleMavenModelPath(moduleName);
        return deserializeMavenModel(accessContext.getFile(path));
    }

    @Override
    public void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, ProjectFileAccessProvider.FileAccessContext fileAccessContext, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<ProjectFileOperation> operationConsumer)
    {
        super.collectUpdateProjectConfigurationOperations(oldStructure, fileAccessContext, versionFileAccessContextProvider, operationConsumer);

        if (oldStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure oldMultiStructure = (MultiModuleMavenProjectStructure) oldStructure;

            // entities module
            Model entitiesModel = createMavenEntitiesModuleModel(versionFileAccessContextProvider);
            String serializedEntitiesModel = serializeMavenModel(entitiesModel);
            moveOrAddOrModifyModuleFile(oldMultiStructure, oldMultiStructure.entitiesModuleName, MAVEN_MODEL_PATH, this.entitiesModuleName, MAVEN_MODEL_PATH, serializedEntitiesModel, fileAccessContext, operationConsumer);

            // other modules
            Set<String> oldOtherModuleNames = Sets.mutable.withAll(oldMultiStructure.otherModules.keySet());
            for (String otherModuleName : this.otherModules.keySet())
            {
                Model otherModuleModel = createMavenOtherModuleModel(otherModuleName, versionFileAccessContextProvider);
                String serializedOtherModuleModel = serializeMavenModel(otherModuleModel);
                if (oldOtherModuleNames.remove(otherModuleName))
                {
                    moveOrAddOrModifyModuleFile(oldMultiStructure, otherModuleName, MAVEN_MODEL_PATH, otherModuleName, MAVEN_MODEL_PATH, serializedOtherModuleModel, fileAccessContext, operationConsumer);
                }
                else
                {
                    addOrModifyModuleFile(otherModuleName, MAVEN_MODEL_PATH, serializedOtherModuleModel, fileAccessContext, operationConsumer);
                }
            }
            // remove other modules from old structure not in new structure
            oldOtherModuleNames.stream()
                    .map(oldMultiStructure::getModulePath)
                    .flatMap(fileAccessContext::getFilesInDirectory)
                    .map(ProjectFileAccessProvider.ProjectFile::getPath)
                    .map(ProjectFileOperation::deleteFile)
                    .forEach(operationConsumer);
        }
        else
        {
            // entities module
            Model entitiesModel = createMavenEntitiesModuleModel(versionFileAccessContextProvider);
            addOrModifyFile(getModuleMavenModelPath(this.entitiesModuleName), serializeMavenModel(entitiesModel), fileAccessContext, operationConsumer);

            // other modules
            for (String otherModuleName : this.otherModules.keySet())
            {
                Model otherModuleModel = createMavenOtherModuleModel(otherModuleName, versionFileAccessContextProvider);
                addOrModifyFile(getModuleMavenModelPath(otherModuleName), serializeMavenModel(otherModuleModel), fileAccessContext, operationConsumer);
            }
        }
    }

    @Override
    protected Model createMavenProjectModel(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        Model projectModel = super.createMavenProjectModel(versionFileAccessContextProvider);

        // Add modules
        projectModel.addModule(getModuleFullName(this.entitiesModuleName));
        this.otherModules.keySet().stream().map(this::getModuleFullName).forEach(projectModel::addModule);

        return projectModel;
    }

    @Override
    protected String getMavenProjectModelPackaging()
    {
        return POM_PACKAGING;
    }

    @Override
    protected void addMavenProjectDependencyManagement(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        String moduleVersion = getMavenModelProjectVersion();
        dependencyConsumer.accept(getModuleDependency(this.entitiesModuleName, moduleVersion));
        this.otherModules.keySet().stream().map(m -> getModuleDependency(m, moduleVersion)).forEach(dependencyConsumer);
        if (this.useDependencyManagement)
        {
            addJunitDependency(dependencyConsumer, true);
            addJacksonDependency(dependencyConsumer, true);
            getAllProjectDependenciesAsMavenDependencies(versionFileAccessContextProvider, true).forEach(dependencyConsumer);
        }
    }

    @Override
    public Stream<String> getAllArtifactIds()
    {
        return Stream.concat(Stream.of(this.entitiesModuleName), this.otherModules.keySet().stream()).map(this::getModuleFullName);
    }

    @Override
    public Stream<String> getArtifactIdsForType(ArtifactType type)
    {
        Stream<String> moduleNames = getModuleNamesForType(type);
        return (moduleNames == null) ? Stream.empty() : moduleNames.map(this::getModuleFullName);
    }

    public abstract Stream<String> getModuleNamesForType(ArtifactType type);

    public String getEntitiesModuleName()
    {
        return this.entitiesModuleName;
    }

    public Set<String> getOtherModulesNames()
    {
        return this.otherModules.keySet();
    }

    public boolean usesDependencyManagement()
    {
        return this.useDependencyManagement;
    }

    protected Model createMavenEntitiesModuleModel(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        Model mavenModel = createMavenModuleModel(this.entitiesModuleName);

        // Properties
        Properties properties = new SortedPropertiesForSerialization();
        addEntitiesModuleProperties(properties::setProperty);
        if (!properties.isEmpty())
        {
            mavenModel.setProperties(properties);
        }

        // Dependencies
        addEntitiesModuleDependencies(versionFileAccessContextProvider, mavenModel::addDependency);

        // Plugins
        Build build = new Build();
        addEntitiesModulePlugins(versionFileAccessContextProvider, build::addPlugin);
        if (!build.getPlugins().isEmpty())
        {
            mavenModel.setBuild(build);
        }

        return mavenModel;
    }

    protected void addEntitiesModuleProperties(BiConsumer<String, String> propertySetter)
    {
        // None by default
    }

    protected void addEntitiesModuleDependencies(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        addJunitDependency(dependencyConsumer, !this.useDependencyManagement);
        addJacksonDependency(dependencyConsumer, !this.useDependencyManagement);

        SortedMap<String, List<ProjectDependency>> dependenciesById = new TreeMap<>();
        getProjectDependencies().forEach(dep -> dependenciesById.computeIfAbsent(dep.getProjectId(), k -> Lists.mutable.empty()).add(dep));
        dependenciesById.values().stream().flatMap(dependencies ->
        {
            ArtifactType type = (dependencies.size() > 1) ? ArtifactType.versioned_entities : ArtifactType.entities;
            dependencies.sort(Comparator.naturalOrder());
            return dependencies.stream().flatMap(dep -> projectDependencyToMavenDependenciesForType(dep, versionFileAccessContextProvider, type, !this.useDependencyManagement));
        }).forEach(dependencyConsumer);
    }

    protected void addEntitiesModulePlugins(BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        // None by default
    }

    protected Model createMavenOtherModuleModel(String otherModuleName, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        Model mavenModel = createMavenModuleModel(otherModuleName);

        ArtifactType typeForConfig = otherModules.get(otherModuleName);
        Map<ModuleConfigType, Method> otherModuleConfigMethods = this.moduleConfigMethods.get(getModuleConfigName(typeForConfig));
        if (otherModuleConfigMethods != null)
        {
            // Properties
            Method propertiesMethod = otherModuleConfigMethods.get(ModuleConfigType.PROPERTIES);
            if (propertiesMethod != null)
            {
                try
                {
                    Properties properties = new SortedPropertiesForSerialization();
                    invokeConfigMethod(propertiesMethod, (BiConsumer<String, String>) properties::setProperty);
                    if (!properties.isEmpty())
                    {
                        mavenModel.setProperties(properties);
                    }
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven properties for module " + otherModuleName, e);
                }
            }

            // Module dependencies
            Method moduleDependenciesMethod = otherModuleConfigMethods.get(ModuleConfigType.MODULE_DEPENDENCIES);
            if (moduleDependenciesMethod != null)
            {
                try
                {
                    invokeConfigMethod(moduleDependenciesMethod, (Consumer<String>) m -> mavenModel.addDependency(getModuleWithNoVersionDependency(m)));
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven module dependencies for module " + otherModuleName, e);
                }
            }

            // Dependencies
            Method dependenciesMethod = otherModuleConfigMethods.get(ModuleConfigType.DEPENDENCIES);
            if (dependenciesMethod != null)
            {
                try
                {
                    invokeConfigMethod(dependenciesMethod, versionFileAccessContextProvider, (Consumer<Dependency>) mavenModel::addDependency);
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven dependencies for module " + otherModuleName, e);
                }
            }

            // Plugins
            Method pluginsMethod = otherModuleConfigMethods.get(ModuleConfigType.PLUGINS);
            if (pluginsMethod != null)
            {
                try
                {
                    Build build = new Build();
                    invokeConfigMethod(pluginsMethod, otherModuleName, versionFileAccessContextProvider, (Consumer<Plugin>) build::addPlugin);
                    if (!build.getPlugins().isEmpty())
                    {
                        mavenModel.setBuild(build);
                    }
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven plugins for module " + otherModuleName, e);
                }
            }
        }

        return mavenModel;
    }


    private void invokeConfigMethod(Method configMethod, Object... args) throws Exception
    {
        try
        {
            configMethod.invoke(this, args);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof Exception)
            {
                throw (Exception) cause;
            }
            if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            // Shouldn't happen, but just in case ...
            throw e;
        }
    }

    protected void addOrModifyModuleFile(String moduleName, String pathWithinModule, String newContent, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        addOrModifyFile(getModuleFilePath(moduleName, pathWithinModule), newContent, fileAccessContext, operationConsumer);
    }

    protected void moveOrAddOrModifyModuleFile(ProjectStructure oldStructure, String oldModule, String oldPath, String newModule, String newPath, String newContent, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        String oldFullPath = (oldStructure instanceof MultiModuleMavenProjectStructure) ? ((MultiModuleMavenProjectStructure) oldStructure).getModuleFilePath(oldModule, oldPath) : oldPath;
        String newFullPath = getModuleFilePath(newModule, newPath);
        moveOrAddOrModifyFile(oldFullPath, newFullPath, newContent, fileAccessContext, operationConsumer);
    }

    private Model createMavenModuleModel(String moduleName)
    {
        Model mavenModel = new Model();
        mavenModel.setModelVersion(getMavenModelVersion());
        mavenModel.setModelEncoding(getMavenModelEncoding());
        mavenModel.setGroupId(getProjectConfiguration().getGroupId());
        mavenModel.setArtifactId(getModuleFullName(moduleName));
        mavenModel.setVersion(getMavenModelProjectVersion());
        mavenModel.setPackaging(JAR_PACKAGING);

        Parent parent = new Parent();
        parent.setGroupId(getProjectConfiguration().getGroupId());
        parent.setArtifactId(getProjectConfiguration().getArtifactId());
        parent.setVersion(getMavenModelProjectVersion());
        mavenModel.setParent(parent);

        return mavenModel;
    }

    public String getModuleFullName(String moduleName)
    {
        return getModuleFullName(getProjectConfiguration(), moduleName);
    }

    protected String getModulePath(String moduleName)
    {
        return getModulePath(getProjectConfiguration(), moduleName, false);
    }

    public String getModuleFilePath(String moduleName, String relativeFilePath)
    {
        return getModulePath(moduleName) + (relativeFilePath.startsWith(ProjectPaths.PATH_SEPARATOR) ? "" : ProjectPaths.PATH_SEPARATOR) + relativeFilePath;
    }

    protected String getModuleMavenModelPath(String moduleName)
    {
        return getModuleFilePath(moduleName, MAVEN_MODEL_PATH);
    }

    protected Dependency getModuleWithParentVersionDependency(String moduleName)
    {
        return getModuleDependency(moduleName, "${project.parent.version}");
    }

    public Dependency getModuleWithNoVersionDependency(String moduleName)
    {
        return getModuleDependency(moduleName, null);
    }

    protected Dependency getModuleDependency(String moduleName, String version)
    {
        return newMavenDependency(getProjectConfiguration().getGroupId(), getModuleFullName(moduleName), version);
    }

    private Map<String, Map<ModuleConfigType, Method>> getModuleConfigMethods()
    {
        Map<String, Map<ModuleConfigType, Method>> moduleConfigMethods = Maps.mutable.ofInitialCapacity(this.otherModules.size());
        for (Class<?> cls = getClass(); (cls != MultiModuleMavenProjectStructure.class) && (cls != null); cls = cls.getSuperclass())
        {
            for (Method method : cls.getDeclaredMethods())
            {
                ModuleConfig moduleConfig = method.getAnnotation(ModuleConfig.class);
                if (moduleConfig != null)
                {
                    // TODO add validations on method signatures and multiple methods with the same
                    moduleConfigMethods.computeIfAbsent(getModuleConfigName(moduleConfig), n -> new EnumMap<>(ModuleConfigType.class)).putIfAbsent(moduleConfig.type(), method);
                }
            }
        }
        return moduleConfigMethods;
    }

    public static boolean isValidModuleName(String string)
    {
        return (string != null) && VALID_MODULE_NAME.matcher(string).matches();
    }

    public Map<ModuleConfigType, Method> getModuleConfig(ArtifactType artifactType)
    {
        return this.moduleConfigMethods.get(getDefaultModuleName(artifactType));
    }


    public enum ModuleConfigType
    {
        PROPERTIES, DEPENDENCIES, PLUGINS, MODULE_DEPENDENCIES
    }


    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ModuleConfig
    {
        ArtifactType artifactType();

        ModuleConfigType type();
    }

    public static String getModuleConfigName(ModuleConfig moduleConfig)
    {
        return getDefaultModuleName(moduleConfig.artifactType());
    }

    public static String getModuleConfigName(ArtifactType type)
    {
        return getDefaultModuleName(type);
    }

    public static String getDefaultModuleName(ArtifactType type)
    {
        return type.name().replace('_', '-').toLowerCase();
    }

    public ArtifactType getOtherModuleArtifactType(String otherModuleName)
    {
        return this.otherModules.get(otherModuleName);
    }

    protected static String getModuleFullName(ProjectConfiguration projectConfiguration, String moduleName)
    {
        return projectConfiguration.getArtifactId() + "-" + moduleName;
    }

    protected static String getModulePath(ProjectConfiguration projectConfiguration, String moduleName, boolean includeAfterSeparator)
    {
        return ProjectPaths.PATH_SEPARATOR + projectConfiguration.getArtifactId() + "-" + moduleName + (includeAfterSeparator ? ProjectPaths.PATH_SEPARATOR : "");
    }

    protected static EntitySourceDirectory getDefaultEntitySourceDirectory(ProjectConfiguration projectConfiguration, String entitiesModuleName)
    {
        return newEntitySourceDirectory(getModulePath(projectConfiguration, entitiesModuleName, false) + "/src/main/resources/entities", EntitySerializers.getDefaultJsonSerializer());
    }

    protected static List<EntitySourceDirectory> getDefaultEntitySourceDirectoriesForSerializers(ProjectConfiguration projectConfiguration, String entitiesModuleName, List<EntitySerializer> serializers)
    {
        String entitiesModuleDirectory = getModulePath(projectConfiguration, entitiesModuleName, false);
        return ListIterate.collect(serializers, s -> newEntitySourceDirectory(entitiesModuleDirectory + "/src/main/" + s.getName(), s));
    }

    private static <T extends EntitySourceDirectory> List<T> validateEntitySourceDirectories(List<T> sourceDirectories, ProjectConfiguration projectConfiguration, String entitiesModuleName)
    {
        String entitiesModuleDirectory = getModulePath(projectConfiguration, entitiesModuleName, true);
        if (ListIterate.anySatisfy(sourceDirectories, sd -> !sd.getDirectory().startsWith(entitiesModuleDirectory)))
        {
            throw new IllegalArgumentException(LazyIterate.select(sourceDirectories, sd -> !sd.getDirectory().startsWith(entitiesModuleDirectory)).makeString("Invalid entity source directories: ", ", ", ""));
        }
        return sourceDirectories;
    }
}
