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

import org.eclipse.collections.api.factory.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestIOTools
{
    @Test
    public void testReadAllBytes() throws IOException
    {
        Random random = new Random();
        for (int i = 0; i < 1_048_577; i = Math.max(1, i * 2))
        {
            byte[] bytes = new byte[i];
            if (i > 0)
            {
                random.nextBytes(bytes);
            }
            Assert.assertArrayEquals("length: " + i, bytes, IOTools.readAllBytes(new ByteArrayInputStream(bytes), -1));
            Assert.assertArrayEquals("length: " + i, bytes, IOTools.readAllBytes(new ByteArrayInputStream(bytes), i / 2));
            Assert.assertArrayEquals("length: " + i, bytes, IOTools.readAllBytes(new ByteArrayInputStream(bytes), i));
            Assert.assertArrayEquals("length: " + i, bytes, IOTools.readAllBytes(new ByteArrayInputStream(bytes), i * 2));
        }
    }

    @Test
    public void testReadAllToString() throws IOException
    {
        Random random = new Random();
        for (int i = 0; i < 65_537; i = Math.max(1, i * 2))
        {
            byte[] bytes = new byte[i];
            if (i > 0)
            {
                random.nextBytes(bytes);
            }
            Assert.assertEquals("length: " + i, new String(bytes, StandardCharsets.UTF_8), IOTools.readAllToString(new ByteArrayInputStream(bytes)));
            Assert.assertEquals("length: " + i, new String(bytes, StandardCharsets.UTF_8), IOTools.readAllToString(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            Assert.assertEquals("length: " + i, new String(bytes, StandardCharsets.ISO_8859_1), IOTools.readAllToString(new ByteArrayInputStream(bytes), StandardCharsets.ISO_8859_1));
            Assert.assertEquals("length: " + i, new String(bytes, StandardCharsets.US_ASCII), IOTools.readAllToString(new ByteArrayInputStream(bytes), StandardCharsets.US_ASCII));
            Assert.assertEquals("length: " + i, new String(bytes, StandardCharsets.UTF_16), IOTools.readAllToString(new ByteArrayInputStream(bytes), StandardCharsets.UTF_16));
        }
    }

    @Test
    public void testStreamCloseableSpliterator()
    {
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        TestCloseableSpliterator<String> spliterator = new TestCloseableSpliterator<>(list, null);
        Stream<String> stream = IOTools.streamCloseableSpliterator(spliterator, false);
        Assert.assertFalse(spliterator.isClosed);
        List<String> result = stream.filter(s -> !spliterator.isClosed).collect(Collectors.toList());
        Assert.assertFalse(spliterator.isClosed);
        stream.close();
        Assert.assertTrue(spliterator.isClosed);
        Assert.assertEquals(list, result);
    }

    @Test
    public void testStreamCloseableSpliteratorWithException()
    {
        String expectedMessage = "hark, hark, the dogs do bark";
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        TestCloseableSpliterator<String> spliterator = new TestCloseableSpliterator<>(list, () ->
        {
            throw new Exception(expectedMessage);
        });
        Stream<String> stream = IOTools.streamCloseableSpliterator(spliterator, false);
        Assert.assertFalse(spliterator.isClosed);
        List<String> result = stream.filter(s -> !spliterator.isClosed).collect(Collectors.toList());
        Assert.assertFalse(spliterator.isClosed);
        try
        {
            stream.close();
            Assert.fail("Expected exception");
        }
        catch (RuntimeException e)
        {
            Throwable cause = e.getCause();
            Assert.assertSame(Exception.class, cause.getClass());
            Assert.assertEquals(expectedMessage, cause.getMessage());
        }
        Assert.assertEquals(list, result);
    }

    @Test
    public void testStreamCloseableSpliteratorWithRuntimeException()
    {
        String expectedMessage = "hark, hark, the dogs do bark";
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        TestCloseableSpliterator<String> spliterator = new TestCloseableSpliterator<>(list, () ->
        {
            throw new RuntimeException(expectedMessage);
        });
        Stream<String> stream = IOTools.streamCloseableSpliterator(spliterator, false);
        Assert.assertFalse(spliterator.isClosed);
        List<String> result = stream.filter(s -> !spliterator.isClosed).collect(Collectors.toList());
        Assert.assertFalse(spliterator.isClosed);
        try
        {
            stream.close();
            Assert.fail("Expected exception");
        }
        catch (RuntimeException e)
        {
            Assert.assertSame(RuntimeException.class, e.getClass());
            Assert.assertEquals(expectedMessage, e.getMessage());
            Assert.assertNull(e.getCause());
        }
        Assert.assertEquals(list, result);
    }

    @Test
    public void testStreamCloseableSpliteratorWithExceptionHandling()
    {
        String expectedMessage = "hark, hark, the dogs do bark";
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        TestCloseableSpliterator<String> spliterator = new TestCloseableSpliterator<>(list, () ->
        {
            throw new Exception(expectedMessage);
        });
        List<Exception> exceptions = Lists.mutable.empty();
        Stream<String> stream = IOTools.streamCloseableSpliterator(spliterator, false, exceptions::add);
        Assert.assertFalse(spliterator.isClosed);
        List<String> result = stream.filter(s -> !spliterator.isClosed).collect(Collectors.toList());
        Assert.assertFalse(spliterator.isClosed);
        stream.close();
        Assert.assertEquals(1, exceptions.size());
        Assert.assertEquals(expectedMessage, exceptions.get(0).getMessage());
        Assert.assertEquals(list, result);
    }

    @Test
    public void testCloseCloseableOnStreamClose()
    {
        TestCloseable closeable = new TestCloseable();
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        Stream<String> stream = list.stream();
        IOTools.closeCloseableOnStreamClose(stream, closeable);
        Assert.assertFalse(closeable.isClosed);
        List<String> result = stream.filter(s -> !closeable.isClosed).collect(Collectors.toList());
        Assert.assertFalse(closeable.isClosed);
        stream.close();
        Assert.assertTrue(closeable.isClosed);
        Assert.assertEquals(list, result);
    }

    @Test
    public void testCloseCloseableOnStreamCloseWithException()
    {
        String expectedMessage = "Not closed!";
        TestCloseable closeable = new TestCloseable(() ->
        {
            throw new Exception(expectedMessage);
        });
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        Stream<String> stream = list.stream();
        IOTools.closeCloseableOnStreamClose(stream, closeable);
        List<String> result = stream.collect(Collectors.toList());
        try
        {
            stream.close();
            Assert.fail("Expected exception");
        }
        catch (RuntimeException e)
        {
            Throwable cause = e.getCause();
            Assert.assertSame(Exception.class, cause.getClass());
            Assert.assertEquals(expectedMessage, cause.getMessage());
        }
        Assert.assertEquals(list, result);
    }

    @Test
    public void testCloseCloseableOnStreamCloseWithRuntimeException()
    {
        String expectedMessage = "Not closed!";
        TestCloseable closeable = new TestCloseable(() ->
        {
            throw new RuntimeException(expectedMessage);
        });
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        Stream<String> stream = list.stream();
        IOTools.closeCloseableOnStreamClose(stream, closeable);
        List<String> result = stream.collect(Collectors.toList());
        try
        {
            stream.close();
            Assert.fail("Expected exception");
        }
        catch (RuntimeException e)
        {
            Assert.assertSame(RuntimeException.class, e.getClass());
            Assert.assertEquals(expectedMessage, e.getMessage());
            Assert.assertNull(e.getCause());
        }
        Assert.assertEquals(list, result);
    }

    @Test
    public void testCloseCloseableOnStreamCloseWithExceptionHandling()
    {
        String expectedMessage = "Not closed!";
        TestCloseable closeable = new TestCloseable(() ->
        {
            throw new Exception(expectedMessage);
        });
        List<String> list = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "G-MAN", "G-MAN", "HOOVER"));
        Stream<String> stream = list.stream();
        List<Exception> exceptions = Lists.mutable.empty();
        IOTools.closeCloseableOnStreamClose(stream, closeable, exceptions::add);
        List<String> result = stream.collect(Collectors.toList());
        stream.close();
        Assert.assertEquals(1, exceptions.size());
        Assert.assertEquals(expectedMessage, exceptions.get(0).getMessage());
        Assert.assertEquals(list, result);
    }

    private static class TestCloseable implements AutoCloseable
    {
        private final Callable<?> closer;
        boolean isClosed = false;

        protected TestCloseable(Callable<?> closer)
        {
            this.closer = closer;
        }

        protected TestCloseable()
        {
            this(null);
        }

        @Override
        public void close() throws Exception
        {
            if (this.closer != null)
            {
                this.closer.call();
            }
            this.isClosed = true;
        }
    }

    private static class TestCloseableSpliterator<T> extends TestCloseable implements Spliterator<T>
    {
        private final Spliterator<T> delegate;

        private TestCloseableSpliterator(Spliterator<T> delegate, Callable<?> closer)
        {
            super(closer);
            this.delegate = delegate;
        }

        private TestCloseableSpliterator(Iterable<T> iterable, Callable<?> closer)
        {
            this(iterable.spliterator(), closer);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action)
        {
            return this.delegate.tryAdvance(action);
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action)
        {
            this.delegate.forEachRemaining(action);
        }

        @Override
        public Spliterator<T> trySplit()
        {
            return this.delegate.trySplit();
        }

        @Override
        public long estimateSize()
        {
            return this.delegate.estimateSize();
        }

        @Override
        public long getExactSizeIfKnown()
        {
            return this.delegate.getExactSizeIfKnown();
        }

        @Override
        public int characteristics()
        {
            return this.delegate.characteristics();
        }

        @Override
        public boolean hasCharacteristics(int characteristics)
        {
            return this.delegate.hasCharacteristics(characteristics);
        }

        @Override
        public Comparator<? super T> getComparator()
        {
            return this.delegate.getComparator();
        }
    }
}
