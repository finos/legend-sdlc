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

package org.finos.legend.sdlc.server.error;

import org.junit.Assert;
import org.junit.Test;

import java.util.function.Function;
import java.util.function.Predicate;
import javax.ws.rs.core.Response.Status;

public class TestLegendSDLCServerException
{
    @Test
    public void testValidateNonNull()
    {
        String message = "the quick brown fox jumped over the lazy dog";
        Object someObject = new Object();

        Assert.assertSame(someObject, LegendSDLCServerException.validateNonNull(someObject, message));

        LegendSDLCServerException e1 = Assert.assertThrows(LegendSDLCServerException.class, () -> LegendSDLCServerException.validateNonNull(null, message));
        Assert.assertEquals(message, e1.getMessage());
        Assert.assertEquals(Status.BAD_REQUEST, e1.getStatus());

        Status status = Status.INTERNAL_SERVER_ERROR;
        LegendSDLCServerException e2 = Assert.assertThrows(message, LegendSDLCServerException.class, () -> LegendSDLCServerException.validateNonNull(null, message, status));
        Assert.assertEquals(message, e2.getMessage());
        Assert.assertEquals(status, e2.getStatus());
    }

    @Test
    public void testValidate_Message()
    {
        String message = "Cogito, ergo sum.";
        Object validObject = new Object();
        Object invalidObject = new Object();
        Predicate<Object> predicate = x -> x == validObject;

        Assert.assertSame(validObject, LegendSDLCServerException.validate(validObject, predicate, message));

        LegendSDLCServerException e1 = Assert.assertThrows(LegendSDLCServerException.class, () -> LegendSDLCServerException.validate(invalidObject, predicate, message));
        Assert.assertEquals(message, e1.getMessage());
        Assert.assertEquals(Status.BAD_REQUEST, e1.getStatus());

        Status status = Status.NOT_IMPLEMENTED;
        LegendSDLCServerException e2 = Assert.assertThrows(message, LegendSDLCServerException.class, () -> LegendSDLCServerException.validate(invalidObject, predicate, message, status));
        Assert.assertEquals(message, e2.getMessage());
        Assert.assertEquals(status, e2.getStatus());
    }

    @Test
    public void testValidate_MessageFunction()
    {
        String message = "Soon you'll be ashes or bones. A mere name at most - and even that is just a sound, an echo. The things we want in life are empty, stale, trivial.";
        int[] messageFnCount = {0};
        Function<Object, String> messageFn = x ->
        {
            messageFnCount[0]++;
            return message;
        };

        Object validObject = new Object();
        Object invalidObject = new Object();
        Predicate<Object> predicate = x -> x == validObject;

        Assert.assertSame(validObject, LegendSDLCServerException.validate(validObject, predicate, messageFn));
        Assert.assertEquals(0, messageFnCount[0]);

        LegendSDLCServerException e1 = Assert.assertThrows(LegendSDLCServerException.class, () -> LegendSDLCServerException.validate(invalidObject, predicate, messageFn));
        Assert.assertEquals(message, e1.getMessage());
        Assert.assertEquals(1, messageFnCount[0]);
        Assert.assertEquals(Status.BAD_REQUEST, e1.getStatus());

        Status status = Status.CONFLICT;
        LegendSDLCServerException e2 = Assert.assertThrows(message, LegendSDLCServerException.class, () -> LegendSDLCServerException.validate(invalidObject, predicate, messageFn, status));
        Assert.assertEquals(message, e2.getMessage());
        Assert.assertEquals(2, messageFnCount[0]);
        Assert.assertEquals(status, e2.getStatus());
    }
}
