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

package org.finos.legend.sdlc.test.junit;

import junit.framework.TestSuite;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.pure.code.core.LegendPureCoreExtension;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m3.coreinstance.meta.pure.test.Test;
import org.finos.legend.pure.m3.coreinstance.meta.pure.test.Testable;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder.PureModelWithContextData;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.test.junit.pure.v1.LegacyMappingTestCase;
import org.finos.legend.sdlc.test.junit.pure.v1.LegacyServiceTestCase;
import org.finos.legend.sdlc.test.junit.pure.v1.TestableTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LegendSDLCTestSuiteBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCTestSuiteBuilder.class);

    private final String name;
    private final String pureVersion;
    private final Set<String> testableClassifiers;
    private final ListIterable<Entity> entities;
    private final PureModel pureModel;
    private final PureModelContextData pureModelContextData;
    private final MapIterable<String, PackageableElement> protocolIndex;
    private final RichIterable<? extends Root_meta_pure_extension_Extension> routerExtensions;
    private final Iterable<? extends PlanTransformer> planTransformers;

    private LegendSDLCTestSuiteBuilder(String name, String pureVersion, ClassLoader classLoader)
    {
        this.name = name;
        this.pureVersion = pureVersion;
        this.testableClassifiers = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap(classLoader).keySet();
        this.entities = getEntities(classLoader);
        PureModelWithContextData pureModelWithContextData = PureModelBuilder.newBuilder().withEntitiesIfPossible(this.entities).withClassLoader(classLoader).build();
        this.pureModel = pureModelWithContextData.getPureModel();
        this.pureModelContextData = pureModelWithContextData.getPureModelContextData();
        this.protocolIndex = Iterate.groupByUniqueKey(this.pureModelContextData.getElements(), PackageableElement::getPath);
        this.routerExtensions = Iterate.flatCollect(ServiceLoader.load(LegendPureCoreExtension.class, classLoader), e -> e.extraPureCoreExtensions(this.pureModel.getExecutionSupport()), Lists.mutable.empty());
        this.planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class, classLoader), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
    }

    public LegendSDLCTestSuiteBuilder(String name, String pureVersion)
    {
        this(name, pureVersion, Thread.currentThread().getContextClassLoader());
    }

    public TestSuite buildSuiteFromDirectories(Path... directoriesForTesting)
    {
        MutableSet<String> entityPaths = Sets.mutable.empty();
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(directoriesForTesting))
        {
            entityLoader.getAllEntities().map(Entity::getPath).forEach(entityPaths::add);
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading entities for testing", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
        return buildSuiteFromEntityPaths(entityPaths);
    }

    public TestSuite buildSuiteFromEntityPaths(Iterable<String> entitiesForTestingPaths)
    {
        return buildSuiteFromEntityPaths(Sets.mutable.withAll(entitiesForTestingPaths));
    }

    private TestSuite buildSuiteFromEntityPaths(MutableSet<String> pathSet)
    {
        ListIterable<Entity> entitiesForTesting = this.entities.select(e -> pathSet.remove(e.getPath()));
        if (pathSet.notEmpty())
        {
            throw new RuntimeException(pathSet.toSortedList().makeString("Missing entities: ", ", ", ""));
        }
        return buildSuite(entitiesForTesting);
    }

    public TestSuite buildSuiteFromPackages(String... packagesForTesting)
    {
        return buildSuiteFromPackages(ArrayAdapter.adapt(packagesForTesting));
    }

    public TestSuite buildSuiteFromPackages(Iterable<String> packagesForTesting)
    {
        return buildSuiteFromPackages(packagesForTesting, null);
    }

    public TestSuite buildSuiteFromPackages(Iterable<String> includePackagesForTesting, Iterable<String> excludePackagesForTesting)
    {
        MutableList<String> includePrefixes = (includePackagesForTesting == null) ? null : Iterate.collect(includePackagesForTesting, pkg -> pkg + "::", Lists.mutable.empty());
        MutableList<String> excludePrefixes = (excludePackagesForTesting == null) ? null : Iterate.collect(excludePackagesForTesting, pkg -> pkg + "::", Lists.mutable.empty());
        return (includePrefixes == null) ?
                ((excludePrefixes == null) ?
                        buildSuite(this.entities) :
                        buildSuite(e -> excludePrefixes.noneSatisfy(p -> e.getPath().startsWith(p)))) :
                ((excludePrefixes == null) ?
                        buildSuite(e -> includePrefixes.anySatisfy(p -> e.getPath().startsWith(p))) :
                        buildSuite(e -> includePrefixes.anySatisfy(p -> e.getPath().startsWith(p)) && excludePrefixes.noneSatisfy(p -> e.getPath().startsWith(p))));
    }

    private TestSuite buildSuite(Predicate<? super Entity> entitiesForTestingPred)
    {
        return buildSuite((entitiesForTestingPred == null) ? this.entities : this.entities.select(entitiesForTestingPred::test));
    }

    private TestSuite buildSuite(ListIterable<? extends Entity> entitiesForTesting)
    {
        TestSuite suite = new TestSuite();
        suite.setName(this.name);
        entitiesForTesting.forEach(entity ->
        {
            LOGGER.debug("Building test suite(s) for {} (classifier: {})", entity.getPath(), entity.getClassifierPath());

            PackageableElement protocolElement = this.protocolIndex.get(entity.getPath());
            if (protocolElement == null)
            {
                return;
            }

            int totalTestCount = 0;

            // Legacy mapping tests
            if (protocolElement instanceof Mapping)
            {
                LOGGER.debug("Building legacy mapping test suite for {} (classifier: {})", entity.getPath(), entity.getClassifierPath());
                Mapping mapping = (Mapping) protocolElement;
                if ((mapping.tests != null) && !mapping.tests.isEmpty())
                {
                    TestSuite mappingTestSuite = buildTestSuite(entity, true, ListIterate.collect(mapping.tests, test -> new LegacyMappingTestCase(entity.getPath(), this.pureModel, test, this.planTransformers, this.routerExtensions, this.pureVersion)));
                    int testCount = mappingTestSuite.testCount();
                    LOGGER.debug("  Legacy mapping test count for {}: {}", entity.getPath(), testCount);
                    totalTestCount += testCount;
                    suite.addTest(mappingTestSuite);
                }
            }

            // Legacy service tests
            if (protocolElement instanceof Service)
            {
                Service service = (Service) protocolElement;
                if (service.test != null)
                {
                    TestSuite serviceTestSuite = buildTestSuite(entity, true, new LegacyServiceTestCase(entity.getPath(), this.pureModel, this.pureModelContextData, service, this.planTransformers, this.routerExtensions, this.pureVersion));
                    int testCount = serviceTestSuite.testCount();
                    LOGGER.debug("  Legacy service test count for {}: {}", entity.getPath(), testCount);
                    totalTestCount += testCount;
                    suite.addTest(serviceTestSuite);
                }
            }

            // Testables
            if (this.testableClassifiers.contains(entity.getClassifierPath()))
            {
                org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement modelElement = this.pureModel.getPackageableElement(entity.getPath());
                if (modelElement instanceof Testable)
                {
                    Testable testable = (Testable) modelElement;
                    RichIterable<? extends Test> tests = testable._tests();
                    if (tests.notEmpty())
                    {
                        TestSuite testableTestSuite = buildTestSuite(entity, false, new TestableTestCase(entity.getPath(), this.pureModel, this.pureModelContextData));
                        int testCount = testableTestSuite.testCount();
                        LOGGER.debug("  Testable test count for {}: {}", entity.getPath(), testCount);
                        totalTestCount += testCount;
                        suite.addTest(testableTestSuite);
                    }
                }
            }

            LOGGER.debug("Test count for {}: {}", entity.getPath(), totalTestCount);
        });

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Entity test suite count: {}", suite.testCount());
            LOGGER.debug("Entity test case count: {}", suite.countTestCases());
        }
        return suite;
    }

    private TestSuite buildTestSuite(Entity entity, boolean legacy, LegendSDLCTestCase testCase)
    {
        return buildTestSuite(entity, legacy, Lists.immutable.with(testCase));
    }

    private TestSuite buildTestSuite(Entity entity, boolean legacy, ListIterable<? extends LegendSDLCTestCase> testCases)
    {
        TestSuite suite = new TestSuite();
        suite.setName(entity.getPath() + " { " + (legacy ? "Specific" : "Generic") + " }");
        testCases.forEachWithIndex((testCase, i) ->
        {
            testCase.setName(entity.getPath() + " Test #" + (i + 1));
            suite.addTest(testCase);
        });
        return suite;
    }

    @SuppressWarnings("unused")
    public static TestSuite buildTestSuite(String name, Path... entitiesDirectories)
    {
        return new LegendSDLCTestSuiteBuilder(name, null).buildSuiteFromDirectories(entitiesDirectories);
    }

    @Deprecated
    public static TestSuite buildTestSuite(String name, EntityLoader entityLoader)
    {
        MutableSet<String> entityPaths = entityLoader.getAllEntities().map(Entity::getPath).collect(Collectors.toCollection(Sets.mutable::empty));
        return new LegendSDLCTestSuiteBuilder(name, null).buildSuiteFromEntityPaths(entityPaths);
    }

    private static MutableList<Entity> getEntities(ClassLoader classLoader)
    {
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(classLoader))
        {
            return entityLoader.getAllEntities().collect(Collectors.toCollection(Lists.mutable::empty));
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading entities", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException("Error loading entities", e);
        }
    }
}
