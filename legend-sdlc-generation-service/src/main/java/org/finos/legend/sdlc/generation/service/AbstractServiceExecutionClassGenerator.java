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

package org.finos.legend.sdlc.generation.service;

import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import javax.lang.model.SourceVersion;

abstract class AbstractServiceExecutionClassGenerator
{
    private final String packagePrefix;

    protected AbstractServiceExecutionClassGenerator(String packagePrefix)
    {
        if ((packagePrefix != null) && !SourceVersion.isName(packagePrefix))
        {
            throw new IllegalArgumentException("Invalid package prefix: \"" + packagePrefix + "\"");
        }
        this.packagePrefix = packagePrefix;
    }

    public GeneratedJavaClass generate()
    {
        String packageName = getJavaPackageName();
        String className = getJavaClassName();
        // TODO maybe validate name?
        MutableMap<String, Object> templateParameters = Maps.mutable.with(
                "packageName", packageName,
                "className", className);
        collectTemplateParameters(templateParameters::put);

        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
        objectWrapper.setExposeFields(true);
        objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
        Template template = loadTemplate(getTemplateResourceName(), getClass().getSimpleName());
        try
        {
            StringWriter code = new StringWriter();
            template.process(templateParameters, code, objectWrapper);
            // Use \n for all line breaks to ensure consistent behavior across environments
            String codeString = code.toString().replaceAll("\\R", "\n");
            return new GeneratedJavaClass(packageName + "." + className, codeString);
        }
        catch (TemplateException | IOException e)
        {
            throw new RuntimeException("Error generating " + packageName + "." + className, e);
        }
    }

    private String getJavaPackageName()
    {
        String legendPackage = getLegendPackage();
        if ((legendPackage == null) || legendPackage.isEmpty())
        {
            throw new RuntimeException("Package missing");
        }
        StringBuilder builder = appendPackagePrefixIfPresent(new StringBuilder());
        EntityPaths.forEachPathElement(legendPackage, name -> ((builder.length() == 0 ? builder : builder.append('.'))).append(JavaSourceHelper.toValidJavaIdentifier(name)));
        return builder.toString();

    }

    private String getJavaClassName()
    {
        String legendName = getLegendName();
        if ((legendName == null) || legendName.isEmpty())
        {
            throw new RuntimeException("Name missing");
        }
        return JavaSourceHelper.toValidJavaIdentifier(legendName);
    }

    protected abstract String getLegendPackage();

    protected abstract String getLegendName();

    protected void collectTemplateParameters(BiConsumer<String, Object> consumer)
    {
        // Do nothing by default
    }

    protected abstract String getTemplateResourceName();

    protected StringBuilder appendPackagePrefixIfPresent(StringBuilder builder)
    {
        if (this.packagePrefix != null)
        {
            builder.append(this.packagePrefix);
        }
        return builder;
    }

    private static Template loadTemplate(String freeMarkerTemplate, String name)
    {
        URL templateURL = Thread.currentThread().getContextClassLoader().getResource(freeMarkerTemplate);
        if (templateURL == null)
        {
            throw new RuntimeException("Unable to find freemarker template '" + freeMarkerTemplate + "' on context classpath");
        }
        try (InputStreamReader reader = new InputStreamReader(templateURL.openStream(), StandardCharsets.UTF_8))
        {
            return new Template(name, reader, new Configuration(Configuration.VERSION_2_3_30));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading freemarker template from " + freeMarkerTemplate, e);
        }
    }
}
