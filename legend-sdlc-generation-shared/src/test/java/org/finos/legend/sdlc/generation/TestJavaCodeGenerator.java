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

package org.finos.legend.sdlc.generation;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TestJavaCodeGenerator
{
    @Test
    public void testInvalidLineBreak()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> new SimpleJavaInterfaceGenerator("br"));
        Assert.assertEquals("Invalid line break: \"br\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> new SimpleJavaInterfaceGenerator(null).withLineBreak("not a line break"));
        Assert.assertEquals("Invalid line break: \"not a line break\"", e.getMessage());
    }

    @Test
    public void testSimpleInterface()
    {
        GeneratedJavaCode generated = new SimpleJavaInterfaceGenerator("\n")
                .withPackageName("org.finos.sdlc.generation.test")
                .withClassName("TestInterface")
                .withImport("org.finos.sdlc.generation.more.SuperInterface1")
                .withImport("org.finos.sdlc.generation.other.SuperInterface2")
                .withExtends("SuperInterface1", "SuperInterface2")
                .withMethod("getName", "String")
                .withMethod("setName", "void", new MethodParameter("String", "name"))
                .withMethod("getId", "int")
                .withMethod("setId", "void", new MethodParameter("int", "id"))
                .withMethod("joinNameAndId", "String", new MethodParameter("String", "prefix"), new MethodParameter("String", "join"), new MethodParameter("String", "suffix"))
                .generate();
        Assert.assertEquals("org.finos.sdlc.generation.test.TestInterface", generated.getClassName());
        String expectedText = "package org.finos.sdlc.generation.test;\n" +
                "\n" +
                "import org.finos.sdlc.generation.more.SuperInterface1;\n" +
                "import org.finos.sdlc.generation.other.SuperInterface2;\n" +
                "\n" +
                "public interface TestInterface extends SuperInterface1, SuperInterface2\n" +
                "{\n" +
                "    String getName();\n" +
                "    void setName(String name);\n" +
                "    int getId();\n" +
                "    void setId(int id);\n" +
                "    String joinNameAndId(String prefix, String join, String suffix);\n" +
                "}\n";
        Assert.assertEquals(expectedText, generated.getText());
    }

    @Test
    public void testSimpleInterfaceCarriageReturn()
    {
        GeneratedJavaCode generated = new SimpleJavaInterfaceGenerator("\r\n")
                .withPackageName("org.finos.sdlc.generation.test")
                .withClassName("TestInterface")
                .withImport("org.finos.sdlc.generation.more.SuperInterface1")
                .withImport("org.finos.sdlc.generation.other.SuperInterface2")
                .withExtends("SuperInterface2", "SuperInterface1")
                .withMethod("getName", "String")
                .withMethod("setName", "void", new MethodParameter("String", "name"))
                .withMethod("getId", "int")
                .withMethod("setId", "void", new MethodParameter("int", "id"))
                .withMethod("joinNameAndId", "String", new MethodParameter("String", "prefix"), new MethodParameter("String", "join"), new MethodParameter("String", "suffix"))
                .generate();
        Assert.assertEquals("org.finos.sdlc.generation.test.TestInterface", generated.getClassName());
        String expectedText = "package org.finos.sdlc.generation.test;\r\n" +
                "\r\n" +
                "import org.finos.sdlc.generation.more.SuperInterface1;\r\n" +
                "import org.finos.sdlc.generation.other.SuperInterface2;\r\n" +
                "\r\n" +
                "public interface TestInterface extends SuperInterface2, SuperInterface1\r\n" +
                "{\r\n" +
                "    String getName();\r\n" +
                "    void setName(String name);\r\n" +
                "    int getId();\r\n" +
                "    void setId(int id);\r\n" +
                "    String joinNameAndId(String prefix, String join, String suffix);\r\n" +
                "}\r\n";
        Assert.assertEquals(expectedText, generated.getText());
    }

    @Test
    public void testInvalidPackage()
    {
        SimpleJavaInterfaceGenerator generator = new SimpleJavaInterfaceGenerator("\n");

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withPackageName(null));
        Assert.assertEquals("Invalid package name: null", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withPackageName(""));
        Assert.assertEquals("Invalid package name: \"\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withPackageName("abc.def.1gh"));
        Assert.assertEquals("Invalid package name: \"abc.def.1gh\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withPackageName("abc.d#f.ghi"));
        Assert.assertEquals("Invalid package name: \"abc.d#f.ghi\"", e.getMessage());
    }

    @Test
    public void testInvalidClassName()
    {
        SimpleJavaInterfaceGenerator generator = new SimpleJavaInterfaceGenerator("\n");

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withClassName(null));
        Assert.assertEquals("Invalid class name: null", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withClassName(""));
        Assert.assertEquals("Invalid class name: \"\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withClassName("1MyClass"));
        Assert.assertEquals("Invalid class name: \"1MyClass\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> generator.withClassName("My#Class"));
        Assert.assertEquals("Invalid class name: \"My#Class\"", e.getMessage());
    }

    public static class MethodSpec
    {
        public final String name;
        public final String returnType;
        public final List<MethodParameter> parameters;

        MethodSpec(String name, String returnType, MethodParameter... parameters)
        {
            this.name = name;
            this.returnType = returnType;
            this.parameters = Collections.unmodifiableList(Arrays.asList(parameters));
        }
    }

    public static class MethodParameter
    {
        public final String type;
        public final String name;

        MethodParameter(String type, String name)
        {
            this.type = type;
            this.name = name;
        }
    }

    private static class SimpleJavaInterfaceGenerator extends JavaCodeGenerator
    {
        protected SimpleJavaInterfaceGenerator(String defaultLineBreak)
        {
            super(GeneratorTemplate.fromResource("org/finos/legend/sdlc/generation/javaTemplate.ftl"), defaultLineBreak);
        }

        public SimpleJavaInterfaceGenerator withLineBreak(String lineBreak)
        {
            setLineBreak(lineBreak);
            return this;
        }

        public SimpleJavaInterfaceGenerator withPackageName(String packageName)
        {
            setPackageName(packageName);
            return this;
        }

        public SimpleJavaInterfaceGenerator withClassName(String className)
        {
            setClassName(className);
            return this;
        }

        public SimpleJavaInterfaceGenerator withImport(String impt)
        {
            addToParameter("imports", impt);
            return this;
        }

        public SimpleJavaInterfaceGenerator withExtends(String... interfaces)
        {
            addAllToParameter("extends", interfaces);
            return this;
        }

        public SimpleJavaInterfaceGenerator withMethod(String name, String returnType, MethodParameter... parameters)
        {
            return withMethod(new MethodSpec(name, returnType, parameters));
        }

        public SimpleJavaInterfaceGenerator withMethod(MethodSpec method)
        {
            addToParameter("methods", method);
            return this;
        }

        @Override
        public GeneratedJavaCode generate()
        {
            List<?> imports = getParameter("imports");
            imports.sort(Comparator.comparing(String.class::cast));
            return super.generate();
        }
    }
}
