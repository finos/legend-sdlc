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
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Execution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureMultiExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.sdlc.generation.GeneratorTemplate;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;

class ServiceExecutionClassGenerator extends AbstractServiceExecutionClassGenerator
{
    private static final String SERVICE_PARAM = "service";
    private static final String PLAN_RESOURCE_NAME_PARAM = "planResourceName";
    private static final String STREAM_PROVIDER_PARAMETER_NAME_PARAM = "streamProviderParameterName";
    private static final String IMPORTS_PARAM = "imports";
    private static final String EXEC_PARAMS_PARAM = "executionParameters";

    private static final ImmutableList<String> PREFERRED_STREAM_PROVIDER_PARAMETER_NAMES = Lists.immutable.with("streamProvider", "inputStreamProvider");

    private ServiceExecutionClassGenerator(String packagePrefix)
    {
        super(GeneratorTemplate.fromResource("generation/service/ServiceExecutionClassGenerator.ftl"), packagePrefix);
    }

    public ServiceExecutionClassGenerator withPlanResourceName(String name)
    {
        setParameter(PLAN_RESOURCE_NAME_PARAM, name);
        return this;
    }

    public ServiceExecutionClassGenerator withService(Service service)
    {
        setPackageName(getJavaPackageName(service._package));
        setClassName(getJavaClassName(service.name));
        setParameter(SERVICE_PARAM, service);
        MutableList<ExecutionParameter> executionParameters = getExecutionParameters(service);
        setParameter(STREAM_PROVIDER_PARAMETER_NAME_PARAM, getStreamProviderParameterName(executionParameters));
        setParameter(IMPORTS_PARAM, getImports(executionParameters));
        setParameter(EXEC_PARAMS_PARAM, executionParameters);
        return this;
    }

    private MutableList<String> getImports(MutableList<ExecutionParameter> executionParameters)
    {
        // TODO add imports for non-primitives when possible
        MutableSet<Class<?>> imports = executionParameters.collect(p -> getVariableJavaClass(p.variable, false), Sets.mutable.with(List.class));
        imports.remove(null);
        imports.removeIf(c -> "java.lang".equals(c.getPackage().getName()));
        return imports.collect(Class::getName, Lists.mutable.ofInitialCapacity(imports.size())).sortThis();
    }

    private MutableList<ExecutionParameter> getExecutionParameters(Service service)
    {
        Execution execution = service.execution;
        if (!(execution instanceof PureExecution))
        {
            throw new IllegalArgumentException("Only services with Pure executions are supported: " + service.getPath());
        }
        Lambda lambda = ((PureExecution) execution).func;
        MutableList<ExecutionParameter> parameters = Lists.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
        MutableSet<String> javaParameterNames = Sets.mutable.ofInitialCapacity(lambda.parameters.size() + 1);
        if (execution instanceof PureMultiExecution)
        {
            PureMultiExecution multiExec = (PureMultiExecution) execution;
            if (multiExec.executionParameters != null && !multiExec.executionParameters.isEmpty())
            {
                String executionKey = multiExec.executionKey;
                String javaParameterName = JavaSourceHelper.toValidJavaIdentifier(executionKey);
                javaParameterNames.add(javaParameterName);
                parameters.add(newExecutionParameter(new Variable(executionKey, "String", new Multiplicity(1, 1)), javaParameterName));
            }
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
            parameters.add(newExecutionParameter(legendParameter, javaParameterName));
        }
        return parameters;
    }

    private ExecutionParameter newExecutionParameter(Variable variable, String javaParamName)
    {
        String javaParamRawType = getVariableJavaClassName(variable, true);
        String serviceParamRawType = getVariableJavaClassName(variable, false);
        return new ExecutionParameter(variable, javaParamName, javaParamRawType, serviceParamRawType);
    }

    private String getStreamProviderParameterName(ListIterable<ExecutionParameter> parameters)
    {
        MutableSet<String> parameterNames = parameters.collect(ExecutionParameter::getJavaParamName, Sets.mutable.ofInitialCapacity(parameters.size()));
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

    private String getVariableJavaClassName(Variable variable, boolean usePrimitive)
    {
        Class<?> cls = getVariableJavaClass(variable, usePrimitive);
        if (cls != null)
        {
            return cls.getSimpleName();
        }

        StringBuilder builder = appendPackagePrefixIfPresent(new StringBuilder());
        EntityPaths.forEachPathElement(variable._class.path, name -> ((builder.length() == 0) ? builder : builder.append('.')).append(JavaSourceHelper.toValidJavaIdentifier(name)));
        return builder.toString();
    }

    private static Class<?> getVariableJavaClass(Variable variable, boolean usePrimitive)
    {
        switch (variable._class.path)
        {
            case "String":
            {
                return String.class;
            }
            case "Integer":
            {
                return usePrimitive && isToOne(variable.multiplicity) ? long.class : Long.class;
            }
            case "Float":
            {
                return usePrimitive && isToOne(variable.multiplicity) ? double.class : Double.class;
            }
            case "Decimal":
            {
                return BigDecimal.class;
            }
            case "Boolean":
            {
                return usePrimitive && isToOne(variable.multiplicity) ? boolean.class : Boolean.class;
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
            case "Byte":
            {
                return usePrimitive && isToOne(variable.multiplicity) ?
                        byte.class :
                        (variable.multiplicity.isUpperBoundGreaterThan(1) ? InputStream.class : Byte.class);
            }
            default:
            {
                return null;
            }
        }
    }

    private static boolean isToOne(Multiplicity multiplicity)
    {
        return (multiplicity.lowerBound == 1) && multiplicity.isUpperBoundEqualTo(1);
    }

    static ServiceExecutionClassGenerator newGenerator(String packagePrefix)
    {
        return new ServiceExecutionClassGenerator(packagePrefix);
    }

    public static class ExecutionParameter
    {
        private final Variable variable;
        private final String javaParamName;
        private final String javaParamRawType;
        private final String serviceParamRawType;

        private ExecutionParameter(Variable variable, String javaParamName, String javaParamRawType, String serviceParamRawType)
        {
            this.variable = variable;
            this.javaParamName = javaParamName;
            this.javaParamRawType = javaParamRawType;
            this.serviceParamRawType = serviceParamRawType;
        }

        @SuppressWarnings("unused")
        public String getLegendParamName()
        {
            return this.variable.name;
        }

        @SuppressWarnings("unused")
        public String getJavaParamName()
        {
            return this.javaParamName;
        }

        @SuppressWarnings("unused")
        public String getServiceParameterType()
        {
            return this.serviceParamRawType;
        }

        @SuppressWarnings("unused")
        public Multiplicity getMultiplicity()
        {
            return this.variable.multiplicity;
        }

        @SuppressWarnings("unused")
        public String getJavaSignature()
        {
            return appendParameter(new StringBuilder()).toString();
        }

        StringBuilder appendParameter(StringBuilder builder)
        {
            return appendTypeString(builder).append(' ').append(this.javaParamName);
        }

        StringBuilder appendTypeString(StringBuilder builder)
        {
            return ("Byte".equals(variable._class.path) || !getMultiplicity().isUpperBoundGreaterThan(1)) ?
                    builder.append(this.javaParamRawType) :
                    builder.append("List<? extends ").append(this.javaParamRawType).append('>');
        }
    }
}
