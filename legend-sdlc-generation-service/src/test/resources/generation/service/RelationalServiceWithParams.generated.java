package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class RelationalServiceWithParams extends AbstractServicePlanExecutor
{
    public RelationalServiceWithParams()
    {
        super("service::RelationalServiceWithParams", "org/finos/legend/sdlc/generation/service/entities/service/RelationalServiceWithParams.json", false);
    }

    public Result execute(String firstName, String lastName)
    {
        return this.execute(firstName, lastName, null);
    }

    public Result execute(String firstName, String lastName, StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(2)
                     .withStreamProvider(streamProvider)
                     .withParameter("firstName", firstName)
                     .withParameter("lastName", lastName)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.of(
            this.newServiceVariable("firstName", String.class, 1, 1),
            this.newServiceVariable("lastName", String.class, 1, 1)
        );
    }
}