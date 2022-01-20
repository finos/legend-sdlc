<#--
Copyright 2022 Goldman Sachs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
package ${classPackage};

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.shared.core.url.StreamProvider;

${imports?map(import->"import ${import};")?join("\n", "", "\n")}
public class ${service.name} extends AbstractServicePlanExecutor
{
    public ${service.name}()
    {
        super("${service.path}", "${planResourceName}", false);
    }

    public Result execute(${executionParameters?map(param->param.javaSignature)?join(", ")})
    {
        return this.execute(${executionParameters?map(param->param.javaParamName)?join(", ", "", ", ")}null);
    }

    public Result execute(${executionParameters?map(param->param.javaSignature)?join(", ", "", ", ")}StreamProvider ${streamProviderParameterName})
    {
        return this.newExecutionBuilder(${executionParameters?size})
                     .withStreamProvider(${streamProviderParameterName})
    <#list executionParameters as param>
                     .withParameter("${param.legendParamName}", ${param.javaParamName})
    </#list>
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
    <#if executionParameters?size != 0>
        return Lists.mutable.of(${executionParameters?map(param-> "\n            this.newServiceVariable(\"${param.legendParamName}\", ${param.serviceParameterType}.class, ${param.multiplicity.lowerBound}, ${param.multiplicity.upperBound!'null'})")?join(",", ");", "\n        );")}
    <#else>
        return Lists.mutable.empty();
    </#if>
    }
}