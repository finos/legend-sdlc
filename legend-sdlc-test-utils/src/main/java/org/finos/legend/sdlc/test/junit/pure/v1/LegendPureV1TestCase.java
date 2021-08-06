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

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.ConnectionFirstPassBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperValueSpecificationBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.result.ConstantResult;
import org.finos.legend.engine.plan.execution.result.ErrorResult;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.result.StreamingResult;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.plan.execution.result.serialization.Serializer;
import org.finos.legend.engine.plan.generation.PlanGenerator;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.ConnectionVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.pure.generated.Root_meta_pure_runtime_Runtime_Impl;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.runtime.Runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class LegendPureV1TestCase<T extends PackageableElement> extends LegendSDLCTestCase
{
    protected final PureModel pureModel;
    protected final PureModelContextData pureModelContextData;
    protected final T entity;

    protected final PlanExecutor planExecutor;
    protected final MutableList<PlanTransformer> planTransformers;
    protected final RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions;
    protected final String pureVersion;

    private Runtime runtime;

    protected LegendPureV1TestCase(PureModel pureModel, PureModelContextData pureModelContextData, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion, T entity)
    {
        super();
        this.pureModel = pureModel;
        this.pureModelContextData = pureModelContextData;
        this.entity = entity;
        this.planTransformers = planTransformers == null ? Lists.mutable.empty() : planTransformers;
        this.extensions = extensions == null ? Lists.mutable.empty() : extensions;
        this.pureVersion = pureVersion;
        this.planExecutor = PlanExecutor.newPlanExecutorWithAvailableStoreExecutors();
    }

    @Override
    protected void doSetUp() throws Exception
    {
        // Initialize runtime
        Runtime newRuntime = new Root_meta_pure_runtime_Runtime_Impl("");
        ConnectionVisitor<org.finos.legend.pure.m3.coreinstance.meta.pure.runtime.Connection> connectionVisitor1 = new ConnectionFirstPassBuilder(this.pureModel.getContext());
        setUpTestData(conn -> newRuntime._connectionsAdd(conn.accept(connectionVisitor1)));
        this.runtime = newRuntime;
    }

    @Override
    protected void doTearDown()
    {
        // Clear runtime
        this.runtime = null;
    }

    protected abstract void setUpTestData(Consumer<? super org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection> connectionRegistrar) throws Exception;

    protected Result executeLegend(Lambda lambda, String mappingPath)
    {
        return executeLegend(lambda, mappingPath, (InputStream) null);
    }

    protected Result executeLegend(Lambda lambda, String mappingPath, String input)
    {
        return executeLegend(lambda, mappingPath, (input == null) ? null : input.getBytes(StandardCharsets.UTF_8));
    }

    protected Result executeLegend(Lambda lambda, String mappingPath, byte[] input)
    {
        return executeLegend(lambda, mappingPath, (input == null) ? null : new ByteArrayInputStream(input));
    }

    protected Result executeLegend(Lambda lambda, String mappingPath, InputStream inputStream)
    {
        LambdaFunction<?> pureLambda = HelperValueSpecificationBuilder.buildLambda(lambda, new CompileContext.Builder(this.pureModel).withElement(mappingPath).build());
        Mapping pureMapping = this.pureModel.getMapping(mappingPath);
        SingleExecutionPlan executionPlan = PlanGenerator.generateExecutionPlan(pureLambda, pureMapping, this.runtime, null, this.pureModel, null, PlanPlatform.JAVA, null, this.extensions, this.planTransformers);
        return this.planExecutor.execute(executionPlan, inputStream);
    }

    protected void assertResultEquals(String expected, Lambda lambda, String mappingPath)
    {
        Result result = executeLegend(lambda, mappingPath);
        assertResultEquals(expected, result);
    }

    protected void assertResultEquals(String expected, Lambda lambda, String mappingPath, String input)
    {
        Result result = executeLegend(lambda, mappingPath, input);
        assertResultEquals(expected, result);
    }

    protected void assertResultEquals(String expected, Lambda lambda, String mappingPath, byte[] input)
    {
        Result result = executeLegend(lambda, mappingPath, input);
        assertResultEquals(expected, result);
    }

    protected void assertResultEquals(String expected, Lambda lambda, String mappingPath, InputStream inputStream)
    {
        Result result = executeLegend(lambda, mappingPath, inputStream);
        assertResultEquals(expected, result);
    }

    protected void assertResultEquals(String expected, Result result)
    {
        JsonNode expectedJson = parseJsonString(expected);
        assertResultEquals(expectedJson, result);
    }

    protected void assertResultEquals(JsonNode expected, Result result)
    {
        assertResultEquals(nodeToList(expected), result);
    }

    protected void assertResultEquals(List<? extends JsonNode> expected, Result result)
    {
        assertEquals(expected, getResultValuesAsJson(result));
    }

    private JsonNode parseJsonString(String rawExpected)
    {
        try
        {
            return objectMapper.readTree(rawExpected);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error getting expected value from: " + rawExpected, e);
        }
    }

    private List<JsonNode> getResultValuesAsJson(Result result)
    {
        if (result instanceof ErrorResult)
        {
            throw new RuntimeException(((ErrorResult) result).getMessage());
        }
        JsonNode jsonResult = getResultAsJson(result);
        JsonNode values = jsonResult.get("values");
        return nodeToList(values);
    }

    private List<JsonNode> nodeToList(JsonNode node)
    {
        if ((node == null) || node.isMissingNode())
        {
            return Collections.emptyList();
        }
        if (node.isArray())
        {
            List<JsonNode> list = new ArrayList<>(node.size());
            node.forEach(list::add);
            return list;
        }
        return Collections.singletonList(node);
    }

    private JsonNode getResultAsJson(Result result)
    {
        if (result instanceof StreamingResult)
        {
            try
            {
                Serializer serializer = ((StreamingResult) result).getSerializer(SerializationFormat.DEFAULT);
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                serializer.stream(byteStream);
                return objectMapper.readTree(byteStream.toByteArray());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        if (result instanceof ConstantResult)
        {
            Object value = ((ConstantResult) result).getValue();
            return objectMapper.valueToTree(value);
        }
        throw new RuntimeException("Unhandled result type: " + result.getClass().getSimpleName());
    }

    protected static boolean hasPath(PackageableElement element, String path)
    {
        if ((element._package == null) || element._package.isEmpty())
        {
            return path.equals(element.name);
        }

        return (path.length() == (element._package.length() + element.name.length() + 2)) &&
                path.startsWith(element._package) &&
                path.startsWith("::", element._package.length()) &&
                path.endsWith(element.name);
    }

    protected static <T extends PackageableElement> T findPackageableElement(Collection<T> elements, String path)
    {
        Optional<T> element = elements.stream().filter(e -> hasPath(e, path)).findAny();
        if (!element.isPresent())
        {
            throw new RuntimeException("Could not find: " + path);
        }
        return element.get();
    }
}
