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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.plan.execution.result.Result;

import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Execution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureMultiExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.shared.core.url.StreamProvider;

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
import javax.lang.model.SourceVersion;

public class ServiceExecutionClassGenerator
{
    private static final List<String> PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES = Arrays.asList("streamProvider", "inputStreamProvider");

    private final Service service;
    private final String packagePrefix;
    private final String planResourceName;

    private final Set<String> javaImports = Sets.mutable.empty();
    private final Set<String> otherImports = Sets.mutable.empty();
    private String packageName;
    private String constructor;
    private String executeMethod;
    private String executeMethodWithStreamProvider;

    private ServiceExecutionClassGenerator(Service service, String packagePrefix, String planResourceName)
    {
        this.service = Objects.requireNonNull(service);
        this.packagePrefix = packagePrefix;
        this.planResourceName = Objects.requireNonNull(planResourceName);

        // add base imports
        addImport(AbstractServicePlanExecutor.class);
        addImport(Result.class);
    }

    public GeneratedJavaClass generate()
    {
        generatePackageName();
        generateConstructor();
        generateExecuteMethods();

        StringBuilder builder = new StringBuilder(8192);
        builder.append("package ").append(this.packageName).append(";\n");
        if (!this.otherImports.isEmpty())
        {
            builder.append('\n');
            this.otherImports.stream().sorted().map(i -> "import " + i + ";\n").forEach(builder::append);
        }
        if (!this.javaImports.isEmpty())
        {
            builder.append('\n');
            this.javaImports.stream().sorted().map(i -> "import " + i + ";\n").forEach(builder::append);
        }
        builder.append('\n');
        builder.append("public class ").append(this.service.name).append(" extends ").append(AbstractServicePlanExecutor.class.getSimpleName()).append('\n');
        builder.append("{\n");
        builder.append(this.constructor);
        builder.append('\n');
        builder.append(this.executeMethod);
        builder.append('\n');
        builder.append(this.executeMethodWithStreamProvider);
        builder.append("}\n");
        return new GeneratedJavaClass(this.packageName + "." + this.service.name, builder.toString());
    }

    private void generatePackageName()
    {
        String servicePackage = this.service._package;
        if ((servicePackage == null) || servicePackage.isEmpty())
        {
            throw new RuntimeException("Service does not have a package: " + this.service.getPath());
        }
        if (this.packagePrefix == null)
        {
            this.packageName = Arrays.stream(servicePackage.split("::")).map(JavaSourceHelper::toValidJavaIdentifier).collect(Collectors.joining("."));
            return;
        }
        if (!SourceVersion.isName(this.packagePrefix))
        {
            throw new RuntimeException("Invalid package prefix: \"" + this.packagePrefix + "\"");
        }
        this.packageName = this.packagePrefix + "." + Arrays.stream(servicePackage.split("::")).map(JavaSourceHelper::toValidJavaIdentifier).collect(Collectors.joining("."));
    }

    private void generateConstructor()
    {
        this.constructor = "    public " + this.service.name + "()\n" +
                "    {\n" +
                "        super(\"" + this.service.getPath() + "\", \"" + this.planResourceName + "\", false);\n" +
                "    }\n";
    }

    private void generateExecuteMethods()
    {
        List<ExecutionParameter> parameters = getExecutionParameters();
        parameters.stream().map(ExecutionParameter::getType).forEach(this::addImport);
        if (parameters.stream().anyMatch(ExecutionParameter::isToMany))
        {
            addImport(List.class);
        }
        addImport(StreamProvider.class);

        this.executeMethod = generateExecuteMethodWithoutStreamProvider(parameters);
        this.executeMethodWithStreamProvider = generateExecuteMethodWithStreamProvider(parameters);
    }

    private String generateExecuteMethodWithoutStreamProvider(List<ExecutionParameter> parameters)
    {
        StringBuilder builder = new StringBuilder(256);
        builder.append("    public Result execute(");
        int startLen = builder.length();
        parameters.forEach(p -> p.appendParameter((builder.length() == startLen) ? builder : builder.append(", ")));
        builder.append(")\n");
        builder.append("    {\n");
        builder.append("        return execute(");
        parameters.stream().map(ExecutionParameter::getJavaParamName).forEach(p -> builder.append(p).append(", "));
        builder.append("null);\n");
        builder.append("    }\n");
        return builder.toString();
    }

