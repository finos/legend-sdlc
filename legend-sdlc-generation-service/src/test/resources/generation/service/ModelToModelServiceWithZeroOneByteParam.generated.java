package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelServiceWithZeroOneByteParam extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithZeroOneByteParam()
    {
        super("service::ModelToModelServiceWithZeroOneByteParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithZeroOneByteParam.json", false);
    }

    public ModelToModelServiceWithZeroOneByteParam(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceWithZeroOneByteParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithZeroOneByteParam.json", storeExecutorConfigurations);
    }

    public Result execute(Byte var)
    {
        return this.execute(var, null);
    }

    public Result execute(Byte var, StreamProvider streamProvider)
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
            this.newServiceVariable("var", Byte.class, 0, 1)
        );
    }
}