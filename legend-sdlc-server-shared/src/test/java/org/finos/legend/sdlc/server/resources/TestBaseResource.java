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

package org.finos.legend.sdlc.server.resources;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.SubstituteLoggerFactory;

import java.util.List;
import javax.ws.rs.core.Response;

public class TestBaseResource
{
    @Test
    public void testExecuteWithLogging_NoException()
    {
        testExecuteWithLogging_NoException(
                "getting project ABCD",
                "getting project ABCD");
        testExecuteWithLogging_NoException(
                "getting project A\nB\r\nC\tD",
                "getting project A_B__C_D");
    }

    private void testExecuteWithLogging_NoException(String description, String descriptionInLog)
    {
        TestResource resource = new TestResource();
        Object expectedReturn = new Object();

        Object actualReturn = resource.executeWithLogging(description, () -> expectedReturn);
        Assert.assertSame(expectedReturn, actualReturn);
        List<SubstituteLoggingEvent> events = resource.getLoggingEvents();
        Assert.assertEquals(2, events.size());

        SubstituteLoggingEvent event1 = events.get(0);
        Assert.assertSame(Level.INFO, event1.getLevel());
        Assert.assertEquals("Starting {}", event1.getMessage());
        Assert.assertArrayEquals(new Object[]{descriptionInLog}, event1.getArgumentArray());

        SubstituteLoggingEvent event2 = events.get(1);
        Assert.assertSame(Level.INFO, event2.getLevel());
        String expectedPattern = "Finished \\Q" + descriptionInLog + "\\E \\(\\d+\\.\\d{9}s\\)";
        if (!event2.getMessage().matches(expectedPattern))
        {
            Assert.fail("Failed to match \"" + expectedPattern + "\": " + event2.getMessage());
        }
    }

    @Test
    public void testExecuteWithLogging_LegendSDLCServerException()
    {
        testExecuteWithLogging_LegendSDLCServerException(
                "getting workspace WS1 for project X",
                "getting workspace WS1 for project X",
                "Something went wrong",
                "Something went wrong",
                Response.Status.INTERNAL_SERVER_ERROR);
        testExecuteWithLogging_LegendSDLCServerException(
                "Changing the following things:\n\t- one\n\t- two\n\t- three",
                "Changing the following things:__- one__- two__- three",
                "There is a conflict in:\n- this\n\n- that\n\n\n- the other",
                "There is a conflict in: - this - that - the other",
                Response.Status.CONFLICT);
        testExecuteWithLogging_LegendSDLCServerException(
                "getting projects",
                "getting projects",
                null,
                null,
                Response.Status.FORBIDDEN);
        testExecuteWithLogging_LegendSDLCServerException(
                "getting projects",
                "getting projects",
                "http://some.url/somewhere?with=parameter",
                "http://some.url/somewhere?with=parameter",
                Response.Status.FOUND);
    }

    private void testExecuteWithLogging_LegendSDLCServerException(String description, String descriptionInLog, String exceptionMessage, String exceptionMessageInLog, Response.Status status)
    {
        TestResource resource = new TestResource();

        LegendSDLCServerException e = Assert.assertThrows(LegendSDLCServerException.class, () -> resource.executeWithLogging(description, () ->
        {
            throw new LegendSDLCServerException(exceptionMessage, status);
        }));
        Assert.assertSame(exceptionMessage, e.getMessage());
        Assert.assertSame(status, e.getStatus());

        List<SubstituteLoggingEvent> events = resource.getLoggingEvents();
        Assert.assertEquals(2, events.size());

        SubstituteLoggingEvent event1 = events.get(0);
        Assert.assertSame(Level.INFO, event1.getLevel());
        Assert.assertEquals("Starting {}", event1.getMessage());
        Assert.assertArrayEquals(new Object[]{descriptionInLog}, event1.getArgumentArray());

        SubstituteLoggingEvent event2 = events.get(1);
        if (status.getFamily() == Response.Status.Family.REDIRECTION)
        {
            Assert.assertSame(Level.INFO, event2.getLevel());
            String expectedPattern = "Redirected \\Q" + descriptionInLog + "\\E to: \\Q" + e.getMessage() + "\\E \\(\\d+\\.\\d{9}s\\)";
            if (!event2.getMessage().matches(expectedPattern))
            {
                Assert.fail("Failed to match \"" + expectedPattern + "\": " + event2.getMessage());
            }
        }
        else
        {
            Assert.assertSame(Level.ERROR, event2.getLevel());
            String expectedPattern = "Error \\Q" + descriptionInLog + "\\E \\(\\d+\\.\\d{9}s\\)";
            if (exceptionMessageInLog != null)
            {
                expectedPattern += "\\Q: " + exceptionMessageInLog + "\\E";
            }
            if (!event2.getMessage().matches(expectedPattern))
            {
                Assert.fail("Failed to match \"" + expectedPattern + "\": " + event2.getMessage());
            }
        }
    }

    @Test
    public void testExecuteWithLogging_OtherException()
    {
        testExecuteWithLogging_OtherException(
                "getting workspace WS1 for project X",
                "getting workspace WS1 for project X",
                "Something went wrong",
                "Something went wrong");
        testExecuteWithLogging_OtherException(
                "Changing the following things:\n\t- one\n\t- two\n\t- three",
                "Changing the following things:__- one__- two__- three",
                "There is a conflict in:\n- this\n\n- that\n\n\n- the other",
                "There is a conflict in: - this - that - the other");
        testExecuteWithLogging_OtherException(
                "getting projects",
                "getting projects",
                null,
                null);
    }

    private void testExecuteWithLogging_OtherException(String description, String descriptionInLog, String exceptionMessage, String exceptionMessageInLog)
    {
        TestResource resource = new TestResource();

        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> resource.executeWithLogging(description, () ->
        {
            throw new RuntimeException(exceptionMessage);
        }));
        Assert.assertSame(exceptionMessage, e.getMessage());

        List<SubstituteLoggingEvent> events = resource.getLoggingEvents();
        Assert.assertEquals(2, events.size());

        SubstituteLoggingEvent event1 = events.get(0);
        Assert.assertSame(Level.INFO, event1.getLevel());
        Assert.assertEquals("Starting {}", event1.getMessage());
        Assert.assertArrayEquals(new Object[]{descriptionInLog}, event1.getArgumentArray());

        SubstituteLoggingEvent event2 = events.get(1);
        Assert.assertSame(Level.ERROR, event2.getLevel());
        String expectedPattern = "Error \\Q" + descriptionInLog + "\\E \\(\\d+\\.\\d{9}s\\)";
        if (exceptionMessageInLog != null)
        {
            expectedPattern += "\\Q: " + exceptionMessageInLog + "\\E";
        }
        if (!event2.getMessage().matches(expectedPattern))
        {
            Assert.fail("Failed to match \"" + expectedPattern + "\": " + event2.getMessage());
        }
    }

    private static class TestResource extends BaseResource
    {
        private final SubstituteLoggerFactory loggerFactory = new SubstituteLoggerFactory();

        @Override
        protected Logger getLogger()
        {
            return this.loggerFactory.getLogger("test");
        }

        List<SubstituteLoggingEvent> getLoggingEvents()
        {
            List<SubstituteLoggingEvent> events = Lists.mutable.empty();
            this.loggerFactory.getEventQueue().drainTo(events);
            return events;
        }
    }
}
