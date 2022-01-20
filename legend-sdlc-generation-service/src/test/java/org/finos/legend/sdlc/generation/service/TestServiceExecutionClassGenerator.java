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
import org.apache.commons.io.IOUtils;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunner;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunnerInput;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.shared.core.url.StreamProvider;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryClassLoader;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryFileManager;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TestServiceExecutionClassGenerator
{
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
        }
        SERVICES = Iterate.groupByUniqueKey(PURE_MODEL_CONTEXT_DATA.getElementsOfType(Service.class), PackageableElement::getPath);
    }

    @AfterClass
    public static void tearDown()
    {
        CLASSPATH = null;
        PURE_MODEL_CONTEXT_DATA = null;
        SERVICES = null;
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
        ServiceExecutionClassGenerator generator = ServiceExecutionClassGenerator.newGenerator(service, packagePrefix, "org/finos/legend/sdlc/generation/service/entities/" + servicePath.replace("::", "/").concat(".json"));
        ServiceExecutionClassGenerator.GeneratedJavaClass generatedJavaClass = generator.generate();
        String expectedClassName = ((packagePrefix == null) ? "" : (packagePrefix + ".")) + servicePath.replace("::", ".");
        Assert.assertEquals(expectedClassName, generatedJavaClass.getName());
        // Uncomment to update generated code
        // org.apache.commons.io.FileUtils.writeStringToFile(new java.io.File("src/test/resources/generation/service/" + service.name + ".generated.java"), generatedJavaClass.getCode(), StandardCharsets.UTF_8);
        Assert.assertEquals("Generated code matches expected formatting?", generatedJavaClass.getCode(), IOUtils.resourceToString("generation/service/" + service.name + ".generated.java", StandardCharsets.UTF_8, Thread.currentThread().getContextClassLoader()));
        Class<?> cls = compileGeneratedJavaClass(generatedJavaClass);
        Assert.assertTrue(AbstractServicePlanExecutor.class.isAssignableFrom(cls));
        Assert.assertTrue(ServiceRunner.class.isAssignableFrom(cls));
        assertRunMethodsExist(cls);
        return cls;
    }

    private Class<?> compileGeneratedJavaClass(ServiceExecutionClassGenerator.GeneratedJavaClass generatedJavaClass) throws ClassNotFoundException
    {
//        System.out.format("%s%n-------------------------%n%s%n", generatedJavaClass.getName(), generatedJavaClass.getCode());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        MemoryFileManager fileManager = new MemoryFileManager(compiler);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, Arrays.asList("-classpath", CLASSPATH), null, Collections.singletonList(new GeneratedJavaFileObject(generatedJavaClass)));
        if (!task.call())
        {
            Assert.fail(diagnosticCollector.getDiagnostics().toString());
        }
        return new MemoryClassLoader(fileManager, Thread.currentThread().getContextClassLoader()).loadClass(generatedJavaClass.getName());
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
        private final ServiceExecutionClassGenerator.GeneratedJavaClass generatedJavaClass;

        private GeneratedJavaFileObject(ServiceExecutionClassGenerator.GeneratedJavaClass generatedJavaClass)
        {
            super(URI.create("string:///" + generatedJavaClass.getName().replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.generatedJavaClass = generatedJavaClass;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors)
        {
            return this.generatedJavaClass.getCode();
        }

        @Override
        public InputStream openInputStream()
        {
            return new ByteArrayInputStream(this.generatedJavaClass.getCode().getBytes(StandardCharsets.UTF_8));
        }
    }
}
