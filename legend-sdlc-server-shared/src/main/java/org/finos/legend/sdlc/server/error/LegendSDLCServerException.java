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

package org.finos.legend.sdlc.server.error;

import org.finos.legend.sdlc.error.LegendSDLCException;

import javax.ws.rs.core.Response.Status;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @deprecated Use {@link LegendSDLCException}, which carries the status code as a plain int and has no JAX-RS
 * dependency. This subclass is retained for temporarily so that existing code throwing, catching, or mapping
 * it (and using the JAX-RS {@link Status} API) keeps working; it will then be removed.
 */
@Deprecated
public class LegendSDLCServerException extends LegendSDLCException
{
    private static final long serialVersionUID = -427388642530259672L;
    private static final Status DEFAULT_STATUS = Status.INTERNAL_SERVER_ERROR;

    public LegendSDLCServerException(String message, Status httpStatus, Throwable cause)
    {
        super(message, ((httpStatus == null) ? DEFAULT_STATUS : httpStatus).getStatusCode(), cause);
    }

    public LegendSDLCServerException(String message, Status httpStatus)
    {
        super(message, ((httpStatus == null) ? DEFAULT_STATUS : httpStatus).getStatusCode());
    }

    public LegendSDLCServerException(String message, Throwable cause)
    {
        this(message, null, cause);
    }

    public LegendSDLCServerException(String message)
    {
        this(message, (Status) null);
    }

    public Status getStatus()
    {
        return Status.fromStatusCode(getStatusCode());
    }

    public static <T> T validateNonNull(T arg, String message)
    {
        return validateNonNull(arg, message, null);
    }

    public static <T> T validateNonNull(T arg, String message, Status httpStatus)
    {
        return validate(arg, Objects::nonNull, message, httpStatus);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, String message)
    {
        return validate(arg, predicate, message, null);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, String message, Status httpStatus)
    {
        if (!predicate.test(arg))
        {
            throw new LegendSDLCServerException(message, (httpStatus == null) ? Status.BAD_REQUEST : httpStatus);
        }
        return arg;
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, Function<? super T, String> messageFn)
    {
        return validate(arg, predicate, messageFn, null);
    }

    public static <T> T validate(T arg, Predicate<? super T> predicate, Function<? super T, String> messageFn, Status httpStatus)
    {
        if (!predicate.test(arg))
        {
            throw new LegendSDLCServerException((messageFn == null) ? null : messageFn.apply(arg), (httpStatus == null) ? Status.BAD_REQUEST : httpStatus);
        }
        return arg;
    }
}
