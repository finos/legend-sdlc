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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;

import java.util.List;

public class FileGenerationFactory
{
    private final GenerationSpecification generationSpecification;
    private final PureModel pureModel;
    private final MapIterable<String, FileGenerationSpecification> fileGenerationSpecifications;

    FileGenerationFactory(GenerationSpecification generationSpecification, PureModelContextData pureModelContextData, PureModel pureModel)
    {
        this.pureModel = pureModel;
        this.generationSpecification = generationSpecification;
        this.fileGenerationSpecifications = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), FileGenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath);
    }

    public static FileGenerationFactory newFactory(GenerationSpecification generationSpecification, PureModelContextData pureModelContextData, PureModel pureModel)
    {
        return new FileGenerationFactory(generationSpecification, pureModelContextData, pureModel);
    }

    public MutableMap<FileGenerationSpecification, List<GenerationOutput>> generateFiles()
    {

        if ((this.generationSpecification._package == null) || this.generationSpecification._package.isEmpty())
        {
            throw new RuntimeException("Invalid generation specifications, missing path '" + this.generationSpecification.name);
        }
        MutableMap<FileGenerationSpecification, List<GenerationOutput>> result = Maps.mutable.empty();
        for (PackageableElementPointer fileGenerationPointer : this.generationSpecification.fileGenerations)
        {
            FileGenerationSpecification fileGenerationSpecification = this.fileGenerationSpecifications.get(fileGenerationPointer.path);
            if (fileGenerationSpecification == null)
            {
                throw new RuntimeException("File generation '" + fileGenerationPointer.path + "' not found in model");
            }
            if (!result.contains(fileGenerationSpecification))
            {
                FileGenerator fileGenerator = FileGenerator.newGenerator(this.pureModel, fileGenerationSpecification);
                List<GenerationOutput> outputs = fileGenerator.generate();
                result.put(fileGenerationSpecification, outputs);
            }
        }
        return result;
    }

}
