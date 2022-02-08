package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelServiceMulti extends AbstractServicePlanExecutor
{
    public ModelToModelServiceMulti()
    {
        super("service::ModelToModelServiceMulti", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceMulti.json", false);
    }

    public ModelToModelServiceMulti(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceMulti", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceMulti.json", storeExecutorConfigurations);
    }

    public Result execute(String env)
    {
        return this.execute(env, null);
    }

    public Result execute(String env, StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(1)
                     .withStreamProvider(streamProvider)
                     .withParameter("env", env)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.of(
            this.newServiceVariable("env", String.class, 1, 1)
        );
    }
}