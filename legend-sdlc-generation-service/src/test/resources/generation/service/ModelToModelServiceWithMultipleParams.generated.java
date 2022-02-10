package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;

public class ModelToModelServiceWithMultipleParams extends AbstractServicePlanExecutor
{
    public ModelToModelServiceWithMultipleParams()
    {
        super("service::ModelToModelServiceWithMultipleParams", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithMultipleParams.json", false);
    }

    public ModelToModelServiceWithMultipleParams(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelServiceWithMultipleParams", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelServiceWithMultipleParams.json", storeExecutorConfigurations);
    }

    public Result execute(String i_s, long i_i, double i_f, BigDecimal i_dec, boolean i_b, LocalDate i_sd, ZonedDateTime i_dt, Temporal i_d, Long i_oi, List<? extends Long> i_li)
    {
        return this.execute(i_s, i_i, i_f, i_dec, i_b, i_sd, i_dt, i_d, i_oi, i_li, null);
    }

    public Result execute(String i_s, long i_i, double i_f, BigDecimal i_dec, boolean i_b, LocalDate i_sd, ZonedDateTime i_dt, Temporal i_d, Long i_oi, List<? extends Long> i_li, StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(10)
                     .withStreamProvider(streamProvider)
                     .withParameter("i_s", i_s)
                     .withParameter("i_i", i_i)
                     .withParameter("i_f", i_f)
                     .withParameter("i_dec", i_dec)
                     .withParameter("i_b", i_b)
                     .withParameter("i_sd", i_sd)
                     .withParameter("i_dt", i_dt)
                     .withParameter("i_d", i_d)
                     .withParameter("i_oi", i_oi)
                     .withParameter("i_li", i_li)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.of(
            this.newServiceVariable("i_s", String.class, 1, 1),
            this.newServiceVariable("i_i", Long.class, 1, 1),
            this.newServiceVariable("i_f", Double.class, 1, 1),
            this.newServiceVariable("i_dec", BigDecimal.class, 1, 1),
            this.newServiceVariable("i_b", Boolean.class, 1, 1),
            this.newServiceVariable("i_sd", LocalDate.class, 1, 1),
            this.newServiceVariable("i_dt", ZonedDateTime.class, 1, 1),
            this.newServiceVariable("i_d", Temporal.class, 1, 1),
            this.newServiceVariable("i_oi", Long.class, 0, 1),
            this.newServiceVariable("i_li", Long.class, 0, null)
        );
    }
}