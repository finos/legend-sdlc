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
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
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
import java.util.Collection;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class LegendSDLCTestSuiteBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCTestSuiteBuilder.class);

    private final String pureVersion;

    public LegendSDLCTestSuiteBuilder(String pureVersion)
    {
        this.pureVersion = pureVersion;
    }

    public LegendSDLCTestSuiteBuilder()
    {
        this(null);
    }

    public static TestSuite buildTestSuite(String name, Path... entitiesDirectories)
    {
        return new LegendSDLCTestSuiteBuilder().buildSuite(name, entitiesDirectories);
    }

    public static TestSuite buildTestSuite(String name, EntityLoader entityLoader)
    {
        return new LegendSDLCTestSuiteBuilder().buildSuite(name, entityLoader);
    }

    public TestSuite buildSuite(String name, Path... entitiesDirectories)
    {
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(entitiesDirectories))
        {
            return buildSuite(name, entityLoader);
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public TestSuite buildSuite(String name, EntityLoader entityLoader)
    {
        return buildSuite(name, entityLoader, null);
    }

    public TestSuite buildSuite(String name, EntityLoader entityLoader, ClassLoader classLoader)
    {
        return buildSuite(name, entityLoader.getAllEntities().collect(Collectors.toList()), classLoader);
    }

    public TestSuite buildSuite(String name, Collection<? extends Entity> entitiesForTesting, ClassLoader classLoader)
    {
        if (classLoader == null)
        {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        PureModelWithContextData pureModelWithContextData = buildPureModelWithContextData(entitiesForTesting, classLoader);
        PureModel pureModel = pureModelWithContextData.getPureModel();
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        MutableMap<String, PackageableElement> pureModelContextDataIndex = Iterate.groupByUniqueKey(pureModelContextData.getElements(), PackageableElement::getPath);

        MutableList<PlanGeneratorExtension> extensions = Lists.mutable.withAll(ServiceLoader.load(PlanGeneratorExtension.class));
        RichIterable<? extends Root_meta_pure_extension_Extension> routerExtensions = extensions.flatCollect(e -> e.getExtraExtensions(pureModel));
        MutableList<PlanTransformer> planTransformers = extensions.flatCollect(PlanGeneratorExtension::getExtraPlanTransformers);

        TestSuite suite = new TestSuite();
        suite.setName(name);
        entitiesForTesting.forEach(entity ->
        {
            LOGGER.debug("Building test suite(s) for {} (classifier: {})", entity.getPath(), entity.getClassifierPath());

            PackageableElement protocolElement = pureModelContextDataIndex.get(entity.getPath());
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
                    TestSuite mappingTestSuite = buildTestSuite(entity, true, ListIterate.collect(mapping.tests, test -> new LegacyMappingTestCase(entity.getPath(), pureModel, test, planTransformers, routerExtensions, this.pureVersion)));
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
                    TestSuite serviceTestSuite = buildTestSuite(entity, true, new LegacyServiceTestCase(entity.getPath(), pureModel, pureModelContextData, service, planTransformers, routerExtensions, this.pureVersion));
                    int testCount = serviceTestSuite.testCount();
                    LOGGER.debug("  Legacy service test count for {}: {}", entity.getPath(), testCount);
                    totalTestCount += testCount;
                    suite.addTest(serviceTestSuite);
                }
            }

            // Testables
            org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement modelElement = pureModel.getPackageableElement(entity.getPath());
            if (modelElement instanceof Testable)
            {
                Testable testable = (Testable) modelElement;
                RichIterable<? extends Test> tests = testable._tests();
                if (tests.notEmpty())
                {
                    TestSuite testableTestSuite = buildTestSuite(entity, false, new TestableTestCase(entity.getPath(), pureModel, pureModelContextData));
                    int testCount = testableTestSuite.testCount();
                    LOGGER.debug("  Testable test count for {}: {}", entity.getPath(), testCount);
                    totalTestCount += testCount;
                    suite.addTest(testableTestSuite);
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

    private PureModelWithContextData buildPureModelWithContextData(Collection<? extends Entity> entitiesForTesting, ClassLoader classLoader)
    {
        MutableSet<String> entitiesForTestingPaths = Iterate.collect(entitiesForTesting, Entity::getPath, Sets.mutable.empty());
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(classLoader))
        {
            return PureModelBuilder.newBuilder()
                    .withEntitiesIfPossible(entitiesForTesting)
                    .withEntitiesIfPossible(entityLoader.getAllEntities().filter(e -> !entitiesForTestingPaths.contains(e.getPath())))
                    .build(classLoader);
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading entities", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException("Error loading entities", e);
        }
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
}
