package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelServiceWithPureOneByteParam extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithPureOneByteParam()
    {
        super("service::ModelToModelServiceWithPureOneByteParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithPureOneByteParam.json", false);
    }

    public ModelToModelServiceWithPureOneByteParam(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceWithPureOneByteParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithPureOneByteParam.json", storeExecutorConfigurations);
    }

    public Result execute(byte var)
    {
        return this.execute(var, null);
    }

    public Result execute(byte var, StreamProvider streamProvider)
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
            this.newServiceVariable("var", Byte.class, 1, 1)
        );
    }
}