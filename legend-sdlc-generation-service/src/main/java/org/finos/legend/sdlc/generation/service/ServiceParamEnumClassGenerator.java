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
import org.finos.legend.sdlc.generation.service.ServiceExecutionClassGenerator.GeneratedJavaClass;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import javax.lang.model.SourceVersion;

public class ServiceParamEnumClassGenerator
{
    private final String packagePrefix;
    private final EnumParameter enumParameter;

    private ServiceParamEnumClassGenerator(String packagePrefix, EnumParameter enumParameter)
    {
        this.packagePrefix = packagePrefix;
        this.enumParameter = enumParameter;
    }

    public GeneratedJavaClass generate()
    {
        String enumClass = this.enumParameter.getEnumClass();
        int lastSeparatorIndex = enumClass.lastIndexOf(EntityPaths.PACKAGE_SEPARATOR);
        String enumClassName = enumClass.substring(lastSeparatorIndex + EntityPaths.PACKAGE_SEPARATOR.length());
        String packageName = generatePackageName(enumClass, lastSeparatorIndex);
        MutableMap<String, Object> dataModel = Maps.mutable.with(
                "classPackage", packageName,
                "enumClassName", enumClassName,
                "validEnumValues", this.enumParameter.getValidEnumValues());

        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
        objectWrapper.setExposeFields(true);
        objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
        Template template = ServiceExecutionClassGenerator.loadTemplate("generation/service/ServiceParamEnumClassGenerator.ftl", "ServiceParamEnumClassGenerator");
        try
        {
            StringWriter code = new StringWriter();
            template.process(dataModel, code, objectWrapper);
            // Use \n for all line breaks to ensure consistent behavior across environments
            String codeString = code.toString().replaceAll("\\R", "\n");
            return new GeneratedJavaClass(packageName + "." + enumClassName, codeString);
        }
        catch (TemplateException | IOException e)
        {
            throw new RuntimeException("Error generating execution class for enum" + enumClass, e);
        }
    }

    public static ServiceParamEnumClassGenerator newGenerator(String packagePrefix, EnumParameter enumParam)
    {
        return new ServiceParamEnumClassGenerator(packagePrefix, enumParam);
    }

    private String generatePackageName(String enumClassWithPackage, int lastSeparatorIndex)
    {
        String enumPackage = (lastSeparatorIndex == -1) ? null : enumClassWithPackage.substring(0, lastSeparatorIndex);
        if ((enumPackage == null) || enumPackage.isEmpty())
        {
            throw new RuntimeException("Enum does not have a package: " + enumClassWithPackage);
        }
        StringBuilder builder = new StringBuilder();
        if (this.packagePrefix != null)
        {
            if (!SourceVersion.isName(this.packagePrefix))
            {
                throw new RuntimeException("Invalid package prefix: \"" + this.packagePrefix + "\"");
            }
            builder.append(this.packagePrefix);
        }
        EntityPaths.forEachPathElement(enumPackage, name -> ((builder.length() == 0) ? builder : builder.append('.')).append(JavaSourceHelper.toValidJavaIdentifier(name)));
        return builder.toString();
    }

    public static class EnumParameter
    {
        public final String enumClass;
        public final List<String> validEnumValues;

        public EnumParameter(String enumClass, List<String> validEnumValues)
        {
            this.enumClass = enumClass;
            this.validEnumValues = validEnumValues;
        }

        public String getEnumClass()
        {
            return this.enumClass;
        }

        public List<String> getValidEnumValues()
        {
            return this.validEnumValues;
        }
    }
}