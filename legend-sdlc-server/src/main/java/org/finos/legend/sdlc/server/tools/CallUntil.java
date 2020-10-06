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

import java.util.function.Predicate;

public class CallUntil<T, E extends Exception>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CallUntil.class);

    private final ThrowingSupplier<? extends T, ? extends E> supplier;
    private final Predicate<? super T> predicate;
    private int tryCount = 0;
    private boolean success = false;
    private T result = null;

    public CallUntil(ThrowingSupplier<? extends T, ? extends E> supplier, Predicate<? super T> predicate)
    {
        this.supplier = supplier;
        this.predicate = predicate;
    }

    public boolean callUntil(int maxTries, long waitIntervalMillis) throws E
    {
        for (int i = 0; !this.success && (i < maxTries); i++)
        {
            this.tryCount++;

            // Possibly wait
            if ((i > 0) && (waitIntervalMillis > 0L))
            {
                try
                {
                    Thread.sleep(waitIntervalMillis);
                }
                catch (InterruptedException e)
                {
                    LOGGER.warn("Interrupted while waiting", e);
                    Thread.currentThread().interrupt();
                }
            }

            // Try
            T value = this.supplier.get();
            if (this.predicate.test(value))
            {
                this.result = value;
                this.success = true;
            }
        }
        return this.success;
    }

    public int getTryCount()
    {
        return this.tryCount;
    }

    public boolean succeeded()
    {
        return this.success;
    }

    public T getResult()
    {
        return this.result;
    }

    public static <T, E extends Exception> CallUntil<T, E> callUntil(ThrowingSupplier<T, E> supplier, Predicate<? super T> predicate, int maxTries, long waitIntervalMillis) throws E
    {
        CallUntil<T, E> callUntil = new CallUntil<>(supplier, predicate);
        callUntil.callUntil(maxTries, waitIntervalMillis);
        return callUntil;
    }
}
