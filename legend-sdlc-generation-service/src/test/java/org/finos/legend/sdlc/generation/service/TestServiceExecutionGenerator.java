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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunner;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunnerInput;
import org.finos.legend.engine.plan.execution.nodes.helpers.platform.JavaHelper;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.result.json.JsonStreamingResult;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.plan.generation.transformers.LegendPlanTransformers;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.shared.core.url.StreamProvider;
import org.finos.legend.pure.generated.core_relational_relational_router_router_extension;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryFileManager;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.Rule;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class TestServiceExecutionGenerator
{
    private static String CLASSPATH;
    private static PureModelContextData PURE_MODEL_CONTEXT_DATA;
    private static PureModel PURE_MODEL;
    private static Map<String, Service> SERVICES;
    private static ObjectMapper OBJECT_MAPPER;

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    public Path generatedSourcesDirectory;
    public Path classesDirectory;

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
            PureModelBuilder.PureModelWithContextData pureModelWithContextData = PureModelBuilder.newBuilder().withEntities(entityLoader.getAllEntities()).build();
            PURE_MODEL_CONTEXT_DATA = pureModelWithContextData.getPureModelContextData();
            PURE_MODEL = pureModelWithContextData.getPureModel();
        }
        SERVICES = Iterate.groupByUniqueKey(PURE_MODEL_CONTEXT_DATA.getElementsOfType(Service.class), PackageableElement::getPath);
        OBJECT_MAPPER = new ObjectMapper();
    }

    @AfterClass
    public static void tearDown()
    {
        CLASSPATH = null;
        PURE_MODEL_CONTEXT_DATA = null;
        PURE_MODEL = null;
        SERVICES = null;
        OBJECT_MAPPER = null;
    }

    @Before
    public void setUpDirectories() throws Exception
    {
        this.generatedSourcesDirectory = this.tmpFolder.newFolder("generated-sources", "java").toPath();
        this.classesDirectory = this.tmpFolder.newFolder("classes").toPath();
    }

    @Test
    public void testSimpleService() throws Exception
    {
        String packagePrefix = "org.finos";
        Service service = getService("service::ModelToModelService");
        generateAndCompile(packagePrefix, service);
    }

    @Test
    public void testSimpleServiceWithParam() throws Exception
    {
        String packagePrefix = "org.finos";
        Service service = getService("service::ModelToModelServiceWithParam");
        ClassLoader classLoader = generateAndCompile(packagePrefix, service);
        assertExecuteMethods(classLoader, "org.finos.service.ModelToModelServiceWithParam", String.class);
    }

    @Test
    public void testSimpleMultiService() throws Exception
    {
        String packagePrefix = "org.finos";
        Service service = getService("service::ModelToModelServiceMulti");
        ClassLoader classLoader = generateAndCompile(packagePrefix, service);
        assertExecuteMethods(classLoader, "org.finos.service.ModelToModelServiceMulti", String.class);
    }

    @Test
    public void testSimpleRelational() throws Exception
    {
        String packagePrefix = "org.finos";
        Service service = getService("service::RelationalService");
        ClassLoader classLoader = generateAndCompile(packagePrefix, service);
        assertExecuteMethods(classLoader, "org.finos.service.RelationalService");
    }

    @Test
    public void testRelationalWithParams() throws Exception
    {
        String packagePrefix = "org.finos";
        Service service = getService("service::RelationalServiceWithParams");
        ClassLoader classLoader = generateAndCompile(packagePrefix, service);
        assertExecuteMethods(classLoader, "org.finos.service.RelationalServiceWithParams", String.class, String.class);
    }

    @Test
    public void testResultSimpleService() throws Exception
    {
        String packagePrefix = "org.finos";
        String servicePath = "service::ModelToModelService";
        Service service = getService(servicePath);
        ClassLoader classLoader = generateAndCompile(packagePrefix, service);
        assertExecuteMethods(classLoader, "org.finos.service.ModelToModelService");

        Class<?> executionClass = classLoader.loadClass("org.finos.service.ModelToModelService");
        Method method = executionClass.getMethod("execute", StreamProvider.class);
        Assert.assertTrue(AbstractServicePlanExecutor.class.isAssignableFrom(executionClass));
        AbstractServicePlanExecutor executor = (AbstractServicePlanExecutor) executionClass.getConstructor().newInstance();
        Assert.assertEquals(servicePath, executor.getServicePath());
        try (JavaHelper.ThreadContextClassLoaderScope scope = JavaHelper.withCurrentThreadContextClassLoader(classLoader))
        {
            assertJsonResult(
                    "{\"builder\":{\"_type\":\"json\"},\"values\":{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\"firstName\\\":\\\"firstName 73\\\",\\\"lastName\\\":\\\"lastName 79\\\",\\\"age\\\":27}\"}," +
                            "\"value\":{\"age\":27,\"firstName\":\"firstName 73\",\"lastName\":\"lastName 79\"}},\"value\":{\"age\":27,\"fullName\":\"firstName 73 lastName 79\"}}}",
                    (Result) method.invoke(executor, AbstractServicePlanExecutor.newStreamProvider("[{\"firstName\":\"John\", \"lastName\":\"Smith\", \"age\":\"10\" }]")));
            assertJsonResult(
                    "{\"builder\":{\"_type\":\"json\"},\"values\":{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\"firstName\\\":\\\"firstName 73\\\",\\\"lastName\\\":\\\"lastName 79\\\",\\\"age\\\":27}\"}," +
                            "\"value\":{\"age\":27,\"firstName\":\"firstName 73\",\"lastName\":\"lastName 79\"}},\"value\":{\"age\":27,\"fullName\":\"firstName 73 lastName 79\"}}}",
                    (Result) method.invoke(executor, AbstractServicePlanExecutor.newStreamProvider("[{\"firstName\":\"John\", \"lastName\":\"Smith\", \"age\":\"10\" }, {\"firstName\":\"John2\", \"lastName\":\"Smith2\", \"age\":\"10\" }]")));
            assertJsonResult(
                    "{\"builder\":{\"_type\":\"json\"},\"values\":{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\"firstName\\\":\\\"firstName 73\\\",\\\"lastName\\\":\\\"lastName 79\\\",\\\"age\\\":27}\"}," +
                            "\"value\":{\"age\":27,\"firstName\":\"firstName 73\",\"lastName\":\"lastName 79\"}},\"value\":{\"age\":27,\"fullName\":\"firstName 73 lastName 79\"}}}",
                    (Result) method.invoke(executor, AbstractServicePlanExecutor.newStreamProvider("[{\"firstName\":\"John\", \"lastName\":\"Smith\", \"age\":\"10\" }, {\"firstName\":\"John2\", \"lastName\":\"Smith2\", \"age\":\"10\" },  {\"firstName\":\"John3\", \"lastName\":\"Smith3\", \"age\":\"10\" }]")));

        }
    }

    @Test
    public void testAllServices() throws Exception
    {
        String packagePrefix = "io.biz";
        Collection<Service> services = getAllServices();
        ClassLoader classLoader = generateAndCompile(packagePrefix, services);
        assertExecuteMethods(classLoader, "io.biz.service.ModelToModelService");
        assertExecuteMethods(classLoader, "io.biz.service.ModelToModelServiceWithParam", String.class);
        assertExecuteMethods(classLoader, "io.biz.service.ModelToModelServiceMulti", String.class);
    }

    @Test
    public void testServiceExecutionUsingServiceRunnerAPI() throws Exception
    {
        ClassLoader classLoader = generateAndCompile("org.finos", getAllServices());

        try (JavaHelper.ThreadContextClassLoaderScope ignored = JavaHelper.withCurrentThreadContextClassLoader(classLoader))
        {
            // No Param Model To Model Service
            ServiceRunner noParamServiceRunner = findServiceRunnerByPath("service::ModelToModelService");

            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> noParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Collections.singletonList("a"))));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 0, Passed parameter size: 1", e.getMessage());

            String expectedOutput = "{\"builder\":{\"_type\":\"json\"},\"values\":{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\"firstName\\\":\\\"firstName 73\\\",\\\"lastName\\\":\\\"lastName 79\\\",\\\"age\\\":27}\"}," +
                    "\"value\":{\"age\":27,\"firstName\":\"firstName 73\",\"lastName\":\"lastName 79\"}},\"value\":{\"age\":27,\"fullName\":\"firstName 73 lastName 79\"}}}";
            Assert.assertEquals(OBJECT_MAPPER.readTree(expectedOutput), OBJECT_MAPPER.readTree(noParamServiceRunner.run(ServiceRunnerInput.newInstance())));


            // Multi Param Model To Model Service
            ServiceRunner multiParamServiceRunner = findServiceRunnerByPath("service::ModelToModelServiceWithMultipleParams");

            IllegalArgumentException e1 = Assert.assertThrows(IllegalArgumentException.class, () -> multiParamServiceRunner.run(ServiceRunnerInput.newInstance()));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 10, Passed parameter size: 0", e1.getMessage());

            IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2, 3.0))));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 10, Passed parameter size: 3", e2.getMessage());

            List<Object> args1 = Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
            IllegalArgumentException e3 = Assert.assertThrows(IllegalArgumentException.class, () -> multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(args1)));
            Assert.assertEquals("Unexpected parameter type. 'i_s' parameter (index: 0) should be of type 'String'", e3.getMessage());

            List<Object> args2 = Arrays.asList(null, null, 1, 1, 1, 1, 1, 1, 1, 1);
            IllegalArgumentException e4 = Assert.assertThrows(IllegalArgumentException.class, () -> multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(args2)));
            Assert.assertEquals("Unexpected parameter value. 'i_i' parameter (index: 1) should not be null", e4.getMessage());

            JsonNode expected = OBJECT_MAPPER.readTree("[{\"age\":22,\"fullName\":\"Peter Smith\"},{\"age\":23,\"fullName\":\"John Johnson\"}]");
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2, 3.0, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), 5, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE))));
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2L, 3.0D, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE))));
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2L, 3.0f, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE))));
            int x = 2;
            float y = 2.0f;
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, y, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE))));
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, x, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE))));
            Assert.assertEquals(expected, OBJECT_MAPPER.readTree(multiParamServiceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, x, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, null)).withSerializationFormat(SerializationFormat.PURE))));
        }
    }

    private ServiceRunner findServiceRunnerByPath(String servicePath)
    {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(ServiceLoader.load(ServiceRunner.class).iterator(), Spliterator.ORDERED), false)
                .filter(x -> servicePath.equals(x.getServicePath()))
                .findFirst().orElse(null);
    }

    private void assertExecuteMethods(ClassLoader classLoader, String className, Class<?>... parameterTypes)
    {
        try
        {
            Class<?> cls = classLoader.loadClass(className);
            TestServiceExecutionClassGenerator.assertExecuteMethodsExist(cls, parameterTypes);
        }
        catch (ClassNotFoundException e)
        {
            Assert.fail(e.getMessage());
        }
    }

    private Service getService(String servicePath)
    {
        return Objects.requireNonNull(SERVICES.get(servicePath), servicePath);
    }

    private Collection<Service> getAllServices()
    {
        return SERVICES.values();
    }

    private ClassLoader generateAndCompile(String packagePrefix, Service service) throws IOException
    {
        return generateAndCompile(packagePrefix, Collections.singletonList(service));
    }

    private ClassLoader generateAndCompile(String packagePrefix, Collection<? extends Service> services) throws IOException
    {
        // Generate
        for (Service service : services)
        {
            ServiceExecutionGenerator.newGenerator(service, PURE_MODEL, packagePrefix, this.generatedSourcesDirectory, this.classesDirectory, null, core_relational_relational_router_router_extension.Root_meta_pure_router_extension_defaultRelationalExtensions__RouterExtension_MANY_(PURE_MODEL.getExecutionSupport()), LegendPlanTransformers.transformers, "vX_X_X").generate();
        }

        // Check generated files
        String separator = this.tmpFolder.getRoot().toPath().getFileSystem().getSeparator();

        // Check execution plan resources generated
        Set<String> expectedResources = services.stream().map(s -> "plans" + separator + getPackagePrefix(packagePrefix, separator) + s.getPath().replace("::", separator) + ".json").collect(Collectors.toSet());
        expectedResources.add("META-INF" + separator + "services" + separator +  ServiceRunner.class.getCanonicalName());
        Set<String> actualResources = Files.walk(this.classesDirectory, Integer.MAX_VALUE).filter(Files::isRegularFile).map(this.classesDirectory::relativize).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(expectedResources, actualResources);

        // Check class files generated
        Set<String> expectedServiceJavaSources = services.stream().map(s -> getPackagePrefix(packagePrefix, separator) + s.getPath().replace("::", separator) + ".java").collect(Collectors.toSet());
        Set<String> actualJavaSources = Files.walk(this.generatedSourcesDirectory, Integer.MAX_VALUE).map(this.generatedSourcesDirectory::relativize).map(Path::toString).filter(s -> s.endsWith(".java")).collect(Collectors.toSet());
        if (!actualJavaSources.containsAll(expectedServiceJavaSources))
        {
            Assert.fail(expectedServiceJavaSources.stream().filter(c -> !actualJavaSources.contains(c)).sorted().collect(Collectors.joining(", ", "Missing Java sources: ", "")));
        }

        // Compile
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        MemoryFileManager fileManager = new MemoryFileManager(compiler);
        List<SourceFile> javaSources = Files.walk(this.generatedSourcesDirectory, Integer.MAX_VALUE).filter(p -> p.getFileName().toString().endsWith(".java")).map(SourceFile::new).collect(Collectors.toList());
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, Arrays.asList("-classpath", CLASSPATH), null, javaSources);
        if (!task.call())
        {
            Assert.fail(diagnosticCollector.getDiagnostics().toString());
        }
        fileManager.writeClassJavaSources(this.classesDirectory, false);

        // Create new classloader and check that everything expected is present
        ClassLoader classLoader = new URLClassLoader(new URL[]{this.classesDirectory.toUri().toURL()}, Thread.currentThread().getContextClassLoader());

        // Check plan resources
        List<String> missingPlanResources = services.stream()
                .map(s -> "plans/" + getPackagePrefix(packagePrefix, "/") + s.getPath().replace("::", "/") + ".json")
                .filter(n -> classLoader.getResource(n) == null)
                .sorted()
                .collect(Collectors.toList());
        Assert.assertEquals(Collections.emptyList(), missingPlanResources);

        // Check service execution classes
        List<String> missingServiceExecutionClasses = services.stream()
                .map(s -> getPackagePrefix(packagePrefix, ".") + s.getPath().replace("::", "."))
                .filter(n ->
                {
                    try
                    {
                        classLoader.loadClass(n);
                        return false;
                    }
                    catch (ClassNotFoundException e)
                    {
                        return true;
                    }
                })
                .sorted()
                .collect(Collectors.toList());
        Assert.assertEquals(Collections.emptyList(), missingServiceExecutionClasses);

        // Check ServiceRunner provider configuration file
        InputStream serviceRunnerResourceStream = classLoader.getResourceAsStream("META-INF" + separator + "services" + separator + ServiceRunner.class.getCanonicalName());
        Assert.assertNotNull("ServiceRunner provider configuration file missing", serviceRunnerResourceStream);
        List<String> serviceRunnerProviders = new BufferedReader(new InputStreamReader(serviceRunnerResourceStream)).lines().collect(Collectors.toList());
        List<String> missingServiceProviders = services.stream()
                .map(PackageableElement::getPath)
                .filter(path -> !serviceRunnerProviders.contains(getPackagePrefix(packagePrefix, ".") + path.replace("::", ".")))
                .sorted()
                .collect(Collectors.toList());
        Assert.assertEquals(Collections.emptyList(), missingServiceProviders);
        return classLoader;
    }

    private static String getPackagePrefix(String packagePrefix, String separator)
    {
        return (packagePrefix == null) ? "" : (packagePrefix.replace(".", separator) + separator);
    }

    private static void assertJsonResult(String expected, Result actual)
    {
        JsonNode expectedJson;
        JsonNode actualJson;
        try
        {
            expectedJson = OBJECT_MAPPER.readTree(expected);
            actualJson = getResultJson(actual);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(expectedJson, actualJson);
    }

    private static JsonNode getResultJson(Result result) throws IOException
    {
        if (result instanceof JsonStreamingResult)
        {
            JsonStreamingResult jsonResult = (JsonStreamingResult) result;
            String json = jsonResult.flush(jsonResult.getSerializer(SerializationFormat.DEFAULT));
            return OBJECT_MAPPER.readTree(json);
        }
        throw new IllegalArgumentException("Could not get Json from result: " + result);
    }

    private static class SourceFile extends SimpleJavaFileObject
    {
        private final Path path;

        private SourceFile(Path path)
        {
            super(path.toUri(), Kind.SOURCE);
            this.path = path;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException
        {
            return new String(Files.readAllBytes(this.path), StandardCharsets.UTF_8);
        }

        @Override
        public InputStream openInputStream() throws IOException
        {
            return Files.newInputStream(this.path);
        }
    }

}
