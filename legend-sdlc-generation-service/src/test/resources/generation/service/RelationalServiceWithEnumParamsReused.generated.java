package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class RelationalServiceWithEnumParamsReused extends AbstractServicePlanExecutor
{
    public RelationalServiceWithEnumParamsReused()
    {
        super("service::RelationalServiceWithEnumParamsReused", "org/finos/legend/sdlc/generation/service/entities/service/RelationalServiceWithEnumParamsReused.json", false);
    }

    public RelationalServiceWithEnumParamsReused(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::RelationalServiceWithEnumParamsReused", "org/finos/legend/sdlc/generation/service/entities/service/RelationalServiceWithEnumParamsReused.json", storeExecutorConfigurations);
    }

    public Result execute(org.finos.model.Country cou, org.finos.model._enum.Country couName, String name)
    {
        return this.execute(cou, couName, name, null);
    }

    public Result execute(org.finos.model.Country cou, org.finos.model._enum.Country couName, String name, StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(3)
                     .withStreamProvider(streamProvider)
                     .withParameter("cou", cou)
                     .withParameter("couName", couName)
                     .withParameter("name", name)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.of(
            this.newServiceVariable("cou", org.finos.model.Country.class, 0, 1),
            this.newServiceVariable("couName", org.finos.model._enum.Country.class, 1, 1),
            this.newServiceVariable("name", String.class, 1, 1)
        );
    }
}
