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

package org.finos.legend.sdlc.generation.artifact;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.extension.ArtifactGenerationExtension;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.sdlc.generation.file.GenerationOutput;
import org.junit.Assert;
import org.junit.Test;

public class TestArtifactGenerationFactory
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
    public void testArtifactGenerationFactory()
    {
        PureModelContextData data = getPureModelContextDataFromPath("ArtifactGenerationFactoryTestData.json");
        PureModel pureModel = new PureModel(data, null, DeploymentMode.PROD);
        ArtifactGenerationFactory factory = new ArtifactGenerationFactory(pureModel, data, data.getElements());
        Assert.assertEquals(2, factory.getExtensions().size());
        TestSimpleFunctionArtifactGenerationExtension functionArtifactGenerationExtension = LazyIterate.selectInstancesOf(factory.getExtensions(), TestSimpleFunctionArtifactGenerationExtension.class).getFirst();
        TestSimpleEnumArtifactGenerationExtension enumArtifactGenerationExtension =  LazyIterate.selectInstancesOf(factory.getExtensions(), TestSimpleEnumArtifactGenerationExtension.class).getFirst();
        MutableMap<ArtifactGenerationExtension, List<ArtifactGenerationResult>> results = factory.generate();
        Assert.assertEquals(2, results.size());
        
        // check enum generation extension
        List<ArtifactGenerationResult> enumFullResults =  results.get(enumArtifactGenerationExtension);
        Assert.assertEquals(1, enumFullResults.size());
        ArtifactGenerationResult enumResult = enumFullResults.get(0);
        Assert.assertEquals(TestSimpleEnumArtifactGenerationExtension.class, enumResult.getGenerator().getClass());
        Assert.assertEquals(enumResult.getElement(), pureModel.getPackageableElement("model::MyEnum"));
        Assert.assertEquals(1, enumResult.getResults().size());
        GenerationOutput enumOutput = enumResult.getResults().get(0);
        Assert.assertEquals("Some output for enumeration 'MyEnum'", enumOutput.getContent());
        Assert.assertEquals("txt", enumOutput.getFormat());
        Assert.assertEquals("SomeTestOutput.txt", enumOutput.getFileName());
        
        // check function generation results
        List<ArtifactGenerationResult> functionResults =  results.get(functionArtifactGenerationExtension);
        Assert.assertEquals(1, functionResults.size());
        ArtifactGenerationResult functionResult = functionResults.get(0);
        Assert.assertEquals(TestSimpleFunctionArtifactGenerationExtension.class, functionResult.getGenerator().getClass());
        Assert.assertEquals(functionResult.getElement(), pureModel.getPackageableElement("model::myFunction_String_1__String_1_"));
        Assert.assertEquals(1, functionResult.getResults().size());
        GenerationOutput functionOutput = functionResult.getResults().get(0);
        Assert.assertEquals("Some output for function 'myFunction_String_1__String_1_'", functionOutput.getContent());
        Assert.assertEquals("txt", functionOutput.getFormat());
        Assert.assertEquals("MyFunction.txt", functionOutput.getFileName());
    }
}
