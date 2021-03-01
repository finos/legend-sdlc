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

import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.file.FileGenerationFactory;
import org.finos.legend.sdlc.generation.file.GenerationOutput;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestFileGenerationFactory
{

    @Test
    public void testAllFileGenerationFormats() throws Exception
    {
        List<Entity> entities = this.init("org/finos/legend/sdlc/generation/file/allFormats");
        PureModelBuilder pureModelBuilder = PureModelBuilder.newBuilder();
        pureModelBuilder.addEntitiesIfPossible(entities);
        PureModelBuilder.PureModelWithContextData pureModelWithContextData = pureModelBuilder.build();
        Map<String, PackageableElement> includedElements = new HashMap<>();
        pureModelWithContextData.getPureModelContextData().getAllElements().forEach(e -> includedElements.put(e.getPath(), e));
        PureModelContextData pureModelContextData = pureModelWithContextData.getPureModelContextData();
        GenerationSpecification generationSpecification = pureModelContextData.getElementsOfType(GenerationSpecification.class).get(0);
        MutableMap<FileGenerationSpecification, List<GenerationOutput>> result = FileGenerationFactory.newFactory(generationSpecification, pureModelContextData, pureModelWithContextData.getPureModel()).generateFiles();

        MapIterable<String, FileGenerationSpecification> specifications = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), FileGenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath);
        Assert.assertEquals(result.size(), 3);

        List<GenerationOutput> avroOutput = result.get(specifications.get("model::myAvro"));
        Assert.assertEquals(avroOutput.size(), 4);

        List<GenerationOutput> rosettaOuput = result.get(specifications.get("model::myRosetta"));
        Assert.assertEquals(rosettaOuput.size(), 1);

        List<GenerationOutput> protobufOuput = result.get(specifications.get("model::myProtobuf"));
        Assert.assertEquals(protobufOuput.size(), 4);
        Assert.assertEquals(specifications.get("model::myProtobuf").generationOutputPath, "myProtobuf");
    }

    @Test
    public void testExtractFileContent() throws Exception
    {
        GenerationOutput jsonGeneration = new GenerationOutput("[[[{\"type\":\"myName\",\"name\":\"testing\",\"fields\": []}]]]", "myFile.json", "json");
        String fileContent = jsonGeneration.extractFileContent();
        String expected = "[ [ [ {%n" +
                "  \"type\" : \"myName\",%n" +
                "  \"name\" : \"testing\",%n" +
                "  \"fields\" : [ ]%n" +
                "} ] ] ]";
        Assert.assertEquals(String.format(expected), fileContent);
    }

    private List<Entity> init(String entityDirectory) throws Exception
    {
        URL url = Thread.currentThread().getContextClassLoader().getResource(entityDirectory);
        if (url == null)
        {
            throw new RuntimeException("Could not find resource: " + entityDirectory);
        }
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(Paths.get(url.toURI())))
        {
            return entityLoader.getAllEntities().collect(Collectors.toList());
        }
    }
}
