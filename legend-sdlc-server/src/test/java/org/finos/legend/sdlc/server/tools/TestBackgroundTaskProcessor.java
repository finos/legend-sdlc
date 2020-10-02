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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBackgroundTaskProcessor
{
    private static BackgroundTaskProcessor backgroundTaskProcessor;

    @BeforeClass
    public static void setUp()
    {
        backgroundTaskProcessor = new BackgroundTaskProcessor(1);
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        if (backgroundTaskProcessor != null)
        {
            backgroundTaskProcessor.shutdown();
            backgroundTaskProcessor.awaitTermination(30L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSimpleTask() throws Exception
    {
        AtomicInteger atomicInt = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        backgroundTaskProcessor.submitTask(() ->
        {
            atomicInt.compareAndSet(0, 1);
            latch.countDown();
            Assert.assertEquals(0L, latch.getCount());
        }, "test task");

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(1, atomicInt.get());
    }

    @Test
    public void testRetryableTask() throws Exception
    {
        AtomicInteger i = new AtomicInteger(0);
        int expected = 5;
        CountDownLatch latch = new CountDownLatch(expected);
        backgroundTaskProcessor.submitRetryableTask(() ->
        {
            boolean finished = i.incrementAndGet() >= expected;
            if (finished)
            {
                Assert.assertEquals(1L, latch.getCount());
            }
            latch.countDown();
            return finished;
        }, "test retryable task");

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(expected, i.get());
    }
}
