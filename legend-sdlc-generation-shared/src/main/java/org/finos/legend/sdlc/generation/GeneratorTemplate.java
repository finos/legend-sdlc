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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class GeneratorTemplate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorTemplate.class);

    private final Template template;
    private final ObjectWrapper objectWrapper;

    private GeneratorTemplate(Template template)
    {
        this.template = template;
        this.objectWrapper = newObjectWrapper();
    }

    String generateText(Map<String, ?> parameters)
    {
        LOGGER.debug("Starting generating text from {}", this.template.getName());
        String text;
        try (StringWriter writer = new StringWriter())
        {
            this.template.process(parameters, writer, this.objectWrapper);
            text = writer.toString();
        }
        catch (Exception e)
        {
            LOGGER.error("Error generating text from {}", this.template.getName(), e);
            if (e instanceof RuntimeException)
            {
                throw (RuntimeException) e;
            }
            if (e instanceof IOException)
            {
                throw new UncheckedIOException((IOException) e);
            }
            throw new RuntimeException(e);
        }
        LOGGER.debug("Finished generating text from {}", this.template.getName());
        return text;
    }

    public static GeneratorTemplate fromResource(String resourceName)
    {
        return fromResource(resourceName, Thread.currentThread().getContextClassLoader());
    }

    public static GeneratorTemplate fromResource(String resourceName, ClassLoader classLoader)
    {
        return new GeneratorTemplate(loadTemplateFromResource(resourceName, Objects.requireNonNull(classLoader, "class loader may not be null")));
    }

    private static Template loadTemplateFromResource(String templateResource, ClassLoader classLoader)
    {
        LOGGER.debug("Loading freemarker template '{}' in class loader {}", templateResource, classLoader);
        URL templateURL = classLoader.getResource(templateResource);
        if (templateURL == null)
        {
            LOGGER.error("Unable to find freemarker template '{}' in class loader {}", templateResource, classLoader);
            throw new RuntimeException("Unable to find freemarker template '" + templateResource + "'");
        }
        Template template;
        try (Reader reader = new InputStreamReader(templateURL.openStream(), StandardCharsets.UTF_8))
        {
            template = new Template(templateResource, reader, new Configuration(Configuration.VERSION_2_3_30));
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading freemarker template from '{}' ({})", templateResource, templateURL, e);
            throw new RuntimeException("Error loading freemarker template from '" + templateResource + "'", e);
        }
        LOGGER.debug("Finished loading freemarker template '{}' ({})", templateResource, templateURL);
        return template;
    }

    private static ObjectWrapper newObjectWrapper()
    {
        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
        objectWrapper.setExposeFields(true);
        objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
        return objectWrapper;
    }
}
