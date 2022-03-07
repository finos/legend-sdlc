// Copyright 2022 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.service;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.dsl.service.execution.AbstractServicePlanExecutor;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceVariable;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.util.List;

public class ModelToModelService extends AbstractServicePlanExecutor
{
    public ModelToModelService()
    {
        super("service::ModelToModelService", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelService.json", false);
    }

    public ModelToModelService(StoreExecutorConfiguration... storeExecutorConfigurations)
    {
        super("service::ModelToModelService", "org/finos/legend/sdlc/generation/service/entities/service/ModelToModelService.json", storeExecutorConfigurations);
    }

    public Result execute()
    {
        return this.execute(null);
    }

    public Result execute(StreamProvider streamProvider)
    {
        return this.newExecutionBuilder(0)
                     .withStreamProvider(streamProvider)
                     .execute();
    }

    @Override
    public final List<ServiceVariable> getServiceVariables()
    {
        return Lists.mutable.empty();
    }
}