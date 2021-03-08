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
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.generationSpecification.GenerationSpecification;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class TestModelGenerationFactory
{

    private GenerationSpecification getTestGenerationSpecification()
    {
        GenerationSpecification generationSpecification = new GenerationSpecification();
        generationSpecification.name = "MyGenerationSpecification";
        generationSpecification._package = "model";
        return generationSpecification;
    }

    @Test
    public void testGenerationSpecificationWithNoModelGenerations() throws Exception
    {
        GenerationSpecification generationSpecification = this.getTestGenerationSpecification();
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElement(generationSpecification);
        ModelGenerationFactory factory = ModelGenerationFactory.newFactory(generationSpecification, builder.build());
        Assert.assertEquals(1, factory.getFullModel().getElements().size());
        Assert.assertEquals(0, factory.getGeneratedModel().getElements().size());
        factory.generate();
        Assert.assertEquals(1, factory.getFullModel().getElements().size());
        Assert.assertEquals(0, factory.getGeneratedModel().getElements().size());
    }

    ModelGenerator simpleClassGenerator = new ModelGenerator()
    {
        @Override
        public String getName()
        {
            return "MyTest";
        }

        @Override
        public PureModelContextData generateModel()
        {
            PureModelContextData.Builder builder = PureModelContextData.newBuilder();
            Class myClass = new Class();
            myClass.name = "MyClass";
            myClass._package = "model";
            builder.addElement(myClass);
            return builder.build();
        }
    };

    ModelGenerator classDependedOnSimpleClassGenerator = new ModelGenerator()
    {
        @Override
        public String getName()
        {
            return "MyTest";
        }

        @Override
        public PureModelContextData generateModel()
        {
            PureModelContextData.Builder builder = PureModelContextData.newBuilder();
            Class myClass = new Class();
            myClass.name = "MyComplexClass";
            myClass._package = "model";
            myClass.superTypes = Collections.singletonList("model::MyClass");
            builder.addElement(myClass);
            return builder.build();
        }
    };

    @Test
    public void testSimpleModelGenerationFactory()
    {
        GenerationSpecification generationSpecification = this.getTestGenerationSpecification();
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElement(generationSpecification);
        ModelGenerationFactory factory = ModelGenerationFactory.newFactory(generationSpecification, builder.build());
        factory.processModelGenerator(simpleClassGenerator);
        Assert.assertEquals(2, factory.getFullModel().getElements().size());
        Assert.assertEquals(1, factory.getGeneratedModel().getElements().size());
        PureModelContextData generatedModel = factory.validateAndBuildGeneratedModel();
        Assert.assertEquals(1, generatedModel.getElements().size());
        Class myClass = (Class) generatedModel.getElements().get(0);
        Assert.assertEquals("MyClass", myClass.name);
    }

    @Test
    public void testMultiStepGenerator()
    {
        GenerationSpecification generationSpecification = this.getTestGenerationSpecification();
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElement(generationSpecification);
        ModelGenerationFactory factory = ModelGenerationFactory.newFactory(generationSpecification, builder.build());
        factory.processModelGenerator(simpleClassGenerator);
        factory.processModelGenerator(classDependedOnSimpleClassGenerator);
        PureModel pureModel = factory.getPureModel();
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class<?> _class = pureModel.getClass("model::MyComplexClass");
        Assert.assertEquals("MyComplexClass", _class.getName());
        Assert.assertEquals(2, pureModel.getModelClasses().size());
        PureModelContextData generatedModel = factory.validateAndBuildGeneratedModel();
        Assert.assertEquals(2, generatedModel.getElements().size());
    }

    @Test
    public void testCompileError()
    {
        GenerationSpecification generationSpecification = this.getTestGenerationSpecification();
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElement(generationSpecification);
        ModelGenerationFactory factory = ModelGenerationFactory.newFactory(generationSpecification, builder.build());
        EngineException compileEngineException = Assert.assertThrows(EngineException.class, () -> factory.processModelGenerator(classDependedOnSimpleClassGenerator));
        Assert.assertEquals("Error in 'model::MyComplexClass': Can't find type 'model::MyClass'", compileEngineException.getMessage());
    }

    @Test
    public void testOverridingCoreModel()
    {
        GenerationSpecification generationSpecification = this.getTestGenerationSpecification();
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElement(generationSpecification);
        Class myClass = new Class();
        myClass.name = "MyClass";
        myClass._package = "model";
        builder.addElement(myClass);
        ModelGenerationFactory factory = ModelGenerationFactory.newFactory(generationSpecification, builder.build());
        factory.processModelGenerator(simpleClassGenerator);
        RuntimeException runtimeException = Assert.assertThrows(RuntimeException.class, factory::validateAndBuildGeneratedModel);
        Assert.assertEquals("Generated element 'model::MyClass' of type Class can't override existing element of type Class", runtimeException.getMessage());
    }

}
