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

package org.finos.legend.sdlc.server.gitlab.tools;

import org.finos.legend.sdlc.server.tools.ThrowingSupplier;
import org.gitlab4j.api.GitLabApiException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

public class TestGitLabApiTools
{
    private static final int MAX_MAX_RETRIES = 50;

    private static final long MAX_UNDER_DURATION_NANOS = 1_000_000;
    private static final long MAX_OVER_DURATION_NANOS = 100_000_000;

    @Test
    public void testIsNotFoundGitLabApiException()
    {
        Assert.assertTrue(GitLabApiTools.isNotFoundGitLabApiException(new GitLabApiException("message doesn't matter", 404)));
        IntStream.rangeClosed(100, 599).filter(status -> status != 404).forEach(status ->
                Assert.assertFalse(String.valueOf(status), GitLabApiTools.isNotFoundGitLabApiException(new GitLabApiException("404 File Not Found", status))));
    }

    @Test
    public void testIsRetryableGitLabApiException()
    {
        IntStream.rangeClosed(100, 599).forEach(status ->
        {
            if ((status == 408) || (status == 502) || (status == 503) || (status == 504))
            {
                Assert.assertTrue(String.valueOf(status), GitLabApiTools.isRetryableGitLabApiException(new GitLabApiException("some message", status)));
            }
            else
            {
                Assert.assertFalse(String.valueOf(status), GitLabApiTools.isRetryableGitLabApiException(new GitLabApiException("some other message", status)));
            }
        });
    }

    @Test
    public void testFindGitLabApiException()
    {
        GitLabApiException innerException = new GitLabApiException("");
        GitLabApiException expected = new GitLabApiException(innerException);
        Assert.assertSame(innerException, GitLabApiTools.findGitLabApiException(innerException));
        Assert.assertSame(expected, GitLabApiTools.findGitLabApiException(expected));
        Assert.assertSame(expected, GitLabApiTools.findGitLabApiException(new RuntimeException(expected)));
        Assert.assertSame(expected, GitLabApiTools.findGitLabApiException(new RuntimeException(new Exception(expected))));

        Assert.assertNull(GitLabApiTools.findGitLabApiException(null));
        Assert.assertNull(GitLabApiTools.findGitLabApiException(new RuntimeException()));
        Assert.assertNull(GitLabApiTools.findGitLabApiException(new Exception(new RuntimeException(new Exception()))));
    }

    @Test
    public void testCall_NoException() throws Exception
    {
        String expected = "the quick brown fox jumped over the lazy dog";
        GitLabApiCallWithCounter<String> call = new GitLabApiCallWithCounter<String>()
        {
            @Override
            protected String realCall()
            {
                return expected;
            }
        };

        for (int maxRetries = 0; maxRetries < MAX_MAX_RETRIES; maxRetries++)
        {
            call.resetCount();
            String actual = GitLabApiTools.callWithRetries(call, maxRetries, 1000, w -> w * 2);
            Assert.assertEquals(expected, actual);
            Assert.assertEquals(1, call.getCallCount());
        }
    }

    @Test
    public void testCall_NonRetryableException()
    {
        String expected = "the quick brown fox jumped over the lazy dog";
        GitLabApiCallWithCounter<String> call = new GitLabApiCallWithCounter<String>()
        {
            @Override
            protected String realCall() throws GitLabApiException
            {
                throw new GitLabApiException(expected, 400);
            }
        };

        try
        {
            call.get();
            Assert.fail("Expected exception");
        }
        catch (GitLabApiException e)
        {
            Assert.assertFalse(GitLabApiTools.isRetryableGitLabApiException(e));
        }

        for (int maxRetries = 0; maxRetries < MAX_MAX_RETRIES; maxRetries++)
        {
            call.resetCount();
            try
            {
                GitLabApiTools.callWithRetries(call, maxRetries, 1000, w -> w + 1000);
                Assert.fail("Expected exception");
            }
            catch (GitLabApiException e)
            {
                Assert.assertEquals(expected, e.getMessage());
                Assert.assertEquals(1, call.getCallCount());
            }
        }
    }

