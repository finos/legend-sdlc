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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class IOTools
{
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    public static byte[] readAllBytes(InputStream stream) throws IOException
    {
        return readAllBytes(stream, -1);
    }

    public static byte[] readAllBytes(InputStream stream, int estimatedSize) throws IOException
    {
        ExtendedByteArrayOutputStream bytes = readAll(stream, estimatedSize);
        return (bytes == null) ? EMPTY_BYTE_ARRAY : bytes.getBytesUnsafe();
    }

    public static byte[] readAllBytes(URL url) throws IOException
    {
        // special case for file URLs
        if ("file".equalsIgnoreCase(url.getProtocol()))
        {
            try
            {
                return Files.readAllBytes(Paths.get(url.toURI()));
            }
            catch (Exception ignore)
            {
                // ignore exceptions here and fall back to the general case
            }
        }
        // general case
        try (InputStream stream = url.openStream())
        {
            return readAllBytes(stream);
        }
    }

    public static String readAllToString(InputStream stream) throws IOException
    {
        return readAllToString(stream, -1, null);
    }

    public static String readAllToString(InputStream stream, int estimatedSize) throws IOException
    {
        return readAllToString(stream, estimatedSize, null);
    }

    public static String readAllToString(InputStream stream, Charset charset) throws IOException
    {
        return readAllToString(stream, -1, charset);
    }

    public static String readAllToString(InputStream stream, int estimatedSize, Charset charset) throws IOException
    {
        ExtendedByteArrayOutputStream bytes = readAll(stream, estimatedSize);
        return (bytes == null) ? "" : bytes.toString((charset == null) ? StandardCharsets.UTF_8 : charset);
    }

    public static String readAllToString(URL url) throws IOException
    {
        return readAllToString(url, null);
    }

    public static String readAllToString(URL url, Charset charset) throws IOException
    {
        // special case for file URLs
        if ("file".equalsIgnoreCase(url.getProtocol()))
        {
            try
            {
                return readAllToString(Paths.get(url.toURI()), charset);
            }
            catch (Exception ignore)
            {
                // ignore exceptions here and fall back to the general case
            }
        }
        // general case
        try (InputStream stream = url.openStream())
        {
            return readAllToString(stream);
        }
    }

    public static String readAllToString(Path path) throws IOException
    {
        return readAllToString(path, null);
    }

    public static String readAllToString(Path path, Charset charset) throws IOException
    {
        try (SeekableByteChannel channel = Files.newByteChannel(path);
             InputStream stream = Channels.newInputStream(channel))
        {
            long size = channel.size();
            int estimatedSize = ((size >= 0L) && (size < Integer.MAX_VALUE)) ? (int) size : -1;
            return readAllToString(stream, estimatedSize, charset);
        }
    }

    public static <T, U extends Spliterator<T> & AutoCloseable> Stream<T> streamCloseableSpliterator(U closeableSpliterator, boolean parallel)
    {
        return streamCloseableSpliterator(closeableSpliterator, parallel, null);
    }

    public static <T, U extends Spliterator<T> & AutoCloseable> Stream<T> streamCloseableSpliterator(U closeableSpliterator, boolean parallel, Consumer<? super Exception> closeExceptionHandler)
    {
        return closeCloseableOnStreamClose(StreamSupport.stream(closeableSpliterator, parallel), closeableSpliterator, closeExceptionHandler);
    }

    public static <T> Stream<T> closeCloseableOnStreamClose(Stream<T> stream, AutoCloseable closeable)
    {
        return closeCloseableOnStreamClose(stream, closeable, null);
    }

    public static <T> Stream<T> closeCloseableOnStreamClose(Stream<T> stream, AutoCloseable closeable, Consumer<? super Exception> exceptionHandler)
    {
        return stream.onClose(new CloseAutoCloseable(closeable, exceptionHandler));
    }

    private static ExtendedByteArrayOutputStream readAll(InputStream stream, int estimatedSize) throws IOException
    {
        ExtendedByteArrayOutputStream outStream;
        if (estimatedSize <= 0)
        {
            int b = stream.read();
            if (b == -1)
            {
                // input stream is empty - don't bother creating an output stream to hold bytes
                return null;
            }
            outStream = new ExtendedByteArrayOutputStream();
            outStream.write(b);
        }
        else
        {
            outStream = new ExtendedByteArrayOutputStream(estimatedSize);
        }
        outStream.readAllFrom(stream);
        return outStream;
    }

    private static class ExtendedByteArrayOutputStream extends ByteArrayOutputStream
    {
        private ExtendedByteArrayOutputStream()
        {
            super();
        }

        private ExtendedByteArrayOutputStream(int size)
        {
            super(size);
        }

        private synchronized void readAllFrom(InputStream stream) throws IOException
        {
            while (true)
            {
                // check capacity
                int capacity = this.buf.length - this.count;
                while (capacity <= 0)
                {
                    // no more capacity; check if there are more bytes in the stream
                    int b = stream.read();
                    if (b == -1)
                    {
                        // done
                        return;
                    }

                    // there are more bytes; increase capacity
                    write(b);
                    capacity = this.buf.length - this.count;
                }

                // read up to capacity from the stream
                int n = stream.read(this.buf, this.count, capacity);
                if (n == -1)
                {
                    // done
                    return;
                }
                this.count += n;
            }
        }

        public synchronized String toString(Charset charset)
        {
            return new String(this.buf, 0, this.count, charset);
        }

        private synchronized byte[] getBytesUnsafe()
        {
            return (this.count == this.buf.length) ? this.buf : toByteArray();
        }
    }

    private static class CloseAutoCloseable implements Runnable
    {
        private final AutoCloseable closeable;
        private final Consumer<? super Exception> exceptionHandler;

        private CloseAutoCloseable(AutoCloseable closeable, Consumer<? super Exception> exceptionHandler)
        {
            this.closeable = closeable;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void run()
        {
            try
            {
                this.closeable.close();
            }
            catch (Exception e)
            {
                if (this.exceptionHandler == null)
                {
                    throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
                }
                this.exceptionHandler.accept(e);
            }
        }
    }
}
