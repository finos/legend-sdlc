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

package org.finos.legend.sdlc.server.project;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.project.maven.MavenProjectStructure;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class

TestMavenProjectStructure<T extends MavenProjectStructure> extends TestProjectStructure<T>
{
    @Override
    protected void testBuild(ProjectType projectType)
    {
        super.testBuild(projectType);
        Model mavenModel = MavenProjectStructure.getProjectMavenModel(this.fileAccessProvider.getProjectFileAccessContext(PROJECT_ID));
        Assert.assertNotNull(mavenModel);
        Assert.assertEquals(GROUP_ID, mavenModel.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, mavenModel.getArtifactId());
    }

    @Override
    protected void testUpdateGroupAndArtifactIds(ProjectType projectType)
    {
        super.testUpdateGroupAndArtifactIds(projectType);
        Model mavenModel = MavenProjectStructure.getProjectMavenModel(this.fileAccessProvider.getProjectFileAccessContext(PROJECT_ID));
        Assert.assertNotNull(mavenModel);
        Assert.assertEquals(GROUP_ID_2, mavenModel.getGroupId());
        Assert.assertEquals(ARTIFACT_ID_2, mavenModel.getArtifactId());
    }

    @Override
    protected void assertStateValid(T projectStructure, String projectId, String workspaceId, String revisionId)
    {
        super.assertStateValid(projectStructure, projectId, workspaceId, revisionId);

        ProjectFileAccessProvider.FileAccessContext fileAccessContext = this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId);

        // Check validity of the main pom.xml
        Model mavenModel = MavenProjectStructure.getProjectMavenModel(fileAccessContext);
        Assert.assertNotNull(mavenModel);
        assertMavenProjectModelValid(mavenModel, projectStructure);

        // Check the pom.xml of each module
        for (String module : Optional.ofNullable(mavenModel.getModules()).orElse(Collections.emptyList()))
        {
            String fileName = "/" + module + MavenProjectStructure.MAVEN_MODEL_PATH;
            ProjectFileAccessProvider.ProjectFile file = fileAccessContext.getFile(fileName);
            Assert.assertNotNull(fileName, file);
            Model moduleModel = MavenProjectStructure.deserializeMavenModel(file);
            Parent parent = moduleModel.getParent();
            Assert.assertNotNull(fileName, parent);
            Assert.assertEquals(fileName, mavenModel.getGroupId(), parent.getGroupId());
            Assert.assertEquals(fileName, mavenModel.getArtifactId(), parent.getArtifactId());
            Assert.assertEquals(fileName, mavenModel.getVersion(), parent.getVersion());
        }
    }

    protected void assertMavenProjectModelValid(Model mavenModel, T projectStructure)
    {
        assertMavenModelValid(
                mavenModel,
                projectStructure,
                ps -> ps.getProjectConfiguration().getGroupId(),
                ps -> ps.getProjectConfiguration().getArtifactId(),
                null,
                this::collectExpectedProjectProperties,
                this::collectExpectedProjectModelDependencies,
                this::collectExpectedProjectModelPlugins,
                this::collectExpectedProjectModelDependencyManagement,
                this::collectExpectedProjectModelModules);
    }

    protected void assertMavenModelValid(Model mavenModel, T projectStructure, Function<? super T, String> groupIdFn, Function<? super T, String> artifactIdFn, Function<? super T, Parent> parentFn, BiConsumer<? super T, BiConsumer<String, String>> propertyCollector, BiConsumer<? super T, Consumer<Dependency>> dependencyCollector, BiConsumer<? super T, Consumer<Plugin>> pluginCollector, BiConsumer<? super T, Consumer<Dependency>> dependencyManagementCollector, BiConsumer<? super T, Consumer<String>> moduleCollector)
    {
        assertMavenModelValid(null, mavenModel, projectStructure, groupIdFn, artifactIdFn, parentFn, propertyCollector, dependencyCollector, pluginCollector, dependencyManagementCollector, moduleCollector);
    }

    protected void assertMavenModelValid(String message, Model mavenModel, T projectStructure, Function<? super T, String> groupIdFn, Function<? super T, String> artifactIdFn, Function<? super T, Parent> parentFn, BiConsumer<? super T, BiConsumer<String, String>> propertyCollector, BiConsumer<? super T, Consumer<Dependency>> dependencyCollector, BiConsumer<? super T, Consumer<Plugin>> pluginCollector, BiConsumer<? super T, Consumer<Dependency>> dependencyManagementCollector, BiConsumer<? super T, Consumer<String>> moduleCollector)
    {
        Assert.assertEquals(message, groupIdFn.apply(projectStructure), mavenModel.getGroupId());
        Assert.assertEquals(message, artifactIdFn.apply(projectStructure), mavenModel.getArtifactId());
        Assert.assertEquals(message, getMavenModelVersion(), mavenModel.getModelVersion());
        Assert.assertEquals(message, getMavenModelEncoding(), mavenModel.getModelEncoding());
        Assert.assertEquals(message, getMavenModelProjectVersion(), mavenModel.getVersion());

        Parent expectedParent = (parentFn == null) ? null : parentFn.apply(projectStructure);
        assertParentsEqual(message, expectedParent, mavenModel.getParent());

        Properties expectedProperties = new Properties();
        if (propertyCollector != null)
        {
            propertyCollector.accept(projectStructure, expectedProperties::setProperty);
        }
        Assert.assertEquals(message, expectedProperties, mavenModel.getProperties());

        List<Dependency> expectedDependencies = Lists.mutable.empty();
        if (dependencyCollector != null)
        {
            dependencyCollector.accept(projectStructure, expectedDependencies::add);
        }
        assertDependencySetsEqual(message, expectedDependencies, mavenModel.getDependencies());

        List<Plugin> expectedPlugins = Lists.mutable.empty();
        if (pluginCollector != null)
        {
            pluginCollector.accept(projectStructure, expectedPlugins::add);
        }
        Build build = mavenModel.getBuild();
        assertPluginSetsEqual(message, expectedPlugins, (build == null) ? Collections.emptyList() : build.getPlugins());

        List<Dependency> expectedDependencyManagement = Lists.mutable.empty();
        if (dependencyManagementCollector != null)
        {
            dependencyManagementCollector.accept(projectStructure, expectedDependencyManagement::add);
        }
        DependencyManagement dependencyManagement = mavenModel.getDependencyManagement();
        assertDependencySetsEqual(message, expectedDependencyManagement, (dependencyManagement == null) ? Collections.emptyList() : dependencyManagement.getDependencies());

        Set<String> expectedModules = Sets.mutable.empty();
        if (moduleCollector != null)
        {
            moduleCollector.accept(projectStructure, expectedModules::add);
        }
        List<String> modules = mavenModel.getModules();
        Assert.assertEquals(expectedModules, (modules == null) ? Collections.emptySet() : Sets.mutable.withAll(modules));
    }

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

    protected void collectExpectedProjectProperties(T projectStructure, BiConsumer<String, String> propertyConsumer)
    {
        propertyConsumer.accept("project.build.sourceEncoding", getMavenModelSourceEncoding());
        propertyConsumer.accept("maven.compiler.source", getMavenModelJavaVersion());
        propertyConsumer.accept("maven.compiler.target", getMavenModelJavaVersion());
    }

    protected void collectExpectedProjectModelDependencies(T projectStructure, Consumer<Dependency> dependencyConsumer)
    {
    }

    protected void collectExpectedProjectModelPlugins(T projectStructure, Consumer<Plugin> pluginConsumer)
    {
        projectStructure.addMavenSourcePlugin(pluginConsumer, true);
    }

    protected void collectExpectedProjectModelDependencyManagement(T projectStructure, Consumer<Dependency> dependencyManagementConsumer)
    {
    }

    protected void collectExpectedProjectModelModules(T projectStructure, Consumer<String> moduleConsumer)
    {
    }

    protected static void assertDependencySetsEqual(Collection<Dependency> expected, Collection<Dependency> actual)
    {
        assertDependencySetsEqual(null, expected, actual);
    }

    protected static void assertDependencySetsEqual(String message, Collection<Dependency> expected, Collection<Dependency> actual)
    {
        Assert.assertEquals(message, toDependencyWrapperSortedSet(expected), toDependencyWrapperSortedSet(actual));
    }

    protected static void assertPluginSetsEqual(Collection<Plugin> expected, Collection<Plugin> actual)
    {
        assertPluginSetsEqual(null, expected, actual);
    }

    protected static void assertPluginSetsEqual(String message, Collection<Plugin> expected, Collection<Plugin> actual)
    {
        Assert.assertEquals(message, toPluginWrapperSortedSet(expected), toPluginWrapperSortedSet(actual));
    }

    protected static void assertParentsEqual(Parent expected, Parent actual)
    {
        assertParentsEqual(null, expected, actual);
    }

    protected static void assertParentsEqual(String message, Parent expected, Parent actual)
    {
        Assert.assertEquals(message, new ParentWrapper(expected), new ParentWrapper(actual));
    }

    private static Set<DependencyWrapper> toDependencyWrapperSet(Collection<? extends Dependency> dependencies)
    {
        return dependencies.stream().map(DependencyWrapper::new).collect(Collectors.toSet());
    }

    private static SortedSet<DependencyWrapper> toDependencyWrapperSortedSet(Collection<? extends Dependency> dependencies)
    {
        return dependencies.stream().map(DependencyWrapper::new).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<PluginWrapper> toPluginWrapperSet(Collection<? extends Plugin> dependencies)
    {
        return dependencies.stream().map(PluginWrapper::new).collect(Collectors.toSet());
    }

    private static SortedSet<PluginWrapper> toPluginWrapperSortedSet(Collection<? extends Plugin> dependencies)
    {
        return dependencies.stream().map(PluginWrapper::new).collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean dependencySetsEqual(Collection<? extends Dependency> set1, Collection<? extends Dependency> set2)
    {
        return (set1 == set2) || toDependencyWrapperSet(set1).equals(toDependencyWrapperSet(set2));
    }

    private static boolean dependenciesEqual(Dependency dep1, Dependency dep2)
    {
        if (dep1 == dep2)
        {
            return true;
        }

        return (dep1 != null) &&
                (dep2 != null) &&
                Objects.equals(dep1.getGroupId(), dep2.getGroupId()) &&
                Objects.equals(dep1.getArtifactId(), dep2.getArtifactId()) &&
                Objects.equals(dep1.getVersion(), dep2.getVersion()) &&
                Objects.equals(dep1.getScope(), dep2.getScope()) &&
                Objects.equals(dep1.getClassifier(), dep2.getClassifier()) &&
                Objects.equals(dep1.getType(), dep2.getType()) &&
                Objects.equals(dep1.getOptional(), dep2.getOptional()) &&
                listsEqual(dep1.getExclusions(), dep2.getExclusions(), TestMavenProjectStructure::exclusionsEqual);
    }

    private static int compareDependencies(Dependency dep1, Dependency dep2)
    {
        if (dep1 == dep2)
        {
            return 0;
        }

        Comparator<String> stringComparator = Comparator.nullsLast(String::compareTo);
        return Comparator.nullsLast(
                Comparator.comparing(Dependency::getGroupId, stringComparator)
                        .thenComparing(Dependency::getArtifactId, stringComparator)
                        .thenComparing(Dependency::getVersion, stringComparator)
                        .thenComparing(Dependency::getScope, stringComparator)
                        .thenComparing(Dependency::getClassifier, stringComparator)
                        .thenComparing(Dependency::getType, stringComparator)
                        .thenComparing(Dependency::getOptional, stringComparator)
                        .thenComparing(Dependency::getExclusions, (e1, e2) -> compareLists(e1, e2, TestMavenProjectStructure::compareExclusions))
        ).compare(dep1, dep2);
    }

    private static boolean exclusionsEqual(Exclusion excl1, Exclusion excl2)
    {
        if (excl1 == excl2)
        {
            return true;
        }
        return (excl1 != null) &&
                (excl2 != null) &&
                Objects.equals(excl1.getGroupId(), excl2.getGroupId()) &&
                Objects.equals(excl1.getArtifactId(), excl2.getArtifactId());
    }

    private static int compareExclusions(Exclusion excl1, Exclusion excl2)
    {
        if (excl1 == excl2)
        {
            return 0;
        }

        Comparator<String> stringComparator = Comparator.nullsLast(String::compareTo);
        return Comparator.nullsLast(
                Comparator.comparing(Exclusion::getGroupId, stringComparator)
                        .thenComparing(Exclusion::getArtifactId, stringComparator)
        ).compare(excl1, excl2);
    }

    private static boolean pluginsEqual(Plugin plugin1, Plugin plugin2)
    {
        if (plugin1 == plugin2)
        {
            return true;
        }

        return (plugin1 != null) &&
                (plugin2 != null) &&
                Objects.equals(plugin1.getGroupId(), plugin2.getGroupId()) &&
                Objects.equals(plugin1.getArtifactId(), plugin2.getArtifactId()) &&
                Objects.equals(plugin1.getVersion(), plugin2.getVersion()) &&
                Objects.equals(plugin1.getExtensions(), plugin2.getExtensions()) &&
                dependencySetsEqual(plugin1.getDependencies(), plugin2.getDependencies()) &&
                Objects.equals(plugin1.getConfiguration(), plugin2.getConfiguration()) &&
                listsEqual(plugin1.getExecutions(), plugin2.getExecutions(), TestMavenProjectStructure::pluginExecutionsEqual);
    }

    private static int comparePlugins(Plugin plugin1, Plugin plugin2)
    {
        if (plugin1 == plugin2)
        {
            return 0;
        }

        Comparator<String> stringComparator = Comparator.nullsLast(String::compareTo);
        return Comparator.nullsLast(
                Comparator.comparing(Plugin::getGroupId, stringComparator)
                        .thenComparing(Plugin::getArtifactId, stringComparator)
                        .thenComparing(Plugin::getVersion, stringComparator)
                        .thenComparing(Plugin::getExtensions, stringComparator)
                        .thenComparing(Plugin::getDependencies, (d1, d2) -> compareLists(d1, d2, TestMavenProjectStructure::compareDependencies))
                        .thenComparing(Plugin::getExecutions, (e1, e2) -> compareLists(e1, e2, TestMavenProjectStructure::comparePluginExecutions))
        ).compare(plugin1, plugin2);
    }

    private static boolean pluginExecutionsEqual(PluginExecution execution1, PluginExecution execution2)
    {
        if (execution1 == execution2)
        {
            return true;
        }

        return (execution1 != null) &&
                (execution2 != null) &&
                (execution1.getPriority() == execution2.getPriority()) &&
                Objects.equals(execution1.getPhase(), execution2.getPhase()) &&
                Objects.equals(execution1.getGoals(), execution2.getGoals());
    }

    private static int comparePluginExecutions(PluginExecution execution1, PluginExecution execution2)
    {
        if (execution1 == execution2)
        {
            return 0;
        }

        Comparator<String> stringComparator = Comparator.nullsLast(String::compareTo);
        return Comparator.nullsLast(
                Comparator.comparingInt(PluginExecution::getPriority)
                        .thenComparing(PluginExecution::getPhase, stringComparator)
                        .thenComparing(PluginExecution::getGoals, (g1, g2) -> compareLists(g1, g2, stringComparator))
        ).compare(execution1, execution2);
    }

    private static boolean parentsEqual(Parent parent1, Parent parent2)
    {
        if (parent1 == parent2)
        {
            return true;
        }

        return (parent1 != null) &&
                (parent2 != null) &&
                Objects.equals(parent1.getGroupId(), parent2.getGroupId()) &&
                Objects.equals(parent1.getArtifactId(), parent2.getArtifactId()) &&
                Objects.equals(parent1.getVersion(), parent2.getVersion());
    }

    private static <T> boolean listsEqual(List<? extends T> list1, List<? extends T> list2, BiPredicate<? super T, ? super T> equals)
    {
        if ((list1 == null) || list1.isEmpty())
        {
            return (list2 == null) || list2.isEmpty();
        }
        if ((list2 == null) || list2.isEmpty())
        {
            return false;
        }
        if (list1 == list2)
        {
            return true;
        }

        if (list1.size() != list2.size())
        {
            return false;
        }

        Iterator<? extends T> iter1 = list1.iterator();
        Iterator<? extends T> iter2 = list2.iterator();
        while (iter1.hasNext() && iter2.hasNext())
        {
            if (!equals.test(iter1.next(), iter2.next()))
            {
                return false;
            }
        }
        return !iter1.hasNext() && !iter2.hasNext();
    }

    private static <T> int compareLists(List<? extends T> list1, List<? extends T> list2, Comparator<? super T> comparator)
    {
        if (list1 == list2)
        {
            return 0;
        }
        if ((list1 == null) || list1.isEmpty())
        {
            return ((list2 == null) || list2.isEmpty()) ? 0 : -1;
        }
        if ((list2 == null) || list2.isEmpty())
        {
            return 1;
        }

        Iterator<? extends T> iter1 = list1.iterator();
        Iterator<? extends T> iter2 = list2.iterator();
        while (iter1.hasNext() && iter2.hasNext())
        {
            int cmp = comparator.compare(iter1.next(), iter2.next());
            if (cmp != 0)
            {
                return cmp;
            }
        }
        return iter1.hasNext() ? 1 : (iter2.hasNext() ? -1 : 0);
    }

    private static class DependencyWrapper implements Comparable<DependencyWrapper>
    {
        private final Dependency dependency;

        private DependencyWrapper(Dependency dependency)
        {
            this.dependency = dependency;
        }

        @Override
        public boolean equals(Object other)
        {
            if ((other == this) || (other == this.dependency))
            {
                return true;
            }

            if (other instanceof DependencyWrapper)
            {
                return dependenciesEqual(this.dependency, ((DependencyWrapper) other).dependency);
            }

            if (other instanceof Dependency)
            {
                return dependenciesEqual(this.dependency, (Dependency) other);
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return (this.dependency == null) ?
                    0 :
                    Objects.hash(
                            this.dependency.getGroupId(),
                            this.dependency.getArtifactId(),
                            this.dependency.getVersion(),
                            this.dependency.getScope(),
                            this.dependency.getClassifier(),
                            this.dependency.getType(),
                            this.dependency.getOptional()
                    );
        }

        @Override
        public int compareTo(DependencyWrapper other)
        {
            return (this == other) ? 0 : ((other == null) ? -1 : compareDependencies(this.dependency, other.dependency));
        }

        @Override
        public String toString()
        {
            return (this.dependency == null) ? "null" : appendString(new StringBuilder(64)).toString();
        }

        StringBuilder appendString(StringBuilder builder)
        {
            return builder.append("Dependency {groupId=")
                    .append(this.dependency.getGroupId())
                    .append(", artifactId=")
                    .append(this.dependency.getArtifactId())
                    .append(", version=")
                    .append(this.dependency.getVersion())
                    .append(", type=")
                    .append(this.dependency.getType())
                    .append(", classifier=")
                    .append(this.dependency.getClassifier())
                    .append(", scope=")
                    .append(this.dependency.getScope())
                    .append('}');
        }
    }

    private static class PluginWrapper implements Comparable<PluginWrapper>
    {
        private final Plugin plugin;

        private PluginWrapper(Plugin plugin)
        {
            this.plugin = plugin;
        }

        @Override
        public boolean equals(Object other)
        {
            if ((other == this) || (other == this.plugin))
            {
                return true;
            }

            if (other instanceof PluginWrapper)
            {
                return pluginsEqual(this.plugin, ((PluginWrapper) other).plugin);
            }

            if (other instanceof Plugin)
            {
                return pluginsEqual(this.plugin, (Plugin) other);
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return (this.plugin == null) ?
                    0 :
                    Objects.hash(
                            this.plugin.getGroupId(),
                            this.plugin.getArtifactId(),
                            this.plugin.getVersion()
                    );
        }

        @Override
        public int compareTo(PluginWrapper other)
        {
            return (this == other) ? 0 : ((other == null) ? -1 : comparePlugins(this.plugin, other.plugin));
        }

        @Override
        public String toString()
        {
            return (this.plugin == null) ? "null" : appendString(new StringBuilder(64)).toString();
        }

        StringBuilder appendString(StringBuilder builder)
        {
            return builder.append("Plugin {groupId=")
                    .append(this.plugin.getGroupId())
                    .append(", artifactId=")
                    .append(this.plugin.getArtifactId())
                    .append(", version=")
                    .append(this.plugin.getVersion())
                    .append(", dependencies=")
                    .append(this.plugin.getDependencies())
                    .append(", configuration=")
                    .append(this.plugin.getConfiguration())
                    .append('}');
        }
    }

    private static class ParentWrapper
    {
        private final Parent parent;

        private ParentWrapper(Parent parent)
        {
            this.parent = parent;
        }

        @Override
        public boolean equals(Object other)
        {
            if ((other == this) || (other == this.parent))
            {
                return true;
            }

            if (other instanceof ParentWrapper)
            {
                return parentsEqual(this.parent, ((ParentWrapper) other).parent);
            }

            if (other instanceof Parent)
            {
                return parentsEqual(this.parent, (Parent) other);
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return (this.parent == null) ?
                    0 :
                    Objects.hash(
                            this.parent.getGroupId(),
                            this.parent.getArtifactId(),
                            this.parent.getVersion()
                    );
        }

        @Override
        public String toString()
        {
            return String.valueOf(this.parent);
        }
    }
}
