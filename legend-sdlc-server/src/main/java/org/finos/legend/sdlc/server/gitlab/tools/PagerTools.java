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

import org.eclipse.collections.api.factory.Lists;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;

import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PagerTools
{
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long DEFAULT_INITIAL_RETRY_WAIT_INTERVAL_MILLIS = 1000L;
    private static final LongUnaryOperator DEFAULT_RETRY_WAIT_INTERVAL_UPDATER = w -> w + 1000L;

    private PagerTools()
    {
        // static utility class
    }

    public static <T> Stream<T> stream(Pager<T> pager)
    {
        return stream(pager, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_RETRY_WAIT_INTERVAL_MILLIS, DEFAULT_RETRY_WAIT_INTERVAL_UPDATER);
    }

    public static <T> Stream<T> stream(Pager<T> pager, int maxRetries, long initialRetryWaitInterval, LongUnaryOperator retryWaitIntervalIncrementer)
    {
        return StreamSupport.stream(new PagerSpliterator<>(pager, maxRetries, initialRetryWaitInterval, retryWaitIntervalIncrementer), false);
    }

    public static <T> Collector<T, ?, List<T>> listCollector(Pager<?> pager)
    {
        return listCollector(pager, -1);
    }

    public static <T> Collector<T, ?, List<T>> listCollector(Pager<?> pager, Integer limit)
    {
        return Collectors.toCollection(() -> newListSizedForPager(pager, limit));
    }

    public static <T> List<T> newListSizedForPager(Pager<?> pager)
    {
        return newListSizedForPager(pager, null);
    }

    public static <T> List<T> newListSizedForPager(Pager<?> pager, Integer limit)
    {
        boolean limited = (limit != null) && (limit > 0);
        int totalItems = pager.getTotalItems();
        int sizeEstimate = limited ? ((totalItems < 0) ? limit : Math.min(limit, totalItems)) : totalItems;
        return (sizeEstimate < 0) ? Lists.mutable.empty() : Lists.mutable.ofInitialCapacity(sizeEstimate);
    }

    /**
     * Check if the pager is empty without changing the state of the pager.
     *
     * @param pager pager
     * @return whether the pager is empty
     */
    public static boolean isEmpty(Pager<?> pager)
    {
        int totalItems = pager.getTotalItems();
        if (totalItems == 0)
        {
            return true;
        }
        if (totalItems > 0)
        {
            return false;
        }

        if (pager.getItemsPerPage() == 0)
        {
            return true;
        }

        int totalPages = pager.getTotalPages();
        if (totalPages == 0)
        {
            return true;
        }
        if (totalPages > 1)
        {
            return false;
        }

        int currentPage = pager.getCurrentPage();
        if (currentPage > 1)
        {
            return false;
        }

        List<?> page = pager.page(currentPage);
        return (page == null) || page.isEmpty();
    }

    public static <T> List<T> getNextWithRetries(Pager<T> pager)
    {
        return getNextWithRetries(pager, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_RETRY_WAIT_INTERVAL_MILLIS, DEFAULT_RETRY_WAIT_INTERVAL_UPDATER);
    }

    public static <T> List<T> getNextWithRetries(Pager<T> pager, int maxRetries, long initialWaitIntervalMillis, LongUnaryOperator waitIntervalUpdater)
    {
        if (maxRetries <= 0)
        {
            return pager.next();
        }

        try
        {
            return GitLabApiTools.callWithRetries(() ->
            {
                try
                {
                    return pager.next();
                }
                catch (RuntimeException e)
                {
                    Throwable cause = e.getCause();
                    if (cause instanceof GitLabApiException)
                    {
                        throw (GitLabApiException)cause;
                    }
                    throw e;
                }
            }, maxRetries, initialWaitIntervalMillis, waitIntervalUpdater);
        }
        catch (GitLabApiException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static class PagerSpliterator<T> implements Spliterator<T>
    {
        private final Pager<T> pager;
        private final int maxRetries;
        private final long initialRetryWaitIntervalMillis;
        private final LongUnaryOperator retryWaitIntervalUpdater;
        private Spliterator<T> current;

        private PagerSpliterator(Pager<T> pager, int maxRetries, long initialRetryWaitIntervalMillis, LongUnaryOperator retryWaitIntervalUpdater)
        {
            this.pager = pager;
            this.current = getNextSpliterator();
            this.maxRetries = maxRetries;
            this.initialRetryWaitIntervalMillis = initialRetryWaitIntervalMillis;
            this.retryWaitIntervalUpdater = retryWaitIntervalUpdater;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action)
        {
            while (this.current != null)
            {
                if (this.current.tryAdvance(action))
                {
                    return true;
                }
                this.current = getNextSpliterator();
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action)
        {
            while (this.current != null)
            {
                this.current.forEachRemaining(action);
                this.current = getNextSpliterator();
            }
        }

        @Override
        public Spliterator<T> trySplit()
        {
            return null;
        }

        @Override
        public long estimateSize()
        {
            // Check if the spliterator is finished
            if (this.current == null)
            {
                return 0L;
            }

            // Check if there are any more pages left in the pager
            if (!this.pager.hasNext())
            {
                return this.current.estimateSize();
            }

            // Estimate based on the reported total items from the pager
            long estimate = this.pager.getTotalItems();
            if (estimate < 0)
            {
                // Unreliable estimate, return unknown value
                return Long.MAX_VALUE;
            }

            int currentPage = this.pager.getCurrentPage();
            if (currentPage == 0)
            {
                // If the current page is 0, the pager hasn't started yet; so return the full estimate
                return estimate;
            }

            int itemsPerPage = this.pager.getItemsPerPage();
            if (itemsPerPage <= 0)
            {
                // Unreliable value for items per page, return unknown value
                return Long.MAX_VALUE;
            }

            // Adjust for pages already processed
            estimate -= (long)itemsPerPage * currentPage;

            // Adjust for remaining values from the current page
            estimate += this.current.estimateSize();

            // One last check if the estimate is reliable
            return (estimate < 0) ? Long.MAX_VALUE : estimate;
        }

        @Override
        public long getExactSizeIfKnown()
        {
            if (this.current == null)
            {
                return 0L;
            }

            if (!this.pager.hasNext())
            {
                return this.current.getExactSizeIfKnown();
            }

            return -1L;
        }

        @Override
        public int characteristics()
        {
            return 0;
        }

        private Spliterator<T> getNextSpliterator()
        {
            while (this.pager.hasNext())
            {
                List<T> page = getNextWithRetries(this.pager, this.maxRetries, this.initialRetryWaitIntervalMillis, this.retryWaitIntervalUpdater);
                if ((page != null) && !page.isEmpty())
                {
                    return page.spliterator();
                }
            }
            return null;
        }
    }
}
