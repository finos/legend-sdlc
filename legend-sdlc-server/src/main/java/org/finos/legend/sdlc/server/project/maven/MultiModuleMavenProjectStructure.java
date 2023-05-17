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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.SortedMaps;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.eclipse.collections.impl.utility.MapIterate;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectPaths;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;

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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class MultiModuleMavenProjectStructure extends MavenProjectStructure
{
    private static final Pattern VALID_MODULE_NAME = Pattern.compile("\\w++(-\\w++)*+");

    private final String entitiesModuleName;
    private final Map<String, ArtifactType> otherModules;
    private final boolean useDependencyManagement;

    @Deprecated
    private final Map<String, Map<ModuleConfigType, Method>> moduleConfigMethods;

    protected MultiModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesModuleName, List<EntitySourceDirectory> sourceDirectories, Map<String, ArtifactType> otherModules, boolean useDependencyManagement, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, validateEntitySourceDirectories(sourceDirectories, projectConfiguration, entitiesModuleName), projectStructurePlatformExtensions);
        this.entitiesModuleName = entitiesModuleName;
        this.otherModules = otherModules;
        this.useDependencyManagement = useDependencyManagement;
        this.moduleConfigMethods = getModuleConfigMethods();
    }

    protected MultiModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesModuleName, Map<String, ArtifactType> otherModules, boolean useDependencyManagement)
    {
        this(projectConfiguration, entitiesModuleName, Lists.fixedSize.with(getDefaultEntitySourceDirectory(projectConfiguration, entitiesModuleName)), otherModules, useDependencyManagement, null);
    }

    protected MultiModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, String entitiesModuleName, List<EntitySourceDirectory> sourceDirectories, Map<String, ArtifactType> otherModules, boolean useDependencyManagement)
    {
        this(projectConfiguration, entitiesModuleName, sourceDirectories, otherModules, useDependencyManagement, null);
    }

    @Deprecated
    protected Stream<String> getGenerationModuleNames(ArtifactType type)
    {
        return getProjectConfiguration().getArtifactGenerations().stream().filter(art -> type == art.getType()).map(ArtifactGeneration::getName);
    }

    public Model getModuleMavenModel(String moduleName, FileAccessContext accessContext)
    {
        String path = getModuleMavenModelPath(moduleName);
        return deserializeMavenModel(accessContext.getFile(path));
    }

    @Override
    protected void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        super.collectUpdateProjectConfigurationOperations(oldStructure, fileAccessContext, operationConsumer);

        if (oldStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure oldMultiStructure = (MultiModuleMavenProjectStructure) oldStructure;

            // entities module
            Model entitiesModel = createMavenEntitiesModuleModel();
            String serializedEntitiesModel = serializeMavenModel(entitiesModel);
            moveOrAddOrModifyModuleFile(oldMultiStructure, oldMultiStructure.entitiesModuleName, MAVEN_MODEL_PATH, this.entitiesModuleName, MAVEN_MODEL_PATH, serializedEntitiesModel, fileAccessContext, operationConsumer);

            // other modules
            MutableSet<String> oldOtherModuleNames = Sets.mutable.withAll(oldMultiStructure.otherModules.keySet());
            MapIterate.forEachKey(this.otherModules, otherModuleName ->
            {
                Model otherModuleModel = createMavenOtherModuleModel(otherModuleName);
                String serializedOtherModuleModel = serializeMavenModel(otherModuleModel);
                if (oldOtherModuleNames.remove(otherModuleName))
                {
                    moveOrAddOrModifyModuleFile(oldMultiStructure, otherModuleName, MAVEN_MODEL_PATH, otherModuleName, MAVEN_MODEL_PATH, serializedOtherModuleModel, fileAccessContext, operationConsumer);
                }
                else
                {
                    addOrModifyModuleFile(otherModuleName, MAVEN_MODEL_PATH, serializedOtherModuleModel, fileAccessContext, operationConsumer);
                }
            });
            // remove other modules from old structure not in new structure
            oldOtherModuleNames.forEach(mn -> fileAccessContext.getFilesInDirectory(getModulePath(mn))
                    .map(ProjectFileAccessProvider.ProjectFile::getPath)
                    .map(ProjectFileOperation::deleteFile)
                    .forEach(operationConsumer));
        }
        else
        {
            // entities module
            Model entitiesModel = createMavenEntitiesModuleModel();
            addOrModifyFile(getModuleMavenModelPath(this.entitiesModuleName), serializeMavenModel(entitiesModel), fileAccessContext, operationConsumer);

            // other modules
            MapIterate.forEachKey(this.otherModules, otherModuleName ->
            {
                Model otherModuleModel = createMavenOtherModuleModel(otherModuleName);
                addOrModifyFile(getModuleMavenModelPath(otherModuleName), serializeMavenModel(otherModuleModel), fileAccessContext, operationConsumer);
            });
        }
    }

    @Override
    protected String getMavenProjectModelPackaging()
    {
        return POM_PACKAGING;
    }

    @Override
    protected void configureMavenProjectModel(MavenModelConfiguration configuration)
    {
        super.configureMavenProjectModel(configuration);

        // Modules
        configuration.addModule(getModuleFullName(this.entitiesModuleName));
        MapIterate.forEachKey(this.otherModules, m -> configuration.addModule(getModuleFullName(m)));

        // Dependency Management
        String moduleVersion = getMavenModelProjectVersion();
        configuration.addDependencyManagement(getModuleDependency(this.entitiesModuleName, moduleVersion));
        MapIterate.forEachKey(this.otherModules, m -> configuration.addDependencyManagement(getModuleDependency(m, moduleVersion)));
        if (this.useDependencyManagement)
        {
            addJunitDependency(configuration::addDependencyManagement, true);
            addJacksonDependency(configuration::addDependencyManagement, true);
            getAllProjectDependenciesAsMavenDependencies(true).forEach(configuration::addDependencyManagement);
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

    protected Model createMavenEntitiesModuleModel()
    {
        // Module configuration
        MavenModelConfiguration mavenModelConfig = new MavenModelConfiguration();
        configureEntitiesModule(mavenModelConfig);

        // Call legacy configuration methods for backward compatibility
        addEntitiesModuleProperties(mavenModelConfig::setProperty);
        addEntitiesModuleDependencies(null, mavenModelConfig::addDependency);
        addEntitiesModulePlugins(null, mavenModelConfig::addPlugin);

        // Create and configure module
        Model mavenModel = createMavenModuleModel(this.entitiesModuleName);
        mavenModelConfig.configureModel(mavenModel);

        return mavenModel;
    }

    protected void configureEntitiesModule(MavenModelConfiguration configuration)
    {
        // Dependencies
        addJunitDependency(configuration::addDependency, !this.useDependencyManagement);
        addJacksonDependency(configuration::addDependency, !this.useDependencyManagement);

        MutableSortedMap<String, List<ProjectDependency>> dependenciesById = SortedMaps.mutable.empty();
        getProjectDependencies().forEach(dep -> dependenciesById.getIfAbsentPut(dep.getProjectId(), Lists.mutable::empty).add(dep));
        Comparator<ProjectDependency> comparator = getProjectDependencyComparator();
        dependenciesById.forEachValue(dependencies ->
        {
            dependencies.sort(comparator);
            ArtifactType type = (dependencies.size() > 1) ? ArtifactType.versioned_entities : ArtifactType.entities;
            dependencies.forEach(dep -> projectDependencyToMavenDependenciesForType(dep, type, !this.useDependencyManagement).forEach(configuration::addDependency));
        });
    }

    @Deprecated
    protected void addEntitiesModuleProperties(BiConsumer<String, String> propertySetter)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addEntitiesModuleDependencies(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Dependency> dependencyConsumer)
    {
        // retained for backward compatibility
    }

    @Deprecated
    protected void addEntitiesModulePlugins(BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<Plugin> pluginConsumer)
    {
        // retained for backward compatibility
    }

    protected Model createMavenOtherModuleModel(String otherModuleName)
    {
        MavenModelConfiguration mavenModelConfiguration = new MavenModelConfiguration();
        ArtifactType typeForConfig = this.otherModules.get(otherModuleName);
        configureOtherModule(typeForConfig, otherModuleName, mavenModelConfiguration);
        legacyConfigureOtherModule(typeForConfig, otherModuleName, mavenModelConfiguration);

        Model mavenModel = createMavenModuleModel(otherModuleName);
        mavenModelConfiguration.configureModel(mavenModel);
        return mavenModel;
    }

    protected void configureOtherModule(ArtifactType type, String name, MavenModelConfiguration configuration)
    {
        // Do nothing by default
    }

    private void legacyConfigureOtherModule(ArtifactType type, String name, MavenModelConfiguration configuration)
    {
        Map<ModuleConfigType, Method> otherModuleConfigMethods = this.moduleConfigMethods.get(getModuleConfigName(type));
        if (otherModuleConfigMethods != null)
        {
            // Properties
            Method propertiesMethod = otherModuleConfigMethods.get(ModuleConfigType.PROPERTIES);
            if (propertiesMethod != null)
            {
                try
                {
                    invokeConfigMethod(propertiesMethod, (BiConsumer<String, String>) configuration::setProperty);
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven properties for module " + name, e);
                }
            }

            // Module dependencies
            Method moduleDependenciesMethod = otherModuleConfigMethods.get(ModuleConfigType.MODULE_DEPENDENCIES);
            if (moduleDependenciesMethod != null)
            {
                try
                {
                    invokeConfigMethod(moduleDependenciesMethod, (Consumer<String>) m -> configuration.addDependency(getModuleWithNoVersionDependency(m)));
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven module dependencies for module " + name, e);
                }
            }

            // Dependencies
            Method dependenciesMethod = otherModuleConfigMethods.get(ModuleConfigType.DEPENDENCIES);
            if (dependenciesMethod != null)
            {
                try
                {
                    invokeConfigMethod(dependenciesMethod, null, (Consumer<Dependency>) configuration::addDependency);
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven dependencies for module " + name, e);
                }
            }

            // Plugins
            Method pluginsMethod = otherModuleConfigMethods.get(ModuleConfigType.PLUGINS);
            if (pluginsMethod != null)
            {
                try
                {
                    invokeConfigMethod(pluginsMethod, name, null, (Consumer<Plugin>) configuration::addPlugin);
                }
                catch (LegendSDLCServerException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new LegendSDLCServerException("Error adding maven plugins for module " + name, e);
                }
            }
        }
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

    protected void addOrModifyModuleFile(String moduleName, String pathWithinModule, String newContent, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        addOrModifyFile(getModuleFilePath(moduleName, pathWithinModule), newContent, fileAccessContext, operationConsumer);
    }

    protected void moveOrAddOrModifyModuleFile(ProjectStructure oldStructure, String oldModule, String oldPath, String newModule, String newPath, String newContent, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        String oldFullPath = (oldStructure instanceof MultiModuleMavenProjectStructure) ? ((MultiModuleMavenProjectStructure) oldStructure).getModuleFilePath(oldModule, oldPath) : oldPath;
        String newFullPath = getModuleFilePath(newModule, newPath);
        moveOrAddOrModifyFile(oldFullPath, newFullPath, newContent, fileAccessContext, operationConsumer);
    }

    protected void deleteModuleFileIfPresent(ProjectStructure structure, String moduleName, String pathWithinModule, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        String fullPath = (structure instanceof MultiModuleMavenProjectStructure) ? ((MultiModuleMavenProjectStructure) structure).getModuleFilePath(moduleName, pathWithinModule) : pathWithinModule;
        deleteFileIfPresent(fullPath, fileAccessContext, operationConsumer);
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

    protected Dependency getModuleWithProjectVersionDependency(String moduleName)
    {
        return getModuleDependency(moduleName, "${project.version}");
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

    @Deprecated
    public Map<ModuleConfigType, Method> getModuleConfig(ArtifactType artifactType)
    {
        return this.moduleConfigMethods.get(getDefaultModuleName(artifactType));
    }


    @Deprecated
    public enum ModuleConfigType
    {
        PROPERTIES, DEPENDENCIES, PLUGINS, MODULE_DEPENDENCIES
    }


    @Deprecated
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
