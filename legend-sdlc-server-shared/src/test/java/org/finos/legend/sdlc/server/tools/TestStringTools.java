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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.function.Function;

public class TestStringTools
{
    @Test
    public void testAppendThrowableMessageIfPresent_VacuousCases()
    {
        Assert.assertNull(StringTools.appendThrowableMessageIfPresent((String)null, null));
        Assert.assertNull(StringTools.appendThrowableMessageIfPresent((String)null, new RuntimeException()));
        Assert.assertNull(StringTools.appendThrowableMessageIfPresent((String)null, new Throwable()));
        Assert.assertNull(StringTools.appendThrowableMessageIfPresent((String)null, new UnknownHostException()));

        Assert.assertEquals("", StringTools.appendThrowableMessageIfPresent("", null));
        Assert.assertEquals("", StringTools.appendThrowableMessageIfPresent("", new RuntimeException()));
        Assert.assertEquals("", StringTools.appendThrowableMessageIfPresent("", new Throwable()));
        Assert.assertEquals("", StringTools.appendThrowableMessageIfPresent("", new UnknownHostException()));
    }

    @Test
    public void testAppendThrowableMessageIfPresent()
    {
        String message = "the quick brown fox jumped over the lazy dog";

        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent((String)null, new RuntimeException(message)));
        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent((String)null, new Throwable(message)));
        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent((String)null, new IOException(message)));

        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent("", new RuntimeException(message)));
        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent("", new Throwable(message)));
        Assert.assertEquals(message, StringTools.appendThrowableMessageIfPresent("", new IOException(message)));

        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new RuntimeException(message)));
        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new Throwable(message)));
        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new IOException(message)));

        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred: ", new RuntimeException(message), (String)null));
        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred: ", new Throwable(message), (String)null));
        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred: ", new IOException(message), (String)null));

        Assert.assertEquals("An error occurred; " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new RuntimeException(message), "; "));
        Assert.assertEquals("An error occurred; " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new Throwable(message), "; "));
        Assert.assertEquals("An error occurred; " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new IOException(message), "; "));
    }

    @Test
    public void testAppendThrowableMessageIfPresent_MessageFunc()
    {
        String message = "the quick brown fox jumped over the lazy dog";
        String otherMessage = "the quick lazy dog jumped over the brown fox";
        Function<Throwable, String> messageFunc = e -> (e instanceof RuntimeException) ? otherMessage : e.getMessage();

        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new Throwable(message), messageFunc));
        Assert.assertEquals("An error occurred: " + otherMessage, StringTools.appendThrowableMessageIfPresent("An error occurred", new RuntimeException(message), messageFunc));
        Assert.assertEquals("An error occurred: " + message, StringTools.appendThrowableMessageIfPresent("An error occurred", new IOException(message), messageFunc));
    }

    @Test
    public void testAppendThrowableMessageIfPresent_UnknownHostException()
    {
        String unknownHostPrefix = "unknown host - ";
        String hostName = "some.host.somewhere";
        Assert.assertEquals(unknownHostPrefix + hostName, StringTools.appendThrowableMessageIfPresent((String)null, new UnknownHostException(hostName)));
        Assert.assertEquals(unknownHostPrefix + hostName, StringTools.appendThrowableMessageIfPresent("", new UnknownHostException(hostName)));
        Assert.assertEquals("Access error: " + unknownHostPrefix + hostName, StringTools.appendThrowableMessageIfPresent("Access error", new UnknownHostException(hostName)));
        Assert.assertEquals("Access error: " + unknownHostPrefix + hostName, StringTools.appendThrowableMessageIfPresent("Access error: ", new UnknownHostException(hostName), (String)null));
        Assert.assertEquals("Access error; " + unknownHostPrefix + hostName, StringTools.appendThrowableMessageIfPresent("Access error", new UnknownHostException(hostName), "; "));
    }

    @Test
    public void testFormatDurationInNanos()
    {
        assertDurationFormatsTo("0.000000000", 0L);
        assertDurationFormatsTo("0.000000001", 1L);
        assertDurationFormatsTo("0.000000003", 3L);
        assertDurationFormatsTo("0.000000010", 10L);
        assertDurationFormatsTo("1.000000001", 1_000_000_001L);
        assertDurationFormatsTo("1,000.000000001", 1_000_000_000_001L);
        assertDurationFormatsTo("10,000.000000001", 10_000_000_000_001L);
        assertDurationFormatsTo("100,000.000000001", 100_000_000_000_001L);
        assertDurationFormatsTo("1,234,567,890.123456789", 1234567890123456789L);
        assertDurationFormatsTo("9,223,372,036.854775807", Long.MAX_VALUE);
    }

    @Test
    public void testFormatDurationInNanos_Negative()
    {
        assertDurationFormatsTo("0.000000000", -0L);
        assertDurationFormatsTo("-0.000000001", -1L);
        assertDurationFormatsTo("-0.000000003", -3L);
        assertDurationFormatsTo("-0.000000010", -10L);
        assertDurationFormatsTo("-1.000000001", -1_000_000_001L);
        assertDurationFormatsTo("-1,000.000000001", -1_000_000_000_001L);
        assertDurationFormatsTo("-10,000.000000001", -10_000_000_000_001L);
        assertDurationFormatsTo("-100,000.000000001", -100_000_000_000_001L);
        assertDurationFormatsTo("-1,234,567,890.123456789", -1234567890123456789L);
        assertDurationFormatsTo("-9,223,372,036.854775808", Long.MIN_VALUE);
    }

    private void assertDurationFormatsTo(String expected, long durationInNanos)
    {
        Assert.assertEquals(expected, StringTools.formatDurationInNanos(new StringBuilder(20), durationInNanos).toString());
        Assert.assertEquals(expected, StringTools.formatDurationInNanos(durationInNanos));
    }
}
