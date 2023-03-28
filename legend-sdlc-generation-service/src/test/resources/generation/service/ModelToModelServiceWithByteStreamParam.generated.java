package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.io.InputStream;
import java.util.List;

public class ModelToModelServiceWithByteStreamParam extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithByteStreamParam()
    {
        super("service::ModelToModelServiceWithByteStreamParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithByteStreamParam.json", false);
    }

    public ModelToModelServiceWithByteStreamParam(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceWithByteStreamParam", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithByteStreamParam.json", storeExecutorConfigurations);
    }

    public Result execute(InputStream var)
    {
        return this.execute(var, null);
    }

    public Result execute(InputStream var, StreamProvider streamProvider)
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
            this.newServiceVariable("var", InputStream.class, 1, 1)
        );
    }
}