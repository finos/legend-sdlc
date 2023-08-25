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

import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

abstract class AbstractGenerationTest
{
    protected void assertGeneratedJavaCode(String expectedClassName, String expectedCode, GeneratedJavaCode generatedJavaCode)
    {
        Assert.assertEquals(expectedClassName, generatedJavaCode.getClassName());
        Assert.assertEquals(expectedClassName, expectedCode, generatedJavaCode.getText());
    }

    protected String loadTextResource(String resourceName)
    {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Unable to find resource '" + resourceName + "' on context classpath");
        }
        try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))
        {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1)
            {
                builder.append(buffer, 0, read);
            }
            return Pattern.compile("\\R").matcher(builder).replaceAll("\n");
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Error reading " + resourceName + " (" + url + ")", e);
        }
    }
}
