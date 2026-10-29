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

package org.finos.legend.sdlc.generation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class TestServiceExecutionGeneratorJsonMapper
{
    @Test
    public void writesAndReadsJsonDeeperThanJacksonDefault() throws Exception
    {
        Map<String, Object> plan = Collections.singletonMap("value", 1);
        // two JSON levels per step: about 1200 levels
        for (int i = 0; i < 600; i++)
        {
            plan = Collections.singletonMap("children", Collections.singletonList(plan));
        }

        JsonMapper mapper = ServiceExecutionGenerator.getDefaultJsonMapper();
        String json = mapper.writeValueAsString(plan);
        Assert.assertEquals(plan, mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
    }
}
