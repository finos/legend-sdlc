// Copyright 2021 Goldman Sachs
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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Execution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureMultiExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.sdlc.generation.service.ServiceParamEnumClassGenerator.EnumParameter;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.SourceVersion;

public class ServiceExecutionClassGenerator
{
    private static final ImmutableList<String> PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES = Lists.immutable.with("streamProvider", "inputStreamProvider");

    private final Service service;
    private final String packagePrefix;
    private final String planResourceName;
    private final Map<String, EnumParameter> enumParameters;

    private ServiceExecutionClassGenerator(Service service, String packagePrefix, String planResourceName, Map<String, EnumParameter> enumParameters)
    {
        if ((packagePrefix != null) && !SourceVersion.isName(packagePrefix))
        {
            throw new IllegalArgumentException("Invalid package prefix: \"" + packagePrefix + "\"");
        }
        this.service = Objects.requireNonNull(service);
        this.packagePrefix = packagePrefix;
        this.planResourceName = Objects.requireNonNull(planResourceName);
        this.enumParameters = enumParameters;
    }

    public GeneratedJavaClass generate()
    {
        String packageName = generatePackageName();
        MutableList<ExecutionParameter> executionParameters = getExecutionParameters();
        MutableMap<String, Object> dataModel = Maps.mutable.<String, Object>empty()
                .withKeyValue("classPackage", packageName)
                .withKeyValue("service", this.service)
                .withKeyValue("planResourceName", this.planResourceName)
                .withKeyValue("streamProviderParameterName", getStreamProviderParameterName(executionParameters))
                .withKeyValue("imports", getImports(executionParameters))
                .withKeyValue("executionParameters", executionParameters);

        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
        objectWrapper.setExposeFields(true);
        objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
        Template template = loadTemplate("generation/service/ServiceExecutionClassGenerator.ftl", "ServiceExecutionClassGenerator");
        try
        {
            StringWriter code = new StringWriter();
            template.process(dataModel, code, objectWrapper);
            // Use \n for all line breaks to ensure consistent behavior across environments
            String codeString = code.toString().replaceAll("\\R", "\n");
            return new GeneratedJavaClass(packageName + "." + this.service.name, codeString);
        }
        catch (TemplateException | IOException e)
        {
            throw new RuntimeException("Error generating execution class for " + this.service.getPath(), e);
        }
    }

    static Template loadTemplate(String freeMarkerTemplate, String name)
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

    private MutableList<String> getImports(MutableList<ExecutionParameter> executionParameters)
    {
        MutableSet<Class<?>> imports = executionParameters.collectIf(p -> !p.isValidEnumParam(), p -> getVariableJavaClassForPrimitive(p.variable, false), Sets.mutable.with(List.class));
        imports.removeIf(c -> "java.lang".equals(c.getPackage().getName()));
        return imports.collect(Class::getName, Lists.mutable.ofInitialCapacity(imports.size())).sortThis();
    }

    private String generatePackageName()
    {
        String servicePackage = this.service._package;
        if ((servicePackage == null) || servicePackage.isEmpty())
        {
            throw new RuntimeException("Service does not have a package: " + this.service.getPath());
        }
        StringBuilder builder = new StringBuilder();
        if (this.packagePrefix != null)
        {
            builder.append(this.packagePrefix);
        }
        EntityPaths.forEachPathElement(servicePackage, name -> ((builder.length() == 0 ? builder : builder.append('.'))).append(JavaSourceHelper.toValidJavaIdentifier(name)));
        return builder.toString();
    }

    private MutableList<ExecutionParameter> getExecutionParameters()
    {
        Execution execution = this.service.execution;
        if (!(execution instanceof PureExecution))
        {
            throw new IllegalArgumentException("Only services with Pure executions are supported: " + service.getPath());
        }
        Lambda lambda = ((PureExecution) execution).func;
        MutableList<ExecutionParameter> parameters = Lists.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
        MutableSet<String> javaParameterNames = Sets.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
        if (execution instanceof PureMultiExecution)
        {
            String executionKey = ((PureMultiExecution) execution).executionKey;
            String javaParameterName = JavaSourceHelper.toValidJavaIdentifier(executionKey);
            javaParameterNames.add(javaParameterName);
            parameters.add(new ExecutionParameter(new Variable(executionKey, "String", new Multiplicity(1, 1)), javaParameterName));
        }
        for (Variable legendParameter : lambda.parameters)
        {
            String javaParameterName = JavaSourceHelper.toValidJavaIdentifier(legendParameter.name);
            if (!javaParameterNames.add(javaParameterName))
            {
                String initialJavaParameterName = javaParameterName;
                int i = 2;
                javaParameterName = initialJavaParameterName + "$" + i;
                while (!javaParameterNames.add(javaParameterName))
                {
                    i++;
                    javaParameterName = initialJavaParameterName + "$" + i;
                }
            }
            parameters.add(new ExecutionParameter(legendParameter, javaParameterName));
        }
        return parameters;
    }

