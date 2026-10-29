// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.generation.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class TestGenerationOutput
{
    @Test
    public void jsonIsFormattedAsJsonNodeToPrettyString() throws Exception
    {
        String json = "{\"b\":1,\"a\":[true,null,{}],\"n\":null,\"e\":[],\"d\":{\"z\":\"x\",\"y\":1.50}}";
        String content = new GenerationOutput(json, "schema.json", "json").extractFileContent();
        Assert.assertEquals(new ObjectMapper().readTree(json).toPrettyString(), content);
        Assert.assertTrue(content, content.contains("\"b\" : 1"));
    }

    @Test
    public void jsonDeeperThanJacksonDefaultLimitIsExtracted() throws Exception
    {
        int depth = 1200;
        String json = String.join("", Collections.nCopies(depth, "{\"a\":")) + "1" + String.join("", Collections.nCopies(depth, "}"));
        String content = new GenerationOutput(json, "schema.json", "json").extractFileContent();
        Assert.assertTrue(content.contains("\"a\" : 1"));
        Assert.assertEquals(depth, content.chars().filter(c -> c == '{').count());
    }

    @Test
    public void emptyOrBlankJsonIsExtractedAsEmpty() throws Exception
    {
        for (String json : new String[]{"", " ", "\n", " \t\r\n "})
        {
            Assert.assertEquals("\"" + json + "\"", "", new GenerationOutput(json, "schema.json", "json").extractFileContent());
        }
        Assert.assertEquals("null", new GenerationOutput("null", "schema.json", "json").extractFileContent());
    }
}
