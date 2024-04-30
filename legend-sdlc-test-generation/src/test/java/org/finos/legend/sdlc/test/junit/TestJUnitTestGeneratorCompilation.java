// Copyright 2023 Goldman Sachs
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

import io.github.classgraph.ClassGraph;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.test.junit.JUnitTestGenerator;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class TestJUnitTestGeneratorCompilation
{
    private static final String ROOT_PACKAGE = "org.finos.legend.sdlc.test.junit.junit4";
    private static String CLASSPATH;
    private static EntityLoader ENTITY_LOADER;

    @BeforeClass
    public static void setUp()
    {
        CLASSPATH = new ClassGraph().getClasspath();
        ENTITY_LOADER = EntityLoader.newEntityLoader(Thread.currentThread().getContextClassLoader());
    }

    @AfterClass
    public static void cleanUp() throws Exception
    {
        CLASSPATH = null;
        if (ENTITY_LOADER != null)
        {
            ENTITY_LOADER.close();
            ENTITY_LOADER = null;
        }
    }

    @Test
    public void testRelationalMapping()
    {
        testCompilation("execution.TestRelationalMapping", "execution::RelationalMapping");
    }

    @Test
    public void testSingleQuoteInResultM2M()
    {
        testCompilation("legend.demo.TestSingleQuoteInResultM2M", "legend::demo::SingleQuoteInResultM2M");
    }

    @Test
    public void testSourceToTargetM2M()
    {
        testCompilation("model.mapping.TestSourceToTargetM2M", "model::mapping::SourceToTargetM2M");
    }

    @Test
    public void testTestService()
    {
        testCompilation("testTestSuites.TestTestService", "testTestSuites::TestService");
    }

    @Test
    public void testTestService2()
    {
        testCompilation("testTestSuites.TestTestService2", "testTestSuites::TestService2");
    }

    @Test
    public void testServiceStoreMapping()
    {
        testCompilation("testTestSuites.TestServiceStoreMapping", "testTestSuites::ServiceStoreMapping");
    }

    private void testCompilation(String expectedClassName, String entityPath)
    {
        testCompilation(Collections.singletonList(expectedClassName), entityPath);
    }

    private void testCompilation(Iterable<String> expectedClassNames, String entityPath)
    {
        Entity entity = ENTITY_LOADER.getEntity(entityPath);
        Assert.assertNotNull(entityPath, entity);

        List<GeneratedJavaCode> generatedCode = JUnitTestGenerator.newGenerator(ROOT_PACKAGE).generateTestClasses(entity);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        InMemoryFileManager fileManager = new InMemoryFileManager(compiler);
        Collection<JavaFileObject> toCompile = Iterate.collect(generatedCode, GeneratedJavaFileObject::new);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, Arrays.asList("-classpath", CLASSPATH), null, toCompile);
        if (!task.call())
        {
            Assert.fail(diagnosticCollector.getDiagnostics().toString());
        }
        MutableList<String> missingClasses = Iterate.reject(expectedClassNames, className -> fileManager.classes.containsKey(ROOT_PACKAGE + "." + className), Lists.mutable.empty()).sortThis();
        Assert.assertEquals("Missing classes", Lists.fixedSize.empty(), missingClasses);
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

    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager>
    {
        private final MutableMap<String, InMemoryJavaFileObject> classes = Maps.mutable.empty();

        /**
         * Creates a new instance of ForwardingJavaFileManager.
         *
         * @param fileManager delegate to this file manager
         */
        private InMemoryFileManager(StandardJavaFileManager fileManager)
        {
            super(fileManager);
        }

        private InMemoryFileManager(JavaCompiler compiler)
        {
            this(compiler.getStandardFileManager(null, null, null));
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException
        {
            return (kind == JavaFileObject.Kind.CLASS) ?
                    this.classes.getIfAbsentPutWithKey(className, InMemoryJavaFileObject::new) :
                    super.getJavaFileForOutput(location, className, kind, sibling);
        }
    }

    private static class InMemoryJavaFileObject extends SimpleJavaFileObject
    {
        private byte[] bytes = new byte[0];

        protected InMemoryJavaFileObject(String className)
        {
            super(URI.create("memo:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public InputStream openInputStream()
        {
            return new ByteArrayInputStream(this.bytes);
        }

        @Override
        public OutputStream openOutputStream()
        {
            return new ByteArrayOutputStream()
            {
                @Override
                public synchronized void close()
                {
                    InMemoryJavaFileObject.this.bytes = toByteArray();
                }
            };
        }
    }
}
