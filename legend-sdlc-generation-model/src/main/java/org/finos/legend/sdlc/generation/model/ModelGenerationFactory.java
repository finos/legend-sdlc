// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.generation.model;

import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.model.ModelGenerationRunner;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;

import java.util.concurrent.ForkJoinPool;

public class ModelGenerationFactory
{
    private final ModelGenerationRunner runner;

    ModelGenerationFactory(GenerationSpecification generationSpecification, PureModelContextData protocol, PureModel pureModel)
    {
        this.runner = ModelGenerationRunner.newRunner(generationSpecification, protocol, pureModel)
                .withCompiler(model -> new PureModel(model, null, null, DeploymentMode.PROD));
    }

    public static ModelGenerationFactory newFactory(GenerationSpecification generationSpecification, PureModelContextData protocol, PureModel pureModel)
    {
        return new ModelGenerationFactory(generationSpecification, protocol, pureModel);
    }

    public static ModelGenerationFactory newFactory(GenerationSpecification generationSpecification, PureModelContextData protocol)
    {
        return new ModelGenerationFactory(generationSpecification, protocol, null);
    }

    /**
     * Generates the elements of a generation node in the given pool. See
     * {@link ModelGenerationRunner#withForkJoinPool(ForkJoinPool)} before passing one.
     */
    public ModelGenerationFactory withForkJoinPool(ForkJoinPool forkJoinPool)
    {
        this.runner.withForkJoinPool(forkJoinPool);
        return this;
    }

    public PureModelContextData generate() throws Exception
    {
        return this.runner.run().getGeneratedModel();
    }

    public void processModelGenerator(ModelGenerator modelGeneratorInterface)
    {
        this.runner.addGeneratedModel(modelGeneratorInterface.getName(), modelGeneratorInterface.generateModel());
        this.runner.getPureModel();
    }

    public PureModelContextData validateAndBuildGeneratedModel()
    {
        return this.runner.getGeneratedModel();
    }

    public PureModelContextData getFullModel()
    {
        return this.runner.getFullModel();
    }

    public PureModelContextData getGeneratedModel()
    {
        return this.runner.getGeneratedModel();
    }

    public PureModel getPureModel()
    {
        return this.runner.getPureModel();
    }
}
