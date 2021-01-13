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

package org.finos.legend.sdlc.language.pure.compiler.toPureGraph;

import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.pure.generated.core_pure_serialization_toPureGrammar;
import org.finos.legend.sdlc.protocol.pure.v1.PureProtocolHelper;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Objects;

public class TestPureModelBuilder
{
    private static final String expectedSourceClass = "Class model::domain::Source\n" +
            "{\n" +
            "  oneName: String[1];\n" +
            "  anotherName: String[0..1];\n" +
            "  oneDate: StrictDate[0..1];\n" +
            "  anotherDate: StrictDate[0..1];\n" +
            "  oneNumber: Integer[0..1];\n" +
            "  anotherNumber: Integer[0..1];\n" +
            "}\n";
    private static final String expectedTargetClass = "Class model::domain::Target\n" +
            "{\n" +
            "  name: String[1];\n" +
            "  date: StrictDate[0..1];\n" +
            "  number: Integer[0..1];\n" +
            "}\n";
    private static final String expectedMapping = "Mapping model::mapping::SourceToTargetM2M\n" +
            "(\n" +
            "  *model::domain::Target[model_domain_Target]: Pure\n" +
            "  {\n" +
            "    ~src model::domain::Source\n" +
            "    name: $src.oneName,\n" +
            "    date: $src.anotherDate,\n" +
            "    number: $src.oneNumber\n" +
            "  }\n" +
            ")\n";


    private EntityLoader entityLoader;

    @Before
    public void setUpEntityLoader() throws Exception
    {
        URI resourceURI = Objects.requireNonNull(getClass().getClassLoader().getResource("pure-model-context-data-builder-test-model")).toURI();
        this.entityLoader = EntityLoader.newEntityLoader(resourceURI);
    }

    @After
    public void tearDownEntityLoader() throws Exception
    {
        if (this.entityLoader != null)
        {
            this.entityLoader.close();
        }
    }

    @Test
    public void testBuild()
    {
        PureModelBuilder builder = PureModelBuilder.newBuilder();
        Assert.assertEquals(0, builder.getElementCount());
        PureModelBuilder.PureModelWithContextData pureModelWithContextData = builder.withEntities(this.entityLoader.getAllEntities()).build();
        Assert.assertEquals(3, builder.getElementCount());
        checkPureModelContextData(pureModelWithContextData.getPureModelContextData());
        checkPureModel(pureModelWithContextData.getPureModel());
    }

    @Test
    public void testBuildPureModel()
    {
        PureModelBuilder builder = PureModelBuilder.newBuilder();
        Assert.assertEquals(0, builder.getElementCount());
        PureModel pureModel = builder.withEntities(this.entityLoader.getAllEntities()).buildPureModel();
        Assert.assertEquals(3, builder.getElementCount());
        checkPureModel(pureModel);
    }

    private void checkPureModelContextData(PureModelContextData pureModelContextData)
    {
        Assert.assertEquals(3, pureModelContextData.getElements().size());

        PureProtocolHelper.assertElementsEqual(
                Sets.mutable.with(expectedSourceClass, expectedTargetClass),
                pureModelContextData.getElementsOfType(Class.class));
        PureProtocolHelper.assertElementsEqual(
                Sets.mutable.with(expectedMapping),
                pureModelContextData.getElementsOfType(Mapping.class));
    }

    private void checkPureModel(PureModel pureModel)
    {
        checkClass(pureModel, "model::domain::Source", expectedSourceClass);
        checkClass(pureModel, "model::domain::Target", expectedTargetClass);
        checkMapping(pureModel, "model::mapping::SourceToTargetM2M");
    }

    private void checkClass(PureModel pureModel, String path, String expected)
    {
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class<?> cls = pureModel.getClass(path);
        Assert.assertNotNull(path, cls);
        String actual = core_pure_serialization_toPureGrammar.Root_meta_pure_metamodel_serialization_grammar_printClass_Class_1__String_1_(cls, pureModel.getExecutionSupport());
        Assert.assertEquals(expected.trim().replace(": ", " : "), actual);
    }

    private void checkMapping(PureModel pureModel, String path)
    {
        Assert.assertNotNull(path, pureModel.getMapping(path));
    }
}
