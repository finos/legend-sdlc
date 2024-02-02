// Copyright 2023 Goldman Sachs
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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.pure.code.core.LegendPureCoreExtension;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder;
import org.finos.legend.sdlc.language.pure.compiler.toPureGraph.PureModelBuilder.PureModelWithContextData;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;

class PureTestHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PureTestHelper.class);

    private static final Object INIT_LOCK = new Object();
    private static boolean IS_INITIALIZED = false;
    private static PureModel PURE_MODEL;
    private static PureModelContextData PURE_MODEL_CONTEXT_DATA;
    private static MapIterable<String, PackageableElement> PROTOCOL_ELEMENT_INDEX;
    private static MutableList<PlanTransformer> PLAN_TRANSFORMERS;
    private static MutableList<? extends Root_meta_pure_extension_Extension> ROUTER_EXTENSIONS;

    static PureModel getPureModel()
    {
        initialize();
        return PURE_MODEL;
    }

    static PureModelContextData getPureModelContextData()
    {
        initialize();
        return PURE_MODEL_CONTEXT_DATA;
    }

    static PackageableElement getProtocolElement(String path)
    {
        initialize();
        return PROTOCOL_ELEMENT_INDEX.get(path);
    }

    static MutableList<PlanTransformer> getPlanTransformers()
    {
        initialize();
        return PLAN_TRANSFORMERS;
    }

    static RichIterable<? extends Root_meta_pure_extension_Extension> getRouterExtensions()
    {
        initialize();
        return ROUTER_EXTENSIONS;
    }

    static void initialize()
    {
        synchronized (INIT_LOCK)
        {
            if (!IS_INITIALIZED)
            {
                LOGGER.debug("Beginning initialization");
                try
                {
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

                    PureModelWithContextData pureModelWithContextData = buildPureModelWithContextData(classLoader);
                    PURE_MODEL_CONTEXT_DATA = pureModelWithContextData.getPureModelContextData();
                    PROTOCOL_ELEMENT_INDEX = indexPureModelContextData(PURE_MODEL_CONTEXT_DATA);
                    PURE_MODEL = pureModelWithContextData.getPureModel();

                    PLAN_TRANSFORMERS = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class, classLoader), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty()).asUnmodifiable();
                    ROUTER_EXTENSIONS = Iterate.flatCollect(ServiceLoader.load(LegendPureCoreExtension.class, classLoader), e -> e.extraPureCoreExtensions(PURE_MODEL.getExecutionSupport()), Lists.mutable.empty()).asUnmodifiable();
                    LOGGER.debug("Finished initialization");
                }
                catch (Throwable t)
                {
                    LOGGER.debug("Initialization error", t);
                    PURE_MODEL = null;
                    PURE_MODEL_CONTEXT_DATA = null;
                    PROTOCOL_ELEMENT_INDEX = null;
                    PLAN_TRANSFORMERS = null;
                    ROUTER_EXTENSIONS = null;
                    throw t;
                }
                IS_INITIALIZED = true;
            }
        }
    }

    private static PureModelWithContextData buildPureModelWithContextData(ClassLoader classLoader)
    {
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(classLoader))
        {
            return PureModelBuilder.newBuilder()
                    .withEntitiesIfPossible(entityLoader.getAllEntities())
                    .withClassLoader(classLoader)
                    .build();
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading entities", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException("Error loading entities", e);
        }
    }

    private static MapIterable<String, PackageableElement> indexPureModelContextData(PureModelContextData pureModelContextData)
    {
        List<PackageableElement> elements = pureModelContextData.getElements();
        MutableMap<String, PackageableElement> index = Maps.mutable.ofInitialCapacity(elements.size());
        elements.forEach(e ->
        {
            String path = e.getPath();
            PackageableElement old = index.put(path, e);
            if (old != null)
            {
                throw new RuntimeException("Conflict for element path: " + path);
            }
        });
        return index;
    }
}
