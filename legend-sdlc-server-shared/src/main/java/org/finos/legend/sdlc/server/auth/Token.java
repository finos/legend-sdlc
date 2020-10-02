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

package org.finos.legend.sdlc.server.auth;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Token
{
    private static final int NULL_STRING_CODE = 0b00000000;
    private static final int EMPTY_STRING_CODE = 0b10000000;

    private static final int LOWER_HEX_STRING_CODE = 0b00000001;
    private static final int UPPER_HEX_STRING_CODE = 0b00000010;

    private static final int BYTE_LENGTH_CODE = 0b01000000;
    private static final int SHORT_LENGTH_CODE = 0b00100000;
    private static final int INT_LENGTH_CODE = 0b00010000;

    private static final byte BOOLEAN_TRUE = 1;
    private static final byte BOOLEAN_FALSE = 0;

    private static final Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Decoder DECODER = Base64.getUrlDecoder();
    private static final Charset STRING_CHARSET = StandardCharsets.UTF_8;
    private static final Charset TOKEN_CHARSET = StandardCharsets.ISO_8859_1;

    public static class TokenBuilder
    {
        private final byte[] eightBytes = new byte[8];
        private final ByteBuffer eightByteBuffer = ByteBuffer.wrap(this.eightBytes);
        private final ByteArrayOutputStream stream;

        private TokenBuilder(int initialSize)
        {
            this.stream = new ByteArrayOutputStream(initialSize);
        }

        public synchronized String toTokenString()
        {
            return new String(ENCODER.encode(this.stream.toByteArray()), TOKEN_CHARSET);
        }

        public synchronized TokenBuilder putBoolean(boolean b)
        {
            return putByte(b ? BOOLEAN_TRUE : BOOLEAN_FALSE);
        }

        public synchronized TokenBuilder putByte(byte b)
        {
            this.stream.write(b);
            return this;
        }

        public synchronized TokenBuilder putShort(short s)
        {
            this.eightByteBuffer.putShort(0, s);
            return putBytes(this.eightBytes, 0, 2);
        }

        public synchronized TokenBuilder putInt(int i)
        {
            this.eightByteBuffer.putInt(0, i);
            return putBytes(this.eightBytes, 0, 4);
        }

        public synchronized TokenBuilder putLong(long l)
        {
            this.eightByteBuffer.putLong(0, l);
            return putBytes(this.eightBytes, 0, 8);
        }

        public synchronized TokenBuilder putString(String string)
        {
            if (string == null)
            {
                putByte((byte) NULL_STRING_CODE);
                return this;
            }

            if (string.isEmpty())
            {
                putByte((byte) EMPTY_STRING_CODE);
                return this;
            }

            int code = 0;

            // Get the string bytes
            byte[] bytes;
            int stringTypes = getStringTypes(string);
            if ((stringTypes & LOWER_HEX_STRING_CODE) == LOWER_HEX_STRING_CODE)
            {
                try
                {
                    bytes = Hex.decodeHex(string.toCharArray());
                    code |= LOWER_HEX_STRING_CODE;
                }
                catch (DecoderException ignore)
                {
                    // should never happen, but just in case ...
                    bytes = string.getBytes(STRING_CHARSET);
                    code &= ~LOWER_HEX_STRING_CODE;
                }
            }
            else if ((stringTypes & UPPER_HEX_STRING_CODE) == UPPER_HEX_STRING_CODE)
            {
                try
                {
                    bytes = Hex.decodeHex(string.toCharArray());
                    code |= UPPER_HEX_STRING_CODE;
                }
                catch (DecoderException ignore)
                {
                    // should never happen, but just in case ...
                    bytes = string.getBytes(STRING_CHARSET);
                    code &= ~UPPER_HEX_STRING_CODE;
                }
            }
            else
            {
                bytes = string.getBytes(STRING_CHARSET);
            }

            // Check the length of the byte array
            int length = bytes.length;
            if ((length & 0xffffff00) == 0)
            {
                code |= BYTE_LENGTH_CODE;
            }
            else if ((length & 0xffff0000) == 0)
            {
                code |= SHORT_LENGTH_CODE;
            }
            else
            {
                code |= INT_LENGTH_CODE;
            }

            // Write code
            putByte((byte) code);

            // Write length
            if ((code & BYTE_LENGTH_CODE) != 0)
            {
                putByte((byte) length);
            }
            else if ((code & SHORT_LENGTH_CODE) != 0)
            {
                putShort((short) length);
            }
            else
            {
                putInt(length);
            }

            // Write bytes
            return putBytes(bytes, 0, length);
        }

        private synchronized TokenBuilder putBytes(byte[] bytes, int offset, int length)
        {
            this.stream.write(bytes, offset, length);
            return this;
        }

        @Override
        public String toString()
        {
            return "<TokenBuilder token=" + toTokenString() + ">";
        }
    }

    public static class TokenReader
    {
        private final ByteBuffer buffer;

        private TokenReader(ByteBuffer buffer)
        {
            this.buffer = buffer;
        }

        public synchronized boolean hasRemaining()
        {
            return this.buffer.hasRemaining();
        }

        public synchronized boolean getBoolean()
        {
            byte b = getByte();
            switch (b)
            {
                case BOOLEAN_TRUE:
                {
                    return true;
                }
                case BOOLEAN_FALSE:
                {
                    return false;
                }
                default:
                {
                    throw new RuntimeException("Expected " + BOOLEAN_FALSE + " or " + BOOLEAN_TRUE + ", got: " + b);
                }
            }
        }

        public synchronized byte getByte()
        {
            return this.buffer.get();
        }

        public synchronized short getShort()
        {
            return this.buffer.getShort();
        }

        public synchronized int getInt()
        {
            return this.buffer.getInt();
        }

        public synchronized long getLong()
        {
            return this.buffer.getLong();
        }

        public synchronized String getString()
        {
            int code = this.buffer.get() & 0xff;
            if (code == NULL_STRING_CODE)
            {
                return null;
            }
            if (code == EMPTY_STRING_CODE)
            {
                return "";
            }

            int length;
            if ((code & BYTE_LENGTH_CODE) != 0)
            {
                length = getByte() & 0xff;
            }
            else if ((code & SHORT_LENGTH_CODE) != 0)
            {
                length = getShort() & 0xffff;
            }
            else
            {
                length = getInt();
            }

            if ((code & LOWER_HEX_STRING_CODE) != 0)
            {
                byte[] bytes = new byte[length];
                this.buffer.get(bytes);
                return new String(Hex.encodeHex(bytes, true));
            }
            if ((code & UPPER_HEX_STRING_CODE) != 0)
            {
                byte[] bytes = new byte[length];
                this.buffer.get(bytes);
                return new String(Hex.encodeHex(bytes, false));
            }

            int offset = this.buffer.position();
            String string = new String(this.buffer.array(), offset, length, STRING_CHARSET);
            this.buffer.position(offset + length);
            return string;
        }
    }

    public static TokenBuilder newBuilder()
    {
        return newBuilder(128);
    }

    public static TokenBuilder newBuilder(int initialSize)
    {
        return new TokenBuilder(initialSize);
    }

    public static TokenReader newReader(String token)
    {
        byte[] decodedBytes = DECODER.decode(token.getBytes(TOKEN_CHARSET));
        return new TokenReader(ByteBuffer.wrap(decodedBytes));
    }

    private static int getStringTypes(String string)
    {
        int length = string.length();
        if ((length & 0x01) != 0)
        {
            return 0;
        }

        int code = LOWER_HEX_STRING_CODE | UPPER_HEX_STRING_CODE;
        for (int i = 0; (code != 0) && (i < length); i++)
        {
            char c = string.charAt(i);
            if (((code & LOWER_HEX_STRING_CODE) == LOWER_HEX_STRING_CODE) && !isLowerHexChar(c))
            {
                code &= ~LOWER_HEX_STRING_CODE;
            }
            if (((code & UPPER_HEX_STRING_CODE) == UPPER_HEX_STRING_CODE) && !isUpperHexChar(c))
            {
                code &= ~UPPER_HEX_STRING_CODE;
            }
        }
        return code;
    }

    private static boolean isLowerHexChar(char c)
    {
        return (('0' <= c) && (c <= '9')) || (('a' <= c) && (c <= 'f'));
    }

    private static boolean isUpperHexChar(char c)
    {
        return (('0' <= c) && (c <= '9')) || (('A' <= c) && (c <= 'F'));
    }
}
