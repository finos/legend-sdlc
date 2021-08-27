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
import org.finos.legend.engine.language.pure.compiler.Compiler;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunner;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunnerInput;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParser;
import org.finos.legend.engine.plan.execution.nodes.helpers.platform.JavaHelper;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.plan.generation.transformers.LegendPlanTransformers;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.pure.runtime.java.compiled.compiler.MemoryFileManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TestServiceRunnerExecution
{
    private static String CLASSPATH;
    private static String MODEL;
    private static PureModelContextData PURE_MODEL_CONTEXT_DATA;
    private static PureModel PURE_MODEL;
    private static Map<String, Service> SERVICES;
    @ClassRule public static TemporaryFolder TMP_FOLDER = TemporaryFolder.builder().build();
    private static Path GENERATE_SOURCES_DIRECTORY;
    private static Path CLASSES_DIRECTORY;
    private static ClassLoader CLASS_LOADER;

    @BeforeClass
    public static void setUp() throws Exception
    {
        CLASSPATH = new ClassGraph().getClasspath();
        MODEL = getModel();
        PURE_MODEL_CONTEXT_DATA = PureGrammarParser.newInstance().parseModel(MODEL);
        PURE_MODEL = Compiler.compile(PURE_MODEL_CONTEXT_DATA, null, null);
        SERVICES = PURE_MODEL_CONTEXT_DATA.getElementsOfType(Service.class).stream().collect(Collectors.toMap(PackageableElement::getPath, Function.identity()));
        GENERATE_SOURCES_DIRECTORY = TMP_FOLDER.newFolder("generated-sources", "java").toPath();
        CLASSES_DIRECTORY = TMP_FOLDER.newFolder("classes").toPath();
        CLASS_LOADER = generateAndCompile();
    }

    @AfterClass
    public static void tearDown()
    {
        CLASSPATH = null;
        MODEL = null;
        PURE_MODEL_CONTEXT_DATA = null;
        SERVICES = null;
        TMP_FOLDER = null;
        GENERATE_SOURCES_DIRECTORY = null;
        CLASSES_DIRECTORY = null;
        CLASS_LOADER = null;
    }

    @Test
    public void testNoParamServiceExecution()
    {
        runInClassLoader(() ->
        {
            ServiceRunner serviceRunner = findServiceRunnerByPath("test::NoParamService");

            // Passing parameter when service does not accept one
            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Collections.singletonList("a"))));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 0, Passed parameter size: 1", e.getMessage());

            String defaultOutput = serviceRunner.run(ServiceRunnerInput.newInstance());
            Assert.assertEquals("{\"builder\":{\"_type\":\"json\"},\"values\":[{\"firstName\":\"Peter\",\"lastName\":\"Smith\"},{\"firstName\":\"John\",\"lastName\":\"Johnson\"}]}", defaultOutput);

            String pureOutput = serviceRunner.run(ServiceRunnerInput.newInstance().withSerializationFormat(SerializationFormat.PURE));
            Assert.assertEquals("[{\"firstName\":\"Peter\",\"lastName\":\"Smith\"},{\"firstName\":\"John\",\"lastName\":\"Johnson\"}]", pureOutput);
        });
    }

    @Test
    public void testMultiParamServiceExecution()
    {
        runInClassLoader(() ->
        {
            ServiceRunner serviceRunner = findServiceRunnerByPath("test::MultiParamService");

            IllegalArgumentException e1 = Assert.assertThrows(IllegalArgumentException.class, () -> serviceRunner.run(ServiceRunnerInput.newInstance()));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 10, Passed parameter size: 0", e1.getMessage());

            IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2, 3.0))));
            Assert.assertEquals("Unexpected number of parameters. Expected parameter size: 10, Passed parameter size: 3", e2.getMessage());

            List<Object> args1 = Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
            IllegalArgumentException e3 = Assert.assertThrows(IllegalArgumentException.class, () -> serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(args1)));
            Assert.assertEquals("Unexpected parameter type. 'i_s' parameter (index: 0) should be of type 'String'", e3.getMessage());

            List<Object> args2 = Arrays.asList(null, null, 1, 1, 1, 1, 1, 1, 1, 1);
            IllegalArgumentException e4 = Assert.assertThrows(IllegalArgumentException.class, () -> serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(args2)));
            Assert.assertEquals("Unexpected parameter value. 'i_i' parameter (index: 1) should not be null", e4.getMessage());

            String expected = "[{\"firstName\":\"Peter\",\"lastName\":\"Smith\",\"stringQualifier($i_s)\":\"1\"},{\"firstName\":\"John\",\"lastName\":\"Johnson\",\"stringQualifier($i_s)\":\"1\"}]";
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2, 3.0, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), 5, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE)));
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2L, 3.0D, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE)));
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", 2L, 3.0f, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE)));
            int x = 2;
            float y = 2.0f;
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, y, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE)));
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, x, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, Arrays.asList(6, 7))).withSerializationFormat(SerializationFormat.PURE)));
            Assert.assertEquals(expected, serviceRunner.run(ServiceRunnerInput.newInstance().withArgs(Arrays.asList("1", x, x, new BigDecimal("4.0"), true, LocalDate.now(), ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC")), LocalDateTime.now(), null, null)).withSerializationFormat(SerializationFormat.PURE)));
        });
    }

    private ServiceRunner findServiceRunnerByPath(String servicePath)
    {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(ServiceLoader.load(ServiceRunner.class).iterator(), Spliterator.ORDERED), false)
                .filter(x -> servicePath.equals(x.getServicePath()))
                .findFirst().orElse(null);
    }

    private void runInClassLoader(Runnable toRun)
    {
        try (JavaHelper.ThreadContextClassLoaderScope ignored = JavaHelper.withCurrentThreadContextClassLoader(CLASS_LOADER))
        {
            toRun.run();
        }
    }

    private static ClassLoader generateAndCompile() throws IOException
    {
        for (Map.Entry<String, Service> service : SERVICES.entrySet())
        {
            ServiceExecutionGenerator.newGenerator(service.getValue(), PURE_MODEL, "org.finos", GENERATE_SOURCES_DIRECTORY, CLASSES_DIRECTORY, null, Lists.mutable.empty(), LegendPlanTransformers.transformers, "vX_X_X").generate();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        MemoryFileManager fileManager = new MemoryFileManager(compiler);
        List<TestServiceExecutionGenerator.SourceFile> javaSources = Files.walk(GENERATE_SOURCES_DIRECTORY, Integer.MAX_VALUE).filter(p -> p.getFileName().toString().endsWith(".java")).map(TestServiceExecutionGenerator.SourceFile::new).collect(Collectors.toList());
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, Arrays.asList("-classpath", CLASSPATH), null, javaSources);
        if (!task.call())
        {
            Assert.fail(diagnosticCollector.getDiagnostics().toString());
        }
        fileManager.writeClassJavaSources(CLASSES_DIRECTORY, false);

        return new URLClassLoader(new URL[]{CLASSES_DIRECTORY.toUri().toURL()}, Thread.currentThread().getContextClassLoader());
    }

    private static String getModel()
    {
        return "###Service\n" +
                "Service test::NoParamService\n" +
                "{\n" +
                "  pattern: '/noParamService';\n" +
                "  documentation: '';\n" +
                "  autoActivateUpdates: true;\n" +
                "  execution: Single\n" +
                "  {\n" +
                "    query: {|\n" +
                "let input = '[{\"fullName\": \"Peter Smith\"},{\"fullName\": \"John Johnson\"}]';\n" +
                "test::Person.all()->graphFetch(#{test::Person{firstName,lastName}}#)->serialize(#{test::Person{firstName,lastName}}#);\n" +
                "};\n" +
                "    mapping: test::Map;\n" +
                "    runtime: test::Runtime;\n" +
                "  }\n" +
                "  test: Single\n" +
                "  {\n" +
                "    data: '';\n" +
                "    asserts:\n" +
                "    [\n" +
                "    ];\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "Service test::MultiParamService\n" +
                "{\n" +
                "  pattern: '/multiParamService';\n" +
                "  documentation: '';\n" +
                "  autoActivateUpdates: true;\n" +
                "  execution: Single\n" +
                "  {\n" +
                "    query: {i_s: String[1],i_i: Integer[1],i_f: Float[1],i_dec: Decimal[1],i_b: Boolean[1],i_sd: StrictDate[1],i_dt: DateTime[1],i_d: Date[1],i_oi: Integer[0..1],i_li: Integer[*]|\n" +
                "let input = '[{\"fullName\": \"Peter Smith\"},{\"fullName\": \"John Johnson\"}]';\n" +
                "test::Person.all()->graphFetch(#{test::Person{firstName,lastName}}#)->serialize(#{test::Person{firstName,lastName,stringQualifier($i_s)}}#);\n" +
                "};\n" +
                "    mapping: test::Map;\n" +
                "    runtime: test::Runtime;\n" +
                "  }\n" +
                "  test: Single\n" +
                "  {\n" +
                "    data: '';\n" +
                "    asserts:\n" +
                "    [\n" +
                "    ];\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "\n" +
                "###Pure\n" +
                "Class test::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "  stringQualifier(s: String[1]) {$s}: String[1];\n" +
                "}\n" +
                "\n" +
                "Class test::S_Person\n" +
                "{\n" +
                "  fullName: String[1];\n" +
                "}\n" +
                "\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping test::Map\n" +
                "(\n" +
                "  test::Person: Pure\n" +
                "  {\n" +
                "    ~src test::S_Person\n" +
                "    firstName: $src.fullName->split(' ')->at(0),\n" +
                "    lastName: $src.fullName->split(' ')->at(1)\n" +
                "  }\n" +
                ")\n" +
                "\n" +
                "\n" +
                "###Runtime\n" +
                "Runtime test::Runtime\n" +
                "{\n" +
                "  mappings:\n" +
                "  [\n" +
                "    test::Map\n" +
                "  ];\n" +
                "  connections:\n" +
                "  [\n" +
                "    ModelStore:\n" +
                "    [\n" +
                "      connection_1:\n" +
                "      #{\n" +
                "        JsonModelConnection\n" +
                "        {\n" +
                "          class: test::S_Person;\n" +
                "          url: 'data:application/json,${input}';\n" +
                "        }\n" +
                "      }#\n" +
                "    ]\n" +
                "  ];\n" +
                "}\n";
    }

}