    @Test
    public void testCall_RetryableException()
    {
        String exceptionMessage = "GitLab is not responding";
        GitLabApiCallWithCounter<String> call = new GitLabApiCallWithCounter<String>()
        {
            @Override
            protected String realCall() throws GitLabApiException
            {
                throw new GitLabApiException(exceptionMessage, 503);
            }
        };

        try
        {
            call.get();
            Assert.fail("Expected exception");
        }
        catch (GitLabApiException e)
        {
            Assert.assertTrue(GitLabApiTools.isRetryableGitLabApiException(e));
        }

        long waitInterval = 15;

        // Warm up call to reduce transient failures
        try
        {
            GitLabApiTools.callWithRetries(call, 1, waitInterval);
        }
        catch (Exception ignore)
        {
            // ignore exception
        }

        for (int maxRetries = 0; maxRetries < 5; maxRetries++)
        {
            call.resetCount();
            Runtime.getRuntime().gc(); // GC to reduce transient failures
            long expectedWaitNanos = maxRetries * waitInterval * 1_000_000;
            long start = System.nanoTime();
            try
            {
                GitLabApiTools.callWithRetries(call, maxRetries, waitInterval);
                Assert.fail("Expected exception");
            }
            catch (GitLabApiException e)
            {
                long end = System.nanoTime();
                Assert.assertEquals(exceptionMessage, e.getMessage());
                Assert.assertEquals(maxRetries, e.getSuppressed().length);
                Assert.assertTrue(Arrays.stream(e.getSuppressed()).allMatch(s -> (s instanceof GitLabApiException) && GitLabApiTools.isRetryableGitLabApiException((GitLabApiException) s)));
                Assert.assertEquals(maxRetries + 1, call.getCallCount());
                assertDuration(maxRetries, expectedWaitNanos, end - start);
            }
        }
    }

    @Test
    public void testCall_RetryableException_SuccessBeforeMaxRetries() throws Exception
    {
        String expected = "the quick brown fox jumped over the lazy dog";
        String exceptionMessage = "GitLab is not responding";
        int[] failureCount = {1};
        GitLabApiCallWithCounter<String> call = new GitLabApiCallWithCounter<String>()
        {
            @Override
            protected String realCall() throws GitLabApiException
            {
                if (getCallCount() < failureCount[0])
                {
                    throw new GitLabApiException(exceptionMessage, 502);
                }
                return expected;
            }
        };

        try
        {
            call.get();
            Assert.fail("Expected exception");
        }
        catch (GitLabApiException e)
        {
            Assert.assertTrue(GitLabApiTools.isRetryableGitLabApiException(e));
        }

        long waitInterval = 15;
        // Warm up call to reduce transient failures
        try
        {
            call.resetCount();
            GitLabApiTools.callWithRetries(call, 1, waitInterval);
        }
        catch (Exception ignore)
        {
            // ignore exception
        }

        for (int maxRetries = 0; maxRetries < 5; maxRetries++)
        {
            failureCount[0] = maxRetries;
            call.resetCount();
            Runtime.getRuntime().gc(); // GC to reduce transient failures
            long expectedWaitNanos = maxRetries * waitInterval * 1_000_000;
            long start = System.nanoTime();
            String actual = GitLabApiTools.callWithRetries(call, maxRetries, waitInterval, null);
            long end = System.nanoTime();
            Assert.assertEquals(Integer.toString(maxRetries), expected, actual);
            Assert.assertEquals(Integer.toString(maxRetries), maxRetries + 1, call.getCallCount());
            assertDuration(maxRetries, expectedWaitNanos, end - start);
        }
    }

    private void assertDuration(int maxRetries, long expected, long actual)
    {
        long difference = actual - expected;
        if ((difference < -MAX_UNDER_DURATION_NANOS) || (difference > MAX_OVER_DURATION_NANOS))
        {
            Assert.fail(String.format("maxRetries: %,d; expected duration: %,dns; duration: %,dns", maxRetries, expected, actual));
        }
    }

    private abstract static class GitLabApiCallWithCounter<T> implements ThrowingSupplier<T, GitLabApiException>
    {
        private int counter = 0;

        @Override
        public T get() throws GitLabApiException
        {
            try
            {
                return realCall();
            }
            finally
            {
                this.counter++;
            }
        }

        protected abstract T realCall() throws GitLabApiException;

        public void resetCount()
        {
            this.counter = 0;
        }

        public int getCallCount()
        {
            return this.counter;
        }
    }
}
