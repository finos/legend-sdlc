// Copyright 2026 Goldman Sachs
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

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.defect.Defect;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.defect.DefectSeverityLevel;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.extension.CompilerExtension;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.extension.Processor;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;

import java.util.Collections;
import java.util.List;

public class TestDefectCompilerExtension implements CompilerExtension
{
    static final String ERROR_PACKAGE = "test::defect::error";
    static final String WARNING_PACKAGE = "test::defect::warning";

    @Override
    public Iterable<? extends Processor<?>> getExtraProcessors()
    {
        return Collections.emptyList();
    }

    @Override
    public List<Procedure2<PureModel, PureModelContextData>> getExtraPostValidators()
    {
        return Collections.singletonList((pureModel, pureModelContextData) -> pureModelContextData.getElements().forEach(element ->
        {
            if (ERROR_PACKAGE.equals(element._package))
            {
                pureModel.addDefects(Collections.singletonList(new Defect(DefectSeverityLevel.ERROR, element.sourceInformation, "Test error defect for " + element.getPath(), "TestError")));
            }
            else if (WARNING_PACKAGE.equals(element._package))
            {
                pureModel.addDefects(Collections.singletonList(new Defect(DefectSeverityLevel.WARN, element.sourceInformation, "Test warning defect for " + element.getPath(), "TestWarning")));
            }
        }));
    }
}
