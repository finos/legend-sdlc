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

package org.finos.legend.sdlc.generation.service;

import io.github.classgraph.ClassGraph;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunner;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunnerInput;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.shared.core.url.StreamProvider;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryClassLoader;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryFileManager;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class TestServiceExecutionClassGenerator
{
    private static PureModel PURE_MODEL;
    private static String CLASSPATH;
    private static PureModelContextData PURE_MODEL_CONTEXT_DATA;
    private static Map<String, Service> SERVICES;

    @BeforeClass
    public static void setUp() throws Exception
    {
        CLASSPATH = new ClassGraph().getClasspath();
        String resourceName = "org/finos/legend/sdlc/generation/service";
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(Paths.get(url.toURI())))
        {
            PURE_MODEL_CONTEXT_DATA = PureModelContextDataBuilder.newBuilder().withEntities(entityLoader.getAllEntities()).build();
            PureModelBuilder.PureModelWithContextData pureModelWithContextData = PureModelBuilder.newBuilder().withEntities(entityLoader.getAllEntities()).build();
            PURE_MODEL = pureModelWithContextData.getPureModel();
        }
        SERVICES = Iterate.groupByUniqueKey(PURE_MODEL_CONTEXT_DATA.getElementsOfType(Service.class), PackageableElement::getPath);
    }

    @AfterClass
    public static void tearDown()
    {
        CLASSPATH = null;
        PURE_MODEL_CONTEXT_DATA = null;
        SERVICES = null;
        PURE_MODEL = null;
    }

    @Test
    public void testSimpleService() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelService");
        assertExecuteMethodsExist(cls);
    }

    @Test
    public void testSimpleServiceWithParam() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithParam");
        assertExecuteMethodsExist(cls, String.class);
    }

    @Test
    public void testSimpleServiceWithZeroOneByteParam() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithZeroOneByteParam");
        assertExecuteMethodsExist(cls, Byte.class);
    }

    @Test
    public void testSimpleServiceWithZeroManyByteParam() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithZeroManyByteParam");
        assertExecuteMethodsExist(cls, InputStream.class);
    }

    @Test
    public void testSimpleServiceWithPureOneByteParam() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithPureOneByteParam");
        assertExecuteMethodsExist(cls, byte.class);
    }

    @Test
    public void testSimpleServiceWithNMultiplicityParam() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithNMultiplicityParam");
        assertExecuteMethodsExist(cls, List.class);
    }

    @Test
    public void testSimpleMultiService() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceMulti");
        assertExecuteMethodsExist(cls, String.class);
    }

    @Test
    public void testSimpleRelational() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::RelationalService");
        assertExecuteMethodsExist(cls);
    }

    @Test
    public void testSimpleRelationalWithParams() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::RelationalServiceWithParams");
        assertExecuteMethodsExist(cls, String.class, String.class);
    }

    @Test
    public void testSimpleRelationalWithEnumParams() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::RelationalServiceWithEnumParams");
        ClassLoader classLoader = cls.getClassLoader();
        assertExecuteMethodsExist(cls, classLoader.loadClass("org.finos.model.Country"), classLoader.loadClass("org.finos.model._enum.Country"), String.class);
    }

    @Test
    public void testModelToModelServiceWithMultipleParams() throws Exception
    {
        Class<?> cls = loadAndCompileService("org.finos", "service::ModelToModelServiceWithMultipleParams");
        assertExecuteMethodsExist(cls, String.class, long.class, double.class, BigDecimal.class, boolean.class, LocalDate.class, ZonedDateTime.class, Temporal.class, Long.class, List.class);
    }

    private Class<?> loadAndCompileService(String packagePrefix, String servicePath) throws Exception
    {
        Service service = SERVICES.get(servicePath);
        if (service == null)
        {
            throw new RuntimeException("Could not find service: " + servicePath);
        }
        MutableList<GeneratedJavaCode> generatedEnumClasses = Lists.mutable.empty();
        MutableMap<String, Enumeration<? extends Enum>> enumerations = Maps.mutable.empty();
        ((PureExecution) service.execution).func.parameters.forEach(p ->
        {
            if (!PrimitiveUtilities.isPrimitiveTypeName(p._class.path))
            {
                enumerations.getIfAbsentPut(p._class.path, () -> PURE_MODEL.getEnumeration(p._class.path, null));
            }
        });
        for (Pair<String, Enumeration<? extends Enum>> pair : enumerations.keyValuesView())
        {
            Enumeration<? extends Enum> enumeration = pair.getTwo();
            GeneratedJavaCode generatedEnumJavaClass = EnumerationClassGenerator.newGenerator(packagePrefix).withEnumeration(enumeration).generate();
            generatedEnumClasses.add(generatedEnumJavaClass);
            StringBuilder builder = new StringBuilder();
            org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.forEachUserPathElement(pair.getOne(), name -> ((builder.length() == 0) ? builder : builder.append('/')).append(JavaSourceHelper.toValidJavaIdentifier(name)));
            String path = builder.toString();
            Assert.assertEquals("Generated code matches expected formatting?", loadExpectedGeneratedJavaFile("generation/" + path + ".generated.java"), generatedEnumJavaClass.getText());
            compileGeneratedJavaClass(generatedEnumJavaClass, null);
        }
        GeneratedJavaCode generatedJavaClass = ServiceExecutionClassGenerator.newGenerator(packagePrefix)
                .withService(service)
                .withPlanResourceName("org/finos/legend/sdlc/generation/service/entities/" + servicePath.replace(EntityPaths.PACKAGE_SEPARATOR, "/").concat(".json"))
                .generate();
        String expectedClassName = ((packagePrefix == null) ? "" : (packagePrefix + ".")) + servicePath.replace(EntityPaths.PACKAGE_SEPARATOR, ".");
        Assert.assertEquals(expectedClassName, generatedJavaClass.getClassName());
        // Uncomment to update generated code
        // org.apache.commons.io.FileUtils.writeStringToFile(new java.io.File("src/test/resources/generation/service/" + service.name + ".generated.java"), generatedJavaClass.getCode(), StandardCharsets.UTF_8);
        Assert.assertEquals("Generated code matches expected formatting?", loadExpectedGeneratedJavaFile("generation/service/" + service.name + ".generated.java"), generatedJavaClass.getText());
        Class<?> cls = compileGeneratedJavaClass(generatedJavaClass, generatedEnumClasses);
        Assert.assertTrue(AbstractServicePlanExecutor.class.isAssignableFrom(cls));
        Assert.assertTrue(ServiceRunner.class.isAssignableFrom(cls));
        assertRunMethodsExist(cls);
        return cls;
    }

    private String loadExpectedGeneratedJavaFile(String resourceName)
    {
        URL url = getClass().getClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
        {
            String line = reader.readLine();
            if (line != null)
            {
                builder.append(line);
                while ((line = reader.readLine()) != null)
                {
                    builder.append('\n').append(line);
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading " + resourceName, e);
        }
        return builder.toString();
    }

    private Class<?> compileGeneratedJavaClass(GeneratedJavaCode generatedJavaClass, List<GeneratedJavaCode> generatedEnumClasses) throws ClassNotFoundException
    {
//        System.out.format("%s%n-------------------------%n%s%n", generatedJavaClass.getName(), generatedJavaClass.getCode());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        MemoryFileManager fileManager = new MemoryFileManager(compiler);
        MutableList<GeneratedJavaFileObject> javaClasses = Lists.mutable.with(new GeneratedJavaFileObject(generatedJavaClass));
        if (generatedEnumClasses != null)
        {
            generatedEnumClasses.forEach(e -> javaClasses.add(new GeneratedJavaFileObject(e)));
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, Arrays.asList("-classpath", CLASSPATH), null, javaClasses);
        if (!task.call())
        {
            Assert.fail(diagnosticCollector.getDiagnostics().toString());
        }
        return new MemoryClassLoader(fileManager, Thread.currentThread().getContextClassLoader()).loadClass(generatedJavaClass.getClassName());
    }

    static void assertExecuteMethodsExist(Class<?> cls, Class<?>... parameterTypes)
    {
        try
        {
            cls.getMethod("execute", parameterTypes);

            Class<?>[] parameterTypes2 = Arrays.copyOf(parameterTypes, parameterTypes.length + 1);
            parameterTypes2[parameterTypes.length] = StreamProvider.class;
            cls.getMethod("execute", parameterTypes2);
        }
        catch (NoSuchMethodException e)
        {
            Assert.fail(e.getMessage());
        }
    }

    static void assertRunMethodsExist(Class<?> cls)
    {
        try
        {
            cls.getMethod("run", ServiceRunnerInput.class);
            cls.getMethod("run", ServiceRunnerInput.class, OutputStream.class);
        }
        catch (NoSuchMethodException e)
        {
            Assert.fail(e.getMessage());
        }
    }

    private static class GeneratedJavaFileObject extends SimpleJavaFileObject
    {
        private final GeneratedJavaCode generatedJavaClass;

        private GeneratedJavaFileObject(GeneratedJavaCode generatedJavaClass)
        {
            super(URI.create("string:///" + generatedJavaClass.getClassName().replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.generatedJavaClass = generatedJavaClass;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors)
        {
            return this.generatedJavaClass.getText();
        }

        @Override
        public InputStream openInputStream()
        {
            return new ByteArrayInputStream(this.generatedJavaClass.getText().getBytes(StandardCharsets.UTF_8));
        }
    }
}
