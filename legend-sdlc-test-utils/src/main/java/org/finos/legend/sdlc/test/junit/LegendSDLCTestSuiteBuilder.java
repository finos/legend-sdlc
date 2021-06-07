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
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.test.junit.pure.v1.MappingTestCase;
import org.finos.legend.sdlc.test.junit.pure.v1.ServiceTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
        Map<Class<? extends PackageableElement>, TestSuiteBuilder> testSuiteBuilders = getTestSuiteBuilderByTypeMap(MappingTestCase.class, ServiceTestCase.class);
        TestSuite suite = new TestSuite();
        suite.setName(name);
        Set<String> entitiesForTestingPaths = entitiesForTesting.stream().map(Entity::getPath).collect(Collectors.toSet());
        PureModelContextData pureModelContextData;
        PureModel pureModel;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader((classLoader == null) ? LegendSDLCTestSuiteBuilder.class.getClassLoader() : classLoader))
        {
            PureModelBuilder.PureModelWithContextData pureModelWithContextData = PureModelBuilder.newBuilder()
                    .withEntitiesIfPossible(entitiesForTesting)
                    .withEntitiesIfPossible(entityLoader.getAllEntities().filter(e -> !entitiesForTestingPaths.contains(e.getPath())))
                    .build(classLoader);
            pureModelContextData = pureModelWithContextData.getPureModelContextData();
            pureModel = pureModelWithContextData.getPureModel();
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading entities", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException("Error loading entities", e);
        }
        EntityToPureConverter converter = new EntityToPureConverter();
        entitiesForTesting.stream()
                .map(e ->
                {
                    Optional<TestSuiteBuilder> optionalBuilder = converter.fromEntityIfPossible(e).map(element -> testSuiteBuilders.get(element.getClass()));
                    if (!optionalBuilder.isPresent())
                    {
                        return null;
                    }
                    TestSuiteBuilder builder = optionalBuilder.get();
                    LOGGER.debug("Building test suite for {} (classifier: {})", e.getPath(), e.getClassifierPath());
                    TestSuite eSuite = builder.build(pureModel, pureModelContextData, e);
                    LOGGER.debug("Test count for {}: {}", e.getPath(), (eSuite == null) ? 0 : eSuite.testCount());
                    return eSuite;
                })
                .filter(Objects::nonNull)
                .forEach(suite::addTest);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Entity test suite count: {}", suite.testCount());
            LOGGER.debug("Entity test case count: {}", suite.countTestCases());
        }
        return suite;
    }

    private Map<Class<? extends PackageableElement>, TestSuiteBuilder> getTestSuiteBuilderByTypeMap(Class<?>... classes)
    {
        return getTestSuiteBuilderByTypeMap(Arrays.asList(classes));
    }

    private Map<Class<? extends PackageableElement>, TestSuiteBuilder> getTestSuiteBuilderByTypeMap(Iterable<? extends Class<?>> classes)
    {
        Map<Class<? extends PackageableElement>, TestSuiteBuilder> buildersByType = new HashMap<>();
        BiConsumer<Class<? extends PackageableElement>, Method> consumer = (collectorClass, method) ->
        {
            TestSuiteBuilder old = buildersByType.put(collectorClass, new TestSuiteBuilder(this.pureVersion, method));
            if (old != null)
            {
                throw new RuntimeException("Multiple " + LegendSDLCTestSuiteBuilder.class.getSimpleName() + " methods defined for " + collectorClass);
            }
        };
        classes.forEach(cls -> collectLegendSDLCTestCaseCollectorMethods(cls, consumer));
        return buildersByType;
    }

    private static void collectLegendSDLCTestCaseCollectorMethods(Class<?> cls, BiConsumer<Class<? extends PackageableElement>, Method> methodConsumer)
    {
        for (Method method : cls.getDeclaredMethods())
        {
            LegendSDLCTestCaseCollector annotation = method.getAnnotation(LegendSDLCTestCaseCollector.class);
            if (annotation != null)
            {
                if (!validateTestCaseCollectorMethod(method))
                {
                    throw new RuntimeException("Methods annotated with " + LegendSDLCTestSuiteBuilder.class.getSimpleName() + " must be public and static, and must have the following parameter types: PureModel, Entity, Consumer<? extends LegendSDLCTestCase<?>>; found: " + method.toGenericString());
                }
                methodConsumer.accept(annotation.collectorClass(), method);
            }
        }
    }

    private static boolean validateTestCaseCollectorMethod(Method method)
    {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers))
        {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        return (parameterTypes.length == 7) &&
                (parameterTypes[0] == PureModel.class) &&
                (parameterTypes[1] == PureModelContextData.class) &&
                (parameterTypes[2] == MutableList.class) &&
                (parameterTypes[3] == RichIterable.class) &&
                (parameterTypes[4] == String.class) &&
                (parameterTypes[5] == Entity.class) &&
                (parameterTypes[6] == Consumer.class);
    }

    private static class TestSuiteBuilder
    {
        private final Method method;

        private final String pureVersion;

        private TestSuiteBuilder(String pureVersion, Method method)
        {
            this.pureVersion = pureVersion;
            this.method = method;
        }

        TestSuite build(PureModel pureModel, PureModelContextData pureModelContextData, Entity entity)
        {
            List<LegendSDLCTestCase> testCases = new ArrayList<>();
            try
            {
                MutableList<PlanGeneratorExtension> extensions = Lists.mutable.withAll(ServiceLoader.load(PlanGeneratorExtension.class));
                RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> routerExtensions = extensions.flatCollect(e -> e.getExtraRouterExtensions(pureModel));
                MutableList<PlanTransformer> planTransformers = extensions.flatCollect(PlanGeneratorExtension::getExtraPlanTransformers);
                this.method.invoke(null, pureModel, pureModelContextData, planTransformers, routerExtensions, this.pureVersion, entity, (Consumer<? extends LegendSDLCTestCase>) testCases::add);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException("Error building test suite for " + entity.getPath(), e);
            }
            if (testCases.isEmpty())
            {
                return null;
            }

            TestSuite suite = new TestSuite();
            suite.setName(entity.getPath());
            int i = 1;
            for (LegendSDLCTestCase testCase : testCases)
            {
                testCase.setName(entity.getPath() + " Test #" + i);
                suite.addTest(testCase);
            }
            return suite;
        }
    }
}
