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

import org.finos.legend.sdlc.server.auth.Token.TokenBuilder;
import org.finos.legend.sdlc.server.auth.Token.TokenReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TestToken
{
    @Test
    public void testInts()
    {
        TokenBuilder builder = Token.newBuilder();
        builder.putInt(Integer.MIN_VALUE);
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++)
        {
            builder.putInt(i);
        }
        builder.putInt(Integer.MAX_VALUE);

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        Assert.assertEquals(Integer.MIN_VALUE, reader.getInt());
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++)
        {
            Assert.assertEquals(i, reader.getInt());
        }
        Assert.assertEquals(Integer.MAX_VALUE, reader.getInt());
        Assert.assertFalse(reader.hasRemaining());
    }

    @Test
    public void testShorts()
    {
        TokenBuilder builder = Token.newBuilder();
        for (short s = Short.MIN_VALUE; s < Short.MAX_VALUE; s++)
        {
            builder.putShort(s);
        }
        builder.putShort(Short.MAX_VALUE);

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        for (short s = Short.MIN_VALUE; s < Short.MAX_VALUE; s++)
        {
            Assert.assertEquals(s, reader.getShort());
        }
        Assert.assertEquals(Short.MAX_VALUE, reader.getShort());
        Assert.assertFalse(reader.hasRemaining());
    }

    @Test
    public void testBytes()
    {
        TokenBuilder builder = Token.newBuilder();
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++)
        {
            builder.putByte(b);
        }
        builder.putByte(Byte.MAX_VALUE);

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++)
        {
            Assert.assertEquals(b, reader.getByte());
        }
        Assert.assertEquals(Byte.MAX_VALUE, reader.getByte());
        Assert.assertFalse(reader.hasRemaining());
    }

    @Test
    public void testBooleans()
    {
        TokenBuilder builder = Token.newBuilder();
        builder.putBoolean(true);
        builder.putBoolean(false);

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        Assert.assertTrue(reader.getBoolean());
        Assert.assertFalse(reader.getBoolean());
        Assert.assertFalse(reader.hasRemaining());
    }

    @Test
    public void testStrings()
    {
        String[] strings = {null, "", "the quick brown fox jumped over the lazy dog", "0123456789abcdef", "0123456789ABCDEF", "The quick brown Fox jumped over the lazy Dog.", "-1234", "3415", "aa0b05f2c8", "a string with some unicode in it: \u2022"};
        TokenBuilder builder = Token.newBuilder();
        for (String string : strings)
        {
            builder.putString(string);
        }

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        for (String string : strings)
        {
            Assert.assertEquals(string, reader.getString());
        }
        Assert.assertFalse(reader.hasRemaining());
    }

    @Test
    public void testMixedTypes()
    {
        Object[] objects = {0, true, "aa0b05f2c8", 5, Integer.MAX_VALUE, "", false, "the quick brown fox jumped over the lazy dog"};
        TokenBuilder builder = Token.newBuilder();
        for (Object object : objects)
        {
            if (object instanceof Integer)
            {
                builder.putInt((Integer) object);
            }
            else if (object instanceof Boolean)
            {
                builder.putBoolean((Boolean) object);
            }
            else if (object instanceof String)
            {
                builder.putString((String) object);
            }
            else
            {
                throw new RuntimeException("Unhandled: " + object);
            }
        }

        String tokenString = builder.toTokenString();
        assertIsURLSafe(tokenString);

        TokenReader reader = Token.newReader(tokenString);
        for (Object expected : objects)
        {
            Object actual;
            if (expected instanceof Integer)
            {
                actual = reader.getInt();
            }
            else if (expected instanceof Boolean)
            {
                actual = reader.getBoolean();
            }
            else if (expected instanceof String)
            {
                actual = reader.getString();
            }
            else
            {
                throw new RuntimeException("Unhandled: " + expected);
            }
            Assert.assertEquals(expected, actual);
        }
        Assert.assertFalse(reader.hasRemaining());
    }

    private void assertIsURLSafe(String token)
    {
        try
        {
            if (!token.equals(URLEncoder.encode(token, "UTF-8")))
            {
                Assert.fail("Token not URL-safe: " + token);
            }
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

}
