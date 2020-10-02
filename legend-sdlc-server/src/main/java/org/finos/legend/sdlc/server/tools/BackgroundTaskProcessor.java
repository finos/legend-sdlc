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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class BackgroundTaskProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskProcessor.class);

    private static final long DEFAULT_SLEEP_MILLIS = 100L;

    private final AtomicInteger taskCounter = new AtomicInteger(0);
    private final ThreadPoolExecutor executor;

    public BackgroundTaskProcessor(int threadPoolSize)
    {
        int realThreadPoolSize = Math.max(1, threadPoolSize);
        this.executor = new ThreadPoolExecutor(realThreadPoolSize, realThreadPoolSize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    /**
     * Submit a task for background execution.
     *
     * @param task task
     */
    public void submitTask(Task task)
    {
        submitTask(task, null);
    }

    /**
     * Submit a task for background execution.
     *
     * @param task        task
     * @param description task description for logging and error messages
     */
    public void submitTask(Task task, String description)
    {
        submit(new SimpleTaskWrapper(task, description));
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * If a task throws an exception, then it is not resubmitted.
     *
     * @param task retryable task
     */
    public void submitRetryableTask(RetryableTask task)
    {
        submitRetryableTask(task, null, 0L, null);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * <p>
     * If a task throws an exception, then the exception is tested with the predicate
     * {@code isExceptionRetryable} to see if it is retryable. If it is, the task is
     * resubmitted. If {@code isExceptionRetryable} is null, then no exceptions are
     * deemed retryable.
     *
     * @param task                 retryable task
     * @param isExceptionRetryable predicate to test which exceptions are retryable (if null, no exceptions are retryable)
     */
    public void submitRetryableTask(RetryableTask task, Predicate<? super Exception> isExceptionRetryable)
    {
        submitRetryableTask(task, isExceptionRetryable, 0L, null);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * If a task throws an exception, then it is not resubmitted.
     * <p>
     * When a task is resubmitted, it will wait at least {@code minWaitBetweenRetriesMillis}
     * before the task is actually retried.
     *
     * @param task                        retryable task
     * @param minWaitBetweenRetriesMillis minimum time to wait between retries in milliseconds
     */
    public void submitRetryableTask(RetryableTask task, long minWaitBetweenRetriesMillis)
    {
        submitRetryableTask(task, null, minWaitBetweenRetriesMillis, null);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * If a task throws an exception, then it is not resubmitted.
     *
     * @param task        retryable task
     * @param description task description for logging and error messages
     */
    public void submitRetryableTask(RetryableTask task, String description)
    {
        submitRetryableTask(task, null, 0L, description);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * <p>
     * When a task is resubmitted, it will wait at least {@code minWaitBetweenRetriesMillis}
     * before the task is actually retried.
     * <p>
     * If a task throws an exception, then the exception is tested with the predicate
     * {@code isExceptionRetryable} to see if it is retryable. If it is, the task is
     * resubmitted. If {@code isExceptionRetryable} is null, then no exceptions are
     * deemed retryable.
     *
     * @param task                        retryable task
     * @param isExceptionRetryable        predicate to test which exceptions are retryable (if null, no exceptions are retryable)
     * @param minWaitBetweenRetriesMillis minimum time to wait between retries in milliseconds
     */
    public void submitRetryableTask(RetryableTask task, Predicate<? super Exception> isExceptionRetryable, long minWaitBetweenRetriesMillis)
    {
        submitRetryableTask(task, isExceptionRetryable, minWaitBetweenRetriesMillis, null);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * <p>
     * If a task throws an exception, then the exception is tested with the predicate
     * {@code isExceptionRetryable} to see if it is retryable. If it is, the task is
     * resubmitted. If {@code isExceptionRetryable} is null, then no exceptions are
     * deemed retryable.
     *
     * @param task                 retryable task
     * @param isExceptionRetryable predicate to test which exceptions are retryable (if null, no exceptions are retryable)
     * @param description          task description for logging and error messages
     */
    public void submitRetryableTask(RetryableTask task, Predicate<? super Exception> isExceptionRetryable, String description)
    {
        submitRetryableTask(task, isExceptionRetryable, 0L, description);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * If a task throws an exception, then it is not resubmitted.
     * <p>
     * When a task is resubmitted, it will wait at least {@code minWaitBetweenRetriesMillis}
     * before the task is actually retried.
     *
     * @param task                        retryable task
     * @param minWaitBetweenRetriesMillis minimum time to wait between retries in milliseconds
     * @param description                 task description for logging and error messages
     */
    public void submitRetryableTask(RetryableTask task, long minWaitBetweenRetriesMillis, String description)
    {
        submitRetryableTask(task, null, minWaitBetweenRetriesMillis, description);
    }

    /**
     * Submit a retryable task for background execution.
     * <p>
     * When the task is run, it returns a boolean indicating whether it completed.
     * If it did not complete (i.e., if it returns false), then the task is resubmitted.
     * <p>
     * When a task is resubmitted, it will wait at least {@code minWaitBetweenRetriesMillis}
     * before the task is actually retried.
     * <p>
     * If a task throws an exception, then the exception is tested with the predicate
     * {@code isExceptionRetryable} to see if it is retryable. If it is, the task is
     * resubmitted. If {@code isExceptionRetryable} is null, then no exceptions are
     * deemed retryable.
     *
     * @param task                        retryable task
     * @param isExceptionRetryable        predicate to test which exceptions are retryable (if null, no exceptions are retryable)
     * @param minWaitBetweenRetriesMillis minimum time to wait between retries in milliseconds
     * @param description                 task description for logger and error messages
     */
    public void submitRetryableTask(RetryableTask task, Predicate<? super Exception> isExceptionRetryable, long minWaitBetweenRetriesMillis, String description)
    {
        submit(new RetryableTaskWrapper(task, isExceptionRetryable, minWaitBetweenRetriesMillis, description));
    }

    /**
     * Shut down the background task processor.
     *
     * <p>After calling this method, no new tasks will be accepted.
     * Previously submitted tasks are executed, though retryable
     * tasks will not longer be resubmitted if they do not complete.
     * accepted.
     *
     * <p>This method has no additional effect once the task processor
     * is shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete. Use {@link #awaitTermination awaitTermination} for that.
     */
    public void shutdown()
    {
        if (!isShutdown())
        {
            LOGGER.info("Shutting down");
        }
        this.executor.shutdown();
    }

    /**
     * Check whether the task processor is shut down.
     *
     * @return whether the task processor is shut down
     */
    public boolean isShutdown()
    {
        return this.executor.isShutdown();
    }

    /**
     * Wait until the background task processor is shut down and
     * all tasks have completed.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return {@code true} if this task processor terminated or {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
    {
        LOGGER.info("Awaiting termination of existing tasks");
        boolean terminated;
        try
        {
            terminated = this.executor.awaitTermination(timeout, unit);
        }
        catch (Exception e)
        {
            LOGGER.warn("Error occurred while awaiting termination", e);
            throw e;
        }
        LOGGER.info(terminated ? "Termination complete" : "Termination not complete");
        return terminated;
    }

    private void submit(TaskWrapper taskWrapper)
    {
        LOGGER.debug("{} Submitting task", taskWrapper.logPrefix);
        try
        {
            this.executor.submit(taskWrapper);
        }
        catch (RejectedExecutionException e)
        {
            LOGGER.error(taskWrapper.logPrefix + " Task rejected", e);
            throw e;
        }
        catch (Exception e)
        {
            LOGGER.error(taskWrapper.logPrefix + " Error while submitting task", e);
            throw e;
        }
        LOGGER.debug("{} task submitted", taskWrapper.logPrefix);
    }

    private boolean isQueueEmpty()
    {
        return this.executor.getQueue().isEmpty();
    }

    public interface Task
    {
        /**
         * Run the task.
         *
         * @throws Exception if unable to complete the task
         */
        void run() throws Exception;
    }

    public interface RetryableTask
    {
        /**
         * Try to run the task.
         * <p>
         * If the task completed, the method should return true. If the
         * task did not complete and should be retried, the method should
         * return false.
         * <p>
         * An exception should be thrown if something went wrong while
         * trying to perform the task. Whether the task is retried after
         * an exception depends on whether the exception is deemed
         * retryable. (By default, no exception is retryable.)
         *
         * @return true if the task finished; otherwise, false
         * @throws Exception if something went wrong during the task
         */
        boolean run() throws Exception;
    }

    private abstract class TaskWrapper implements Callable<Void>
    {
        protected final int id;
        protected final String logPrefix;

        protected TaskWrapper(String description)
        {
            this.id = BackgroundTaskProcessor.this.taskCounter.incrementAndGet();
            this.logPrefix = "[task " + this.id + (((description == null) || description.isEmpty()) ? "" : (": " + description)) + "]";
        }

        @Override
        public Void call() throws Exception
        {
            runTask();
            return null;
        }

        @Override
        public String toString()
        {
            return "<" + getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this)) + " " + this.logPrefix + ">";
        }

        protected abstract void runTask() throws Exception;
    }

    private class SimpleTaskWrapper extends TaskWrapper
    {
        private final Task task;

        private SimpleTaskWrapper(Task task, String description)
        {
            super(description);
            this.task = task;
        }

        @Override
        protected void runTask() throws Exception
        {
            LOGGER.debug("{} Starting task", this.logPrefix);
            try
            {
                this.task.run();
            }
            catch (Exception e)
            {
                LOGGER.warn(this.logPrefix + " Error occurred during task", e);
                throw e;
            }
            LOGGER.debug("{} Finished task", this.logPrefix);
        }
    }

    private class RetryableTaskWrapper extends TaskWrapper
    {
        private final RetryableTask task;
        private final Predicate<? super Exception> isRetryableException;
        private final long minWaitBetweenRetriesMillis;
        private long lastTime;
        private int tryCount = 1;

        private RetryableTaskWrapper(RetryableTask task, Predicate<? super Exception> isRetryableException, long minWaitBetweenRetriesMillis, String description)
        {
            super(description);
            this.task = task;
            this.isRetryableException = isRetryableException;
            this.minWaitBetweenRetriesMillis = minWaitBetweenRetriesMillis;
        }

        @Override
        protected void runTask() throws Exception
        {
            long millisBeforeNextTry = getMillisBeforeNextTry();
            while (millisBeforeNextTry > 0)
            {
                try
                {
                    Thread.sleep(Math.min(millisBeforeNextTry, DEFAULT_SLEEP_MILLIS));
                }
                catch (InterruptedException e)
                {
                    resubmit();
                    throw e;
                }
                millisBeforeNextTry = getMillisBeforeNextTry();
                if ((millisBeforeNextTry > DEFAULT_SLEEP_MILLIS) && !isQueueEmpty())
                {
                    // Let other tasks have a chance: try to resubmit, but if we fail then let the task run
                    if (resubmit())
                    {
                        return;
                    }
                    millisBeforeNextTry = 0;
                }
            }

            LOGGER.debug("{} Starting task, attempt #{}", this.logPrefix, this.tryCount);
            boolean finished;
            try
            {
                finished = this.task.run();
            }
            catch (Exception e)
            {
                if (isExceptionRetryable(e))
                {
                    LOGGER.warn(this.logPrefix + " Retryable error occurred on attempt #" + this.tryCount + ", resubmitting", e);
                    this.lastTime = System.currentTimeMillis();
                    this.tryCount++;
                    resubmit();
                    return;
                }
                LOGGER.warn(this.logPrefix + " Non-retryable error occurred during task on attempt #" + this.tryCount, e);
                throw e;
            }
            if (finished)
            {
                LOGGER.debug("{} Finished task on attempt #{}", this.logPrefix, this.tryCount);
            }
            else
            {
                LOGGER.debug("{} Task unfinished on attempt #{}, resubmitting", this.logPrefix, this.tryCount);
                this.lastTime = System.currentTimeMillis();
                this.tryCount++;
                resubmit();
            }
        }

        private long getMillisBeforeNextTry()
        {
            if ((this.tryCount <= 1) || (this.minWaitBetweenRetriesMillis <= 0) || isShutdown())
            {
                return 0L;
            }

            long millisSinceLastTime = (System.currentTimeMillis() - this.lastTime);
            return this.minWaitBetweenRetriesMillis - millisSinceLastTime;
        }

        private boolean isExceptionRetryable(Exception exception)
        {
            if (this.isRetryableException != null)
            {
                try
                {
                    return this.isRetryableException.test(exception);
                }
                catch (Exception e)
                {
                    LOGGER.warn(this.logPrefix + " Error occurred while testing if exception is retryable on attempt #" + this.tryCount, e);
                }
            }
            return false;
        }

        private boolean resubmit()
        {
            if (isShutdown())
            {
                LOGGER.debug("{} Executor service is shut down, not resubmitting", this.logPrefix);
                return false;
            }

            try
            {
                BackgroundTaskProcessor.this.executor.submit(this);
                return true;
            }
            catch (RejectedExecutionException e)
            {
                LOGGER.warn(this.logPrefix + " Task rejected on retry submission", e);
            }
            catch (Exception e)
            {
                LOGGER.warn(this.logPrefix + " Error resubmitting task for retry", e);
            }
            return false;
        }
    }
}
