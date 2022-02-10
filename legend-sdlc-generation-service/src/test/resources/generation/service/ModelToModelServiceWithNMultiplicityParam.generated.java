package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelServiceWithNMultiplicityParam extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithNMultiplicityParam()
    {
        super("service::ModelToModelServiceWithNMultiplicityParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithNMultiplicityParam.json", false);
    }

    public ModelToModelServiceWithNMultiplicityParam(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceWithNMultiplicityParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithNMultiplicityParam.json", storeExecutorConfigurations);
    }

    public Result execute(List<? extends String> var)
    {
        return this.execute(var, null);
    }

    public Result execute(List<? extends String> var, StreamProvider streamProvider)
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
            this.newServiceVariable("var", String.class, 1, null)
        );
    }
}