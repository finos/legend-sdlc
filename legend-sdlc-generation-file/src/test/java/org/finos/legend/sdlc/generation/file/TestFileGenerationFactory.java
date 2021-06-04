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

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.fileGeneration.FileGenerationSpecification;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.List;
import java.util.Objects;

public class TestFileGenerationFactory
{
    private PureModelContextData getPureModelContextDataFromPath(String path)
    {
        URL url = Objects.requireNonNull(getClass().getClassLoader().getResource(path), "Can't find resource '" + path + "'");
        try
        {
            JsonMapper jsonMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder().build());
            return jsonMapper.readValue(url, PureModelContextData.class);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to build PureModelContextData with path '" + path + "'", e);
        }
    }

    @Test
    public void testUnsupportedFileGeneratorTypeHandler()
    {
        String UNSUPPORTED = "UnsupportedType";
        FileGenerationSpecification fileGenerationSpecification = new FileGenerationSpecification();
        fileGenerationSpecification.type = UNSUPPORTED;
        fileGenerationSpecification.name = "MyFileGeneration";
        fileGenerationSpecification._package = "package";
        FileGenerator fileGenerator = FileGenerator.newGenerator(new PureModel(PureModelContextData.newBuilder().build(), null, DeploymentMode.TEST), fileGenerationSpecification);
        EngineException handlerException = Assert.assertThrows(EngineException.class, fileGenerator::generate);
        Assert.assertEquals("Can't find a handler for the file generation type '" + UNSUPPORTED.toLowerCase() + "'", handlerException.getMessage());
    }

    private void testAvroOutput(List<GenerationOutput> avroResult)
    {
        Assert.assertEquals(2, avroResult.size());
        MapIterable<String, GenerationOutput> avroOutputs = LazyIterate.adapt(avroResult).groupByUniqueKey(GenerationOutput::getFileName);
        Assert.assertEquals("{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"firstName\",\"type\":\"string\"},{\"name\":\"lastName\",\"type\":\"string\"}]}", avroOutputs.get("model/Person.avro").getContent());
        Assert.assertEquals("{\"type\":\"record\",\"name\":\"Firm\",\"fields\":[{\"name\":\"employees\",\"type\":{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"firstName\",\"type\":\"string\"},{\"name\":\"lastName\",\"type\":\"string\"}]}}]}", avroOutputs.get("model/Firm.avro").getContent());
        avroResult.forEach(e -> Assert.assertEquals("json", e.getFormat()));
    }

    @Test
    public void testFileGenerator()
    {
        PureModelContextData pureModelContextData = getPureModelContextDataFromPath("FileGenerationFactoryTestData.json");
        MapIterable<String, FileGenerationSpecification> specifications = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), FileGenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath);
        FileGenerator fileGenerator = FileGenerator.newGenerator(new PureModel(pureModelContextData, null, DeploymentMode.TEST), specifications.get("generation::MyAvro"));
        List<GenerationOutput> avroResult = fileGenerator.generate();
        testAvroOutput(avroResult);
    }

    @Test
    public void testFileGenerationFactory()
    {
        PureModelContextData pureModelContextData = getPureModelContextDataFromPath("FileGenerationFactoryTestData.json");
        GenerationSpecification generationSpecification = pureModelContextData.getElementsOfType(GenerationSpecification.class).get(0);
        FileGenerationFactory factory = FileGenerationFactory.newFactory(generationSpecification, pureModelContextData);
        MutableMap<FileGenerationSpecification, List<GenerationOutput>> result = factory.generateFiles();
        MapIterable<String, FileGenerationSpecification> specifications = LazyIterate.selectInstancesOf(pureModelContextData.getElements(), FileGenerationSpecification.class).groupByUniqueKey(PackageableElement::getPath);

        // avro
        List<GenerationOutput> avroResult = result.get(specifications.get("generation::MyAvro"));
        testAvroOutput(avroResult);

        // protobuf
        List<GenerationOutput> protoBufResult = result.get(specifications.get("generation::MyProtobuf"));
        Assert.assertEquals(2, protoBufResult.size());
        MapIterable<String, GenerationOutput> protobufOutputs = LazyIterate.adapt(protoBufResult).groupByUniqueKey(GenerationOutput::getFileName);
        Assert.assertEquals("message Person {\n" +
                " string firstname = 1;\n" +
                " string lastname = 2;\n" +
                "}", protobufOutputs.get("model/Person.proto").getContent());
        Assert.assertEquals("message Firm {\n" +
                " Person employees = 1;\n" +
                "}\n" +
                "message Person {\n" +
                " string firstname = 1;\n" +
                " string lastname = 2;\n" +
                "}", protobufOutputs.get("model/Firm.proto").getContent());
        // rosetta
        List<GenerationOutput> rosettaResult = result.get(specifications.get("generation::MyRosetta"));
        Assert.assertEquals(1, rosettaResult.size());
        Assert.assertEquals("rosettaTypes.txt", rosettaResult.get(0).getFileName());
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

}
