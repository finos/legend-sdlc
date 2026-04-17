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

import java.util.function.Function;

public interface SourceSpecificationVisitor<T> extends Function<SourceSpecification, T>
{
    default T visit(ProjectSourceSpecification sourceSpec)
    {
        throw new UnsupportedOperationException("Unsupported source specification: " + sourceSpec);
    }

    default T visit(VersionSourceSpecification sourceSpec)
    {
        throw new UnsupportedOperationException("Unsupported source specification: " + sourceSpec);
    }

    default T visit(PatchSourceSpecification sourceSpec)
    {
        throw new UnsupportedOperationException("Unsupported source specification: " + sourceSpec);
    }

    default T visit(WorkspaceSourceSpecification sourceSpec)
    {
        throw new UnsupportedOperationException("Unsupported source specification: " + sourceSpec);
    }

    @Override
    default T apply(SourceSpecification sourceSpec)
    {
        return sourceSpec.visit(this);
    }
}
