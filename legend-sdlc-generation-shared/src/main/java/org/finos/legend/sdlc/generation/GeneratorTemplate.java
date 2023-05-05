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

import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GeneratorTemplate
{
    private final Template template;
    private final ObjectWrapper objectWrapper;

    private GeneratorTemplate(Template template)
    {
        this.template = template;
        this.objectWrapper = newObjectWrapper();
    }

    String generateText(Map<String, ?> parameters)
    {
        try (StringWriter writer = new StringWriter())
        {
            this.template.process(parameters, writer, this.objectWrapper);
            return writer.toString();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        catch (TemplateException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static GeneratorTemplate fromResource(String resourceName)
    {
        return new GeneratorTemplate(loadTemplateFromResource(resourceName));
    }

    private static Template loadTemplateFromResource(String templateResource)
    {
        URL templateURL = Thread.currentThread().getContextClassLoader().getResource(templateResource);
        if (templateURL == null)
        {
            throw new RuntimeException("Unable to find freemarker template '" + templateResource + "' on context classpath");
        }
        try (Reader reader = new InputStreamReader(templateURL.openStream(), StandardCharsets.UTF_8))
        {
            return new Template(templateResource, reader, new Configuration(Configuration.VERSION_2_3_30));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Error loading freemarker template from " + templateResource, e);
        }
    }

    private static ObjectWrapper newObjectWrapper()
    {
        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
        objectWrapper.setExposeFields(true);
        objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
        return objectWrapper;
    }
}
