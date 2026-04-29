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

package org.finos.legend.sdlc.error;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class LegendSDLCException extends RuntimeException
{
    protected static final int DEFAULT_STATUS_CODE = 500;
    protected static final int DEFAULT_VALIDATION_STATUS_CODE = 400;

    private final int statusCode;

    public LegendSDLCException(String message, int statusCode, Throwable cause)
    {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public LegendSDLCException(String message, int statusCode)
    {
        super(message);
        this.statusCode = statusCode;
    }

    public LegendSDLCException(String message, Throwable cause)
    {
        this(message, DEFAULT_STATUS_CODE, cause);
    }

    public LegendSDLCException(String message)
    {
        this(message, DEFAULT_STATUS_CODE);
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public static <T> T validateNonNull(T arg, String message)
    {
        return validateNonNull(arg, message, DEFAULT_VALIDATION_STATUS_CODE);
    }

    public static <T> T validateNonNull(T arg, String message, int statusCode)
    {
        return validate(arg, Objects::nonNull, message, statusCode);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, String message)
    {
        return validate(arg, predicate, message, DEFAULT_VALIDATION_STATUS_CODE);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, String message, int statusCode)
    {
        if (!predicate.test(arg))
        {
            throw new LegendSDLCException(message, statusCode);
        }
        return arg;
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, Function<? super T, String> messageFn)
    {
        return validate(arg, predicate, messageFn, DEFAULT_VALIDATION_STATUS_CODE);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, Function<? super T, String> messageFn, int statusCode)
    {
        if (!predicate.test(arg))
        {
            throw new LegendSDLCException((messageFn == null) ? null : messageFn.apply(arg), statusCode);
        }
        return arg;
    }
}
