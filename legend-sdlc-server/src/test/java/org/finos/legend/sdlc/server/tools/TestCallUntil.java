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

public class TestCallUntil
{
    @Test
    public void testSingleCallSuccess() throws Exception
    {
        CountingSupplier supplier = new CountingSupplier();
        CallUntil<String, TestException> callUntil = new CallUntil<>(supplier, i -> i.length() > 1);

        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(0, callUntil.getTryCount());
        Assert.assertEquals(0, supplier.getCallCount());
        Assert.assertEquals("0", supplier.getCurrentValue());

        Assert.assertTrue(callUntil.callUntil(5, 0L));
        Assert.assertTrue(callUntil.succeeded());
        Assert.assertEquals(1, callUntil.getTryCount());
        Assert.assertEquals(1, supplier.getCallCount());
        Assert.assertEquals("0_1", supplier.getCurrentValue());
    }

    @Test
    public void testMultipleFailuresBeforeSuccess() throws Exception
    {
        CountingSupplier supplier = new CountingSupplier();
        CallUntil<String, TestException> callUntil = new CallUntil<>(supplier, i -> i.length() > 11);

        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(0, callUntil.getTryCount());
        Assert.assertEquals(0, supplier.getCallCount());
        Assert.assertEquals("0", supplier.getCurrentValue());

        Assert.assertFalse(callUntil.callUntil(5, 0L));
        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(5, callUntil.getTryCount());
        Assert.assertEquals(5, supplier.getCallCount());
        Assert.assertEquals("0_1_2_3_4_5", supplier.getCurrentValue());

        Assert.assertTrue(callUntil.callUntil(5, 0L));
        Assert.assertTrue(callUntil.succeeded());
        Assert.assertEquals(6, callUntil.getTryCount());
        Assert.assertEquals("0_1_2_3_4_5_6", callUntil.getResult());
        Assert.assertEquals(6, supplier.getCallCount());
        Assert.assertEquals("0_1_2_3_4_5_6", supplier.getCurrentValue());
    }

    @Test
    public void testSingleCallException() throws Exception
    {
        String message = "No calls allowed!";
        CountingSupplier supplier = new ErroringCountingSupplier(0, message);
        CallUntil<String, TestException> callUntil = new CallUntil<>(supplier, i -> i.length() > 1);

        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(0, callUntil.getTryCount());
        Assert.assertEquals(0, supplier.getCallCount());
        Assert.assertEquals("0", supplier.getCurrentValue());

        for (int i = 1; i <= 3; i++)
        {
            try
            {
                callUntil.callUntil(5, 0L);
                Assert.fail("Expected exception");
            }
            catch (TestException e)
            {
                Assert.assertEquals(message, e.getMessage());
            }

            Assert.assertFalse(callUntil.succeeded());
            Assert.assertEquals(i, callUntil.getTryCount());
            Assert.assertEquals(i, supplier.getCallCount());
        }
    }

    @Test
    public void testMultipleFailuresBeforeException() throws Exception
    {
        String message = "Only 5 calls allowed!";
        CountingSupplier supplier = new ErroringCountingSupplier(5, message);
        CallUntil<String, TestException> callUntil = new CallUntil<>(supplier, i -> i.length() > 11);

        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(0, callUntil.getTryCount());
        Assert.assertEquals(0, supplier.getCallCount());
        Assert.assertEquals("0", supplier.getCurrentValue());

        Assert.assertFalse(callUntil.callUntil(5, 0L));
        Assert.assertFalse(callUntil.succeeded());
        Assert.assertEquals(5, callUntil.getTryCount());
        Assert.assertEquals(5, supplier.getCallCount());
        Assert.assertEquals("0_1_2_3_4_5", supplier.getCurrentValue());

        for (int i = 1; i <= 3; i++)
        {
            try
            {
                callUntil.callUntil(5, 0L);
                Assert.fail("Expected exception");
            }
            catch (TestException e)
            {
                Assert.assertEquals(message, e.getMessage());
            }

            Assert.assertFalse(callUntil.succeeded());
            Assert.assertEquals(i + 5, callUntil.getTryCount());
            Assert.assertEquals(i + 5, supplier.getCallCount());
        }
    }

    private static class TestException extends Exception
    {
        private TestException(String message)
        {
            super(message);
        }
    }

    private static class CountingSupplier implements ThrowingSupplier<String, TestException>
    {
        private final StringBuilder builder = new StringBuilder("0");
        private int i = 0;

        @Override
        public String get() throws TestException
        {
            return this.builder.append('_').append(++this.i).toString();
        }

        String getCurrentValue()
        {
            return this.builder.toString();
        }

        int getCallCount()
        {
            return this.i;
        }
    }

    private static class ErroringCountingSupplier extends CountingSupplier
    {
        private final int maxCalls;
        private final String errorMessage;

        private ErroringCountingSupplier(int maxCalls, String errorMessage)
        {
            this.maxCalls = maxCalls;
            this.errorMessage = errorMessage;
        }

        @Override
        public String get() throws TestException
        {
            String result = super.get();
            if (getCallCount() > this.maxCalls)
            {
                throw new TestException(this.errorMessage);
            }
            return result;
        }
    }
}