    private String getStreamProviderParameterName(ListIterable<ExecutionParameter> parameters)
    {
        MutableSet<String> parameterNames = parameters.collect(ExecutionParameter::getLegendParamName, Sets.mutable.ofInitialCapacity(parameters.size()));
        String name = PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES.detect(n -> !parameterNames.contains(n));
        if (name == null)
        {
            String prefix = "";
            while (name == null)
            {
                prefix += "_";
                name = PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES.asLazy().collect(prefix::concat).detect(n -> !parameterNames.contains(n));
            }
        }
        return name;
    }

    private String getVariableJavaClass(ExecutionParameter executionParameter, boolean usePrimitive)
    {
        if (executionParameter.isValidEnumParam())
        {
            StringBuilder builder = new StringBuilder();
            if (this.packagePrefix != null)
            {
                builder.append(this.packagePrefix);
            }
            EntityPaths.forEachPathElement(executionParameter.variable._class, name -> ((builder.length() == 0) ? builder : builder.append('.')).append(JavaSourceHelper.toValidJavaIdentifier(name)));
            return builder.toString();
        }
        else
        {
            return getVariableJavaClassForPrimitive(executionParameter.variable, usePrimitive).getSimpleName();
        }
    }

    private static Class<?> getVariableJavaClassForPrimitive(Variable variable, boolean usePrimitive)
    {
        switch (variable._class)
        {
            case "String":
            {
                return String.class;
            }
            case "Integer":
            {
                return usePrimitive && isToOne(variable) ? long.class : Long.class;
            }
            case "Float":
            {
                return usePrimitive && isToOne(variable) ? double.class : Double.class;
            }
            case "Decimal":
            {
                return BigDecimal.class;
            }
            case "Boolean":
            {
                return usePrimitive && isToOne(variable) ? boolean.class : Boolean.class;
            }
            case "StrictDate":
            {
                return LocalDate.class;
            }
            case "DateTime":
            {
                return ZonedDateTime.class;
            }
            case "Date":
            {
                return Temporal.class;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown variable type: " + variable._class);
            }
        }
    }

    private static boolean isToOne(Variable variable)
    {
        return (variable.multiplicity.lowerBound == 1) && variable.multiplicity.isUpperBoundEqualTo(1);
    }

    public static ServiceExecutionClassGenerator newGenerator(Service service, String packagePrefix, String planResourceName, Map<String, EnumParameter> enumParameters)
    {
        return new ServiceExecutionClassGenerator(service, packagePrefix, planResourceName, enumParameters);
    }

    public static class GeneratedJavaClass
    {
        private final String name;
        private final String code;

        GeneratedJavaClass(String name, String code)
        {
            this.name = name;
            this.code = code;
        }

        /**
         * Get the name of the generated Java class, including the package.
         *
         * @return generated class name
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * Get the code of the generated Java class.
         *
         * @return generated class code
         */
        public String getCode()
        {
            return this.code;
        }
    }

    public class ExecutionParameter
    {
        private final Variable variable;
        private final String javaParamName;

        private ExecutionParameter(Variable variable, String javaParamName)
        {
            this.variable = variable;
            this.javaParamName = javaParamName;
        }

        public String getLegendParamName()
        {
            return this.variable.name;
        }

        public String getJavaParamName()
        {
            return this.javaParamName;
        }

        public String getJavaMethodType()
        {
            return getVariableJavaClass(this, true);
        }

        public String getServiceParameterType()
        {
            return getVariableJavaClass(this, false);
        }

        public boolean isValidEnumParam()
        {
            return enumParameters != null && enumParameters.size() >= 1 && variable._class.equals(enumParameters.get(variable.name).getEnumClass());
        }

        public Multiplicity getMultiplicity()
        {
            return this.variable.multiplicity;
        }

        public String getJavaSignature()
        {
            return appendParameter(new StringBuilder()).toString();
        }

        StringBuilder appendParameter(StringBuilder builder)
        {
            return this.appendTypeString(builder).append(' ').append(this.javaParamName);
        }

        StringBuilder appendTypeString(StringBuilder builder)
        {
            boolean isToMany = this.getMultiplicity().isUpperBoundGreaterThan(1);
            if (isToMany)
            {
                builder.append("List<? extends ");
            }
            builder.append(this.getJavaMethodType());
            if (isToMany)
            {
                builder.append('>');
            }
            return builder;
        }
    }
}
