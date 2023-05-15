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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TestGeneratorTemplate
{
    @Test
    public void testTextTemplate()
    {
        GeneratorTemplate template = GeneratorTemplate.fromResource("org/finos/legend/sdlc/generation/textTemplate.ftl");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("title", "The Quick Brown Fox");
        parameters.put("author", "Anonymous");
        parameters.put("date", LocalDate.of(2023, 5, 5));
        parameters.put("lines", Arrays.asList("The quick brown fox", "jumped over the lazy dog.", "", "The End"));
        String generated = template.generateText(parameters);

        String expected = "Title: The Quick Brown Fox\n" +
                "Author: Anonymous\n" +
                "Date: 2023-05-05\n" +
                "\n" +
                "The quick brown fox\n" +
                "jumped over the lazy dog.\n" +
                "\n" +
                "The End\n";
        Assert.assertEquals(expected, generated.replaceAll("\\R", "\n"));
    }

    @Test
    public void testJavaTemplate()
    {
        GeneratorTemplate template = GeneratorTemplate.fromResource("org/finos/legend/sdlc/generation/javaTemplate.ftl");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("packageName", "org.finos.sdlc.generation.test");
        parameters.put("className", "TestInterface");
        parameters.put("imports", Arrays.asList("org.finos.sdlc.generation.more.SuperInterface1", "org.finos.sdlc.generation.other.SuperInterface2"));
        parameters.put("extends", Arrays.asList("SuperInterface1", "SuperInterface2"));
        String generated = template.generateText(parameters);

        String expected = "package org.finos.sdlc.generation.test;\n" +
                "\n" +
                "import org.finos.sdlc.generation.more.SuperInterface1;\n" +
                "import org.finos.sdlc.generation.other.SuperInterface2;\n" +
                "\n" +
                "public interface TestInterface extends SuperInterface1, SuperInterface2\n" +
                "{\n" +
                "}\n";
        Assert.assertEquals(expected, generated.replaceAll("\\R", "\n"));
    }
}
