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

package org.finos.legend.sdlc.generation.file;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.external.shared.format.extension.GenerationExtension;
import org.finos.legend.engine.shared.core.operational.Assert;
import org.finos.legend.pure.generated.Root_meta_pure_generation_metamodel_GenerationOutput;

import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class FileGenerator
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FileGenerator.class);
    public MutableListMultimap<String, GenerationExtension> extensions;
    private FileGenerationSpecification fileGeneration;
    private PureModel pureModel;

    private FileGenerator(PureModel pureModel, FileGenerationSpecification fileGeneration)
    {
        if (pureModel == null || fileGeneration == null)
        {
            throw new RuntimeException("File generation and pure model required for file generation transformer");
        }
        this.pureModel = pureModel;
        this.fileGeneration = fileGeneration;
        this.extensions = Iterate.addAllTo(ServiceLoader.load(GenerationExtension.class), Lists.mutable.empty()).groupBy(GenerationExtension::getKey);
    }

    public static FileGenerator newGenerator(PureModel pureModel, FileGenerationSpecification fileGeneration)
    {
        return new FileGenerator(pureModel, fileGeneration);
    }

    public  List<GenerationOutput> generate()
    {
        List<GenerationOutput> result = new ArrayList<>();
        try
        {
            LOGGER.info("Start generating file generation '" + this.fileGeneration.getPath() + "' with type '" + fileGeneration.type + "'");
            List<? extends Root_meta_pure_generation_metamodel_GenerationOutput> outputs = this.transform();
            outputs.forEach(generationOutput -> result.add(new GenerationOutput(generationOutput._content(), generationOutput._fileName(), generationOutput._format())));
            LOGGER.info("Done generating files, {} files generated", outputs.size());
            return result;
        }
        catch (Exception e)
        {
            LOGGER.info("Error generating file Generation '" + fileGeneration.getPath() + "':", e);
            throw new RuntimeException(e);
        }
    }

    private List<? extends Root_meta_pure_generation_metamodel_GenerationOutput> transform()
    {
        GenerationExtension extension = this.extensions.get(fileGeneration.type).getFirst();
        Assert.assertTrue(extension != null, () -> "Can't find a handler for the file type '" + fileGeneration.type.toLowerCase() + "'");
        List<Root_meta_pure_generation_metamodel_GenerationOutput> outputs =  extension.generateFromElement(fileGeneration, pureModel.getContext());
        Assert.assertTrue(outputs != null, () -> "No generator found for file generation for file type '" + fileGeneration.type.toLowerCase() + "'");
        return outputs;
    }
}
