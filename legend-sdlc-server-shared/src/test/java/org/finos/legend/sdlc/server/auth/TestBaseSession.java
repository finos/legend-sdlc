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

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class TestBaseSession
{
    @Test
    public void testUserId()
    {
        Assert.assertNull(new StubSessionBuilder().build().getUserId());
        Assert.assertEquals("user", new StubSessionBuilder().withUserId("user").build().getUserId());
    }

    @Test
    public void testIsValid()
    {
        Assert.assertTrue(new StubSessionBuilder().withUserId("user").build().isValid());
        Assert.assertFalse(new StubSessionBuilder().build().isValid());
    }

    @Test
    public void testToString()
    {
        Instant creationTime = ZonedDateTime.of(2019, 10, 24, 14, 25, 51, 0, ZoneOffset.UTC).toInstant();
        Assert.assertEquals("<StubSession userId=null creationTime=2019-10-24T14:25:51Z text=null number=null>", new StubSessionBuilder().withCreationTime(creationTime).build().toString());
        Assert.assertEquals("<StubSession userId=\"null\" creationTime=2019-10-24T14:25:51Z text=\"null\" number=null>", new StubSessionBuilder().withText("null").withUserId("null").withCreationTime(creationTime).build().toString());
        Assert.assertEquals("<StubSession userId=\"user\" creationTime=2019-10-24T14:25:51Z text=\"additional info\" number=99243785211111>", new StubSessionBuilder().withText("additional info").withNumber(99243785211111L).withUserId("user").withCreationTime(creationTime).build().toString());
    }

    @Test
    public void testEncode()
    {
        StubSession before = new StubSessionBuilder().withText("the quick brown fox jumped over the lazy dog").withNumber(333888777444222L).withUserId("slothrop").build();
        String token = before.encode();
        StubSession after = new StubSessionBuilder().fromToken(token).build();
        Assert.assertEquals(before.getUserId(), after.getUserId());
        Assert.assertEquals(before.getText(), after.getText());
        Assert.assertEquals(before.getNumber(), after.getNumber());
        Assert.assertEquals(token, after.encode());

        StubSession beforeEmpty = new StubSessionBuilder().build();
        String tokenEmpty = beforeEmpty.encode();
        StubSession afterEmpty = new StubSessionBuilder().fromToken(tokenEmpty).build();
        Assert.assertEquals(beforeEmpty.getUserId(), afterEmpty.getUserId());
        Assert.assertEquals(beforeEmpty.getText(), afterEmpty.getText());
        Assert.assertEquals(beforeEmpty.getNumber(), afterEmpty.getNumber());
        Assert.assertEquals(tokenEmpty, afterEmpty.encode());
    }

    private static class StubSession extends BaseSession
    {
        private final String text;
        private final Long number;

        private StubSession(String userId, Instant creationTime, String text, Long number)
        {
            super(userId, creationTime);
            this.text = text;
            this.number = number;
        }

        public String getText()
        {
            return this.text;
        }

        public Long getNumber()
        {
            return this.number;
        }

        @Override
        public boolean isValid()
        {
            return getUserId() != null;
        }

        @Override
        protected void writeToStringInfo(StringBuilder builder)
        {
            builder.append(" text=");
            if (this.text == null)
            {
                builder.append("null");
            }
            else
            {
                builder.append('"').append(this.text).append('"');
            }
            builder.append(" number=").append(this.number);
        }

        @Override
        public Token.TokenBuilder encode(Token.TokenBuilder builder)
        {
            super.encode(builder).putString(this.text);
            return (this.number == null) ? builder.putBoolean(false) : builder.putBoolean(true).putLong(this.number);
        }
    }

    private static class StubSessionBuilder extends SessionBuilder<StubSession>
    {
        private String text;
        private Long number;

        public StubSessionBuilder withText(String text)
        {
            this.text = text;
            return this;
        }

        public StubSessionBuilder withNumber(Long number)
        {
            this.number = number;
            return this;
        }

        @Override
        public StubSessionBuilder reset()
        {
            super.reset();
            this.text = null;
            this.number = null;
            return this;
        }

        @Override
        protected StubSession newSession()
        {
            return new StubSession(getUserId(), getCreationTime(), this.text, this.number);
        }

        @Override
        public StubSessionBuilder fromToken(Token.TokenReader reader)
        {
            super.fromToken(reader);
            this.text = reader.getString();
            if (reader.getBoolean())
            {
                this.number = reader.getLong();
            }
            return this;
        }
    }
}
