// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.server.tools;

import org.finos.legend.sdlc.backend.api.tools.ThrowingSupplier;

import java.util.function.Predicate;

/**
 * @deprecated Retained temporarily for backward compatibility. Use
 * {@link org.finos.legend.sdlc.backend.api.tools.CallUntil} instead (note that the static
 * {@code callUntil} helper returns the relocated type).
 */
@Deprecated
public class CallUntil<T, E extends Exception> extends org.finos.legend.sdlc.backend.api.tools.CallUntil<T, E>
{
    public CallUntil(ThrowingSupplier<? extends T, ? extends E> supplier, Predicate<? super T> predicate)
    {
        super(supplier, predicate);
    }
}
