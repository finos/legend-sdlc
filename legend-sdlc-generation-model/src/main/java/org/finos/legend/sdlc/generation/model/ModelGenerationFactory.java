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

import org.eclipse.collections.api.block.function.Function3;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.compiler.toPureGraph.GenerationCompilerExtension;
import org.finos.legend.engine.language.pure.dsl.generation.compiler.toPureGraph.HelperGenerationSpecificationBuilder;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationTreeNode;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public class ModelGenerationFactory
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ModelGenerationFactory.class);
    private final GenerationSpecification generationSpecification;


    /**
     * This holds the core elements. We keep these elements to validate. No generated element should overwrite elements in this model
     */
    private final PureModelContextData coreModel;
    private final MutableMap<String, PackageableElement> coreModelElementIndex;
    /**
     * The full model is the model holding the elements from the original model and the elements generated from the generation specification.
     * It will grow in size after each model generation.
     * Used to recompile after each generation.
     */
    private final PureModelContextData.Builder fullModelBuilder;

    /**
     * Holds all the generated elements from the model generations
     */
    private final PureModelContextData.Builder generatedModelBuilder;

    private PureModel pureModel;


    ModelGenerationFactory(GenerationSpecification generationSpecification, PureModelContextData protocol, PureModel pureModel)
    {
        this.generationSpecification = generationSpecification;
        this.pureModel = pureModel;
        this.coreModel = protocol;
        this.coreModelElementIndex = Iterate.groupByUniqueKey(this.coreModel.getElements(), PackageableElement::getPath);
        this.fullModelBuilder = PureModelContextData.newBuilder().withPureModelContextData(protocol);
        this.generatedModelBuilder = PureModelContextData.newBuilder();
    }

    public static ModelGenerationFactory newFactory(GenerationSpecification generationSpecification, PureModelContextData protocol, PureModel pureModel)
    {
        return new ModelGenerationFactory(generationSpecification, protocol, pureModel);
    }

    public static ModelGenerationFactory newFactory(GenerationSpecification generationSpecification, PureModelContextData protocol)
    {
        PureModel pureModel =  new PureModel(protocol, null, null, DeploymentMode.PROD);
        return new ModelGenerationFactory(generationSpecification, protocol, pureModel);
    }

    public PureModelContextData generate() throws Exception
    {
        if ((this.generationSpecification._package == null) || this.generationSpecification._package.isEmpty())
        {
            throw new RuntimeException("Invalid generation specifications, missing path '" + this.generationSpecification.name);
        }
        LOGGER.info("Generation generation specification '" + generationSpecification.getPath() + "'");
        List<GenerationTreeNode> nodes = this.generationSpecification.generationNodes;
        for (GenerationTreeNode node : nodes)
        {
            LOGGER.info("Start generating generation model element '" + node.generationElement + "'");
            List<Function3<String, SourceInformation, CompileContext, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement>> extraModelGenerationSpecificationResolvers = ListIterate.flatCollect(HelperGenerationSpecificationBuilder.getGenerationCompilerExtensions(this.pureModel.getContext()), GenerationCompilerExtension::getExtraModelGenerationSpecificationResolvers);
            org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement generationElement = extraModelGenerationSpecificationResolvers.stream().map(resolver -> resolver.value(node.generationElement, node.sourceInformation, this.pureModel.getContext())).filter(Objects::nonNull).findFirst()
                    .orElseThrow(() -> new EngineException("Can't find generation element '" + node.generationElement + "'", node.sourceInformation, EngineErrorType.COMPILATION));
            ModelGenerator modelGenerator = ModelGenerator.newGenerator(generationElement, this.pureModel);
            processModelGenerator(modelGenerator);
        }
        return validateAndBuildGeneratedModel();
    }


    public void processModelGenerator(ModelGenerator modelGeneratorInterface)
    {
        try
        {
            PureModelContextData generatedModelFromModelGenerator = modelGeneratorInterface.generateModel();
            LOGGER.info("Finished generating model for '" + modelGeneratorInterface.getName() + "', " + generatedModelFromModelGenerator.getElements().size() + " elements generated");
            this.fullModelBuilder.withPureModelContextData(generatedModelFromModelGenerator).distinct().sorted();
            this.generatedModelBuilder.withPureModelContextData(generatedModelFromModelGenerator).distinct().sorted();
        }
        catch (Exception error)
        {
            LOGGER.info("Error generating element '" + modelGeneratorInterface.getName() + "'", error.getMessage());
            throw error;
        }
        LOGGER.info("Recompiling graph with generated elements");
        this.pureModel = new PureModel(this.fullModelBuilder.build(), null, null, DeploymentMode.PROD);
        LOGGER.info("Finished recompiling graph");
    }

    public PureModelContextData validateAndBuildGeneratedModel()
    {
        LOGGER.info("Validating generated elements");
        PureModelContextData generatedPureModelContextData = this.generatedModelBuilder.build();
        validateGeneratedElements(generatedPureModelContextData);
        LOGGER.info("Finished generating generation specification. Generated " + generatedPureModelContextData.getElements().size() + " elements");
        return generatedPureModelContextData;
    }

    private void validateGeneratedElements(PureModelContextData generatedPureModelContextData)
    {
        generatedPureModelContextData.getElements().forEach(e ->
        {
            String path = e.getPath();
            PackageableElement coreElement = this.coreModelElementIndex.get(path);
            if (coreElement != null)
            {
                throw new RuntimeException("Generated element '" + path + "' of type " + e.getClass().getSimpleName() + " can't override existing element of type " + coreElement.getClass().getSimpleName());
            }
        });
    }

    public PureModelContextData getFullModel()
    {
        return this.fullModelBuilder.build();
    }

    public PureModelContextData getGeneratedModel()
    {
        return this.generatedModelBuilder.build();
    }

    public PureModel getPureModel()
    {
        return this.pureModel;
    }

}