    private String generateExecuteMethodWithStreamProvider(List<ExecutionParameter> parameters)
    {
        StringBuilder builder = new StringBuilder(512);
        builder.append("    public Result execute(");
        parameters.forEach(p -> p.appendParameter(builder).append(", "));
        String streamProviderParameterName = getStreamProviderParameterName(parameters);
        builder.append(StreamProvider.class.getSimpleName()).append(' ').append(streamProviderParameterName);
        builder.append(")\n");
        builder.append("    {\n");
        builder.append("        return ");
        String indent;
        switch (parameters.size())
        {
            case 0:
            {
                indent = "";
                builder.append("newNoParameterExecutionBuilder()");
                break;
            }
            case 1:
            {
                indent = "";
                ExecutionParameter parameter = parameters.get(0);
                builder.append("newSingleParameterExecutionBuilder(\"").append(parameter.getLegendParamName()).append("\", ").append(parameter.getJavaParamName()).append(')');
                break;
            }
            default:
            {
                indent = (parameters.size() > 2) ? "\n                " : "";
                builder.append("newExecutionBuilder(").append(parameters.size()).append(')');
                parameters.forEach(p -> builder.append(indent).append(".withParameter(\"").append(p.getLegendParamName()).append("\", ").append(p.getJavaParamName()).append(')'));
            }
        }
        builder.append(indent).append(".withStreamProvider(").append(streamProviderParameterName).append(')');
        builder.append(indent).append(".execute();\n");
        builder.append("    }\n");
        return builder.toString();
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
            parameters.add(new ExecutionParameter(executionKey, javaParameterName, String.class, false));
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
            parameters.add(new ExecutionParameter(legendParameter.name, javaParameterName, getVariableJavaClass(legendParameter), isToMany(legendParameter)));
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

    private void addImport(Class<?> cls)
    {
        if (requiresImport(cls))
        {
            String className = cls.getName();
            (className.startsWith("java") ? this.javaImports : this.otherImports).add(className);
        }
    }

    private static Class<?> getVariableJavaClass(Variable variable)
    {
        switch (variable._class)
        {
            case "String":
            {
                return String.class;
            }
            case "Integer":
            {
                return isToOne(variable) ? long.class : Long.class;
            }
            case "Float":
            {
                return isToOne(variable) ? double.class : Double.class;
            }
            case "Decimal":
            {
                return BigDecimal.class;
            }
            case "Boolean":
            {
                return isToOne(variable) ? boolean.class : Boolean.class;
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

    private static boolean isToMany(Variable variable)
    {
        return variable.multiplicity.isUpperBoundGreaterThan(1);
    }

    private static boolean requiresImport(Class<?> cls)
    {
        return !cls.isPrimitive() && !cls.getName().startsWith("java.lang.");
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

    private static class ExecutionParameter
    {
        private final String legendParamName;
        private final String javaParamName;
        private final Class<?> type;
        private final boolean isToMany;

        private ExecutionParameter(String legendParamName, String javaParamName, Class<?> type, boolean isToMany)
        {
            this.legendParamName = legendParamName;
            this.javaParamName = javaParamName;
            this.type = type;
            this.isToMany = isToMany;
        }

        String getLegendParamName()
        {
            return this.legendParamName;
        }

        String getJavaParamName()
        {
            return this.javaParamName;
        }

        Class<?> getType()
        {
            return this.type;
        }

        boolean isToMany()
        {
            return this.isToMany;
        }

        StringBuilder appendParameter(StringBuilder builder)
        {
            if (this.isToMany)
            {
                builder.append("List<? extends ");
            }
            builder.append(this.type.getSimpleName());
            if (this.isToMany)
            {
                builder.append('>');
            }
            builder.append(' ').append(this.javaParamName);
            return builder;
        }
    }
}
