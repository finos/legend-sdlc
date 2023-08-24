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

package org.finos.legend.sdlc.domain.model.project;

import org.finos.legend.sdlc.domain.model.patch.Patch;

import java.util.function.Consumer;

public abstract class DevelopmentStreamConsumer implements DevelopmentStreamVisitor<Void>, Consumer<DevelopmentStream>
{
    @Override
    public final Void visit(ProjectDevelopmentStream source)
    {
        accept(source);
        return null;
    }

    @Override
    public final Void visit(Patch source)
    {
        accept(source);
        return null;
    }

    @Override
    public final void accept(DevelopmentStream source)
    {
        source.visit(this);
    }

    protected void accept(ProjectDevelopmentStream source)
    {
        // nothing by default
    }

    protected void accept(Patch source)
    {
        // nothing by default
    }
}

