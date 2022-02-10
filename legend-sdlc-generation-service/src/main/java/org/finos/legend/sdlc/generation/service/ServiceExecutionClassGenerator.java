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
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Execution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureMultiExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServiceExecutionClassGenerator
{
    private static final List<String> PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES = Arrays.asList("streamProvider", "inputStreamProvider");

    private final Service service;
    private final String packagePrefix;
    private final String planResourceName;

    private ServiceExecutionClassGenerator(Service service, String packagePrefix, String planResourceName)
    {
        this.service = Objects.requireNonNull(service);
        this.packagePrefix = packagePrefix;
        this.planResourceName = Objects.requireNonNull(planResourceName);
    }

    public GeneratedJavaClass generate()
    {
        String packageName = generatePackageName();
        List<ExecutionParameter> executionParameters = getExecutionParameters();
        MutableMap<String, Object> dataModel = Maps.mutable
                .with(
                        "classPackage", packageName,
                        "service", this.service,
                        "planResourceName", this.planResourceName,
                        "streamProviderParameterName", getStreamProviderParameterName(executionParameters)
                ).withKeyValue("imports", getImports(executionParameters))
                .withKeyValue("executionParameters", executionParameters);

        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("generation/service/ServiceExecutionClassGenerator.ftl"))))
        {
            DefaultObjectWrapper objectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_30);
            objectWrapper.setExposeFields(true);
            objectWrapper.setExposureLevel(BeansWrapper.EXPOSE_ALL);
            Template template = new Template("ServiceExecutionClassGenerator", reader, new Configuration(Configuration.VERSION_2_3_30));
            StringWriter code = new StringWriter();
            template.process(dataModel, code, objectWrapper);
            return new GeneratedJavaClass(packageName + "." + this.service.name, code.toString());
        }
        catch (TemplateException | IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private List<String> getImports(List<ExecutionParameter> executionParameters)
    {
        Stream<? extends Class<?>> classesToImportFromParameters = executionParameters.stream()
                .map(x -> getVariableJavaClass(x.variable, false))
                .filter(x -> !Objects.equals(x.getPackage().getName(), "java.lang"));

        Stream<? extends Class<?>> defaultJdkImports = Stream.of(List.class);

        return Stream.concat(classesToImportFromParameters, defaultJdkImports)
                .distinct()
                .map(Class::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private String generatePackageName()
    {
        String servicePackage = this.service._package;
        if ((servicePackage == null) || servicePackage.isEmpty())
        {
            throw new RuntimeException("Service does not have a package: " + this.service.getPath());
        }
        if (this.packagePrefix == null)
        {
            return Arrays.stream(servicePackage.split("::")).map(JavaSourceHelper::toValidJavaIdentifier).collect(Collectors.joining("."));
        }
        if (!SourceVersion.isName(this.packagePrefix))
        {
            throw new RuntimeException("Invalid package prefix: \"" + this.packagePrefix + "\"");
        }
        return this.packagePrefix + "." + Arrays.stream(servicePackage.split("::")).map(JavaSourceHelper::toValidJavaIdentifier).collect(Collectors.joining("."));
    }

    private List<ExecutionParameter> getExecutionParameters()
    {
        Execution execution = this.service.execution;
        if (!(execution instanceof PureExecution))
        {
            throw new IllegalArgumentException("Only services with Pure executions are supported: " + service.getPath());
        }
        Lambda lambda = ((PureExecution) execution).func;
        List<ExecutionParameter> parameters = Lists.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
        Set<String> javaParameterNames = Sets.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
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

    private String getStreamProviderParameterName(List<ExecutionParameter> parameters)
    {
        Set<String> parameterNames = parameters.stream().map(ExecutionParameter::getLegendParamName).collect(Collectors.toSet());
        Optional<String> name = PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES.stream().filter(n -> !parameterNames.contains(n)).findFirst();
        if (!name.isPresent())
        {
            String previousPrefix = "";
            while (!name.isPresent())
            {
                String prefix = "_" + previousPrefix;
                name = PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES.stream().map(prefix::concat).filter(n -> !parameterNames.contains(n)).findFirst();
                previousPrefix = prefix;
            }
        }
        return name.get();
    }


    private static Class<?> getVariableJavaClass(Variable variable, boolean usePrimitive)
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

    public static ServiceExecutionClassGenerator newGenerator(Service service, String packagePrefix, String planResourceName)
    {
        return new ServiceExecutionClassGenerator(service, packagePrefix, planResourceName);
    }

    public static class GeneratedJavaClass
    {
        private final String name;
        private final String code;

        private GeneratedJavaClass(String name, String code)
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

    public static class ExecutionParameter
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

        public  String getJavaParamName()
        {
            return this.javaParamName;
        }

        public String getJavaMethodType()
        {
            return getVariableJavaClass(this.variable, true).getSimpleName();
        }

        public String getServiceParameterType()
        {
            return getVariableJavaClass(this.variable, false).getSimpleName();
        }

        public  Multiplicity getMultiplicity()
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
