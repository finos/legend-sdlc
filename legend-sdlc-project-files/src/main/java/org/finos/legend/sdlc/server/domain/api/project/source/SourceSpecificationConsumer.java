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

package org.finos.legend.sdlc.server.domain.api.project.source;

import java.util.function.Consumer;

public abstract class SourceSpecificationConsumer implements SourceSpecificationVisitor<Void>, Consumer<SourceSpecification>
{
    @Override
    public final Void visit(ProjectSourceSpecification sourceSpec)
    {
        accept(sourceSpec);
        return null;
    }

    @Override
    public final Void visit(VersionSourceSpecification sourceSpec)
    {
        accept(sourceSpec);
        return null;
    }

    @Override
    public final Void visit(PatchSourceSpecification sourceSpec)
    {
        accept(sourceSpec);
        return null;
    }

    @Override
    public final Void visit(WorkspaceSourceSpecification sourceSpec)
    {
        accept(sourceSpec);
        return null;
    }

    @Override
    public void accept(SourceSpecification sourceSpec)
    {
        sourceSpec.visit(this);
    }

    protected void accept(ProjectSourceSpecification sourceSpec)
    {
        // nothing by default
    }

    protected void accept(VersionSourceSpecification sourceSpec)
    {
        // nothing by default
    }

    protected void accept(PatchSourceSpecification sourceSpec)
    {
        // nothing by default
    }

    protected void accept(WorkspaceSourceSpecification sourceSpec)
    {
        // nothing by default
    }
}
