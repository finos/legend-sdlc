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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerContext;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.junit.Assert;

import java.util.List;
import java.util.Set;

public class PureProtocolHelper
{
    public static void assertElementEquals(String expected, PackageableElement actual)
    {
        assertElementEquals(null, expected, actual);
    }

    public static void assertElementEquals(String message, String expected, PackageableElement actual)
    {
        Assert.assertEquals(message, expected, serializeForComparison(actual));
    }

    public static void assertElementsEqual(List<String> expected, List<? extends PackageableElement> actual)
    {
        assertElementsEqual(null, expected, actual);
    }

    public static void assertElementsEqual(String message, List<String> expected, List<? extends PackageableElement> actual)
    {
        List<String> actualSerialized = Iterate.collect(actual, PureProtocolHelper::serializeForComparison, Lists.mutable.empty());
        Assert.assertEquals(message, expected, actualSerialized);
    }

    public static void assertElementsEqual(Set<String> expected, Iterable<? extends PackageableElement> actual)
    {
        assertElementsEqual(null, expected, actual);
    }

    public static void assertElementsEqual(String message, Set<String> expected, Iterable<? extends PackageableElement> actual)
    {
        Set<String> actualSerialized = Iterate.collect(actual, PureProtocolHelper::serializeForComparison, Sets.mutable.empty());
        Assert.assertEquals(message, expected, actualSerialized);
    }

    public static String serializeForComparison(PackageableElement element)
    {
        PureModelContextData pureModelContextData = PureModelContextData.newBuilder().withElement(element).build();
        PureGrammarComposerContext composerContext = PureGrammarComposerContext.Builder.newInstance()
                .withRenderStyle(PureGrammarComposerContext.RenderStyle.PRETTY)
                .build();
        String result = PureGrammarComposer.newInstance(composerContext).renderPureModelContextData(pureModelContextData);
        if (result.startsWith("###"))
        {
            result = result.substring(result.indexOf('\n') + 1);
        }
        return result;
    }
}
