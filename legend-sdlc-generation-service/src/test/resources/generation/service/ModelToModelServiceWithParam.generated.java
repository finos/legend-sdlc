package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelServiceWithParam extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithParam()
    {
        super("service::ModelToModelServiceWithParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithParam.json", false);
    }

    public Result execute(String var)
    {
        return this.execute(var, null);
    }

    public Result execute(String var, StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(1)
                     .withStreamProvider(streamProvider)
                     .withParameter("var", var)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.of(
            this.newServiceVariable("var", String.class, 1, 1)
        );
    }
}