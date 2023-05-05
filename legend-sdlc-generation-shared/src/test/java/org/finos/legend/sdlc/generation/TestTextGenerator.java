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

public class TestTextGenerator
{
    @Test
    public void testInvalidLineBreak()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> new SimpleTextGenerator("br"));
        Assert.assertEquals("Invalid line break: \"br\"", e.getMessage());

        e = Assert.assertThrows(IllegalArgumentException.class, () -> new SimpleTextGenerator(null).withLineBreak("not a line break"));
        Assert.assertEquals("Invalid line break: \"not a line break\"", e.getMessage());
    }

    @Test
    public void testSimpleText()
    {
        GeneratedText generated = new SimpleTextGenerator("\n")
                .withTitle("The Quick Brown Fox")
                .withAuthor("Anonymous")
                .withDate(LocalDate.of(2023, 5, 5))
                .withLine("The quick brown fox")
                .withLine("jumped over the")
                .withLine("lazy dog.")
                .withLine("")
                .withLine("The End")
                .generate();

        String expected = "Title: The Quick Brown Fox\n" +
                "Author: Anonymous\n" +
                "Date: 2023-05-05\n" +
                "\n" +
                "The quick brown fox\n" +
                "jumped over the\n" +
                "lazy dog.\n" +
                "\n" +
                "The End\n";
        Assert.assertEquals(expected, generated.getText());
    }

    @Test
    public void testSimpleTextCarriageReturn()
    {
        GeneratedText generated = new SimpleTextGenerator("\r\n")
                .withTitle("The Quick Brown Fox")
                .withAuthor("Anonymous")
                .withDate(LocalDate.of(2023, 4, 4))
                .withLine("The quick brown fox")
                .withLine("jumped over the lazy dog.")
                .withLine("")
                .withLine("The End")
                .generate();

        String expected = "Title: The Quick Brown Fox\r\n" +
                "Author: Anonymous\r\n" +
                "Date: 2023-04-04\r\n" +
                "\r\n" +
                "The quick brown fox\r\n" +
                "jumped over the lazy dog.\r\n" +
                "\r\n" +
                "The End\r\n";
        Assert.assertEquals(expected, generated.getText());
    }

    private static class SimpleTextGenerator extends TextGenerator<GeneratedText>
    {
        private SimpleTextGenerator(String defaultLineBreak)
        {
            super(GeneratorTemplate.fromResource("org/finos/legend/sdlc/generation/textTemplate.ftl"), defaultLineBreak);
        }

        public SimpleTextGenerator withLineBreak(String lineBreak)
        {
            setLineBreak(lineBreak);
            return this;
        }

        public SimpleTextGenerator withTitle(String title)
        {
            setParameter("title", title);
            return this;
        }

        public SimpleTextGenerator withAuthor(String author)
        {
            setParameter("author", author);
            return this;
        }

        public SimpleTextGenerator withDate(LocalDate date)
        {
            setParameter("date", date);
            return this;
        }

        public SimpleTextGenerator withLine(String line)
        {
            addToParameter("lines", line);
            return this;
        }

        @Override
        protected GeneratedText newGeneratedText(String text)
        {
            return () -> text;
        }
    }
}
