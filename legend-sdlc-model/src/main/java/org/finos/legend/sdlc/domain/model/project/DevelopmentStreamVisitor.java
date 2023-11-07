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

import java.util.function.Function;

public interface DevelopmentStreamVisitor<T> extends Function<DevelopmentStream, T>
{
    default T visit(ProjectDevelopmentStream source)
    {
        throw new UnsupportedOperationException("Unsupported development stream specification: " + source);
    }

    default T visit(Patch source)
    {
        throw new UnsupportedOperationException("Unsupported development stream specification: " + source);
    }

    @Override
    default T apply(DevelopmentStream source)
    {
        return source.visit(this);
    }

}
