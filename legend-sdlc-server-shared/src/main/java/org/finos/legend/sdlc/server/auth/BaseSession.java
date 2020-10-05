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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public abstract class BaseSession implements Session
{
    private static final long serialVersionUID = -5374017688208975327L;

    private final String userId;
    private final Instant creationTime;

    protected BaseSession(String userId, Instant creationTime)
    {
        this.userId = userId;
        this.creationTime = validateAndCanonicalizeCreationTime(creationTime);
    }

    @Override
    public final String getUserId()
    {
        return this.userId;
    }

    @Override
    public final Instant getCreationTime()
    {
        return this.creationTime;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(64).append('<').append(getClass().getSimpleName());
        builder.append(" userId=");
        if (this.userId == null)
        {
            builder.append("null");
        }
        else
        {
            builder.append('"').append(this.userId).append('"');
        }
        builder.append(" creationTime=");
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(ZonedDateTime.ofInstant(this.creationTime, ZoneOffset.UTC), builder);
        writeToStringInfo(builder);
        return builder.append('>').toString();
    }

    protected void writeToStringInfo(StringBuilder builder)
    {
    }

    @Override
    public Token.TokenBuilder encode(Token.TokenBuilder tokenBuilder)
    {
        return tokenBuilder.putString(this.userId).putLong(this.creationTime.getEpochSecond());
    }

    private static Instant validateAndCanonicalizeCreationTime(Instant creationTime)
    {
        long nowEpochSecond = Instant.now().getEpochSecond();
        if (creationTime == null)
        {
            return Instant.ofEpochSecond(nowEpochSecond);
        }

        if (nowEpochSecond < creationTime.getEpochSecond())
        {
            StringBuilder builder = new StringBuilder("Invalid creation time (in the future): ");
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(ZonedDateTime.ofInstant(creationTime, ZoneOffset.UTC), builder);
            throw new IllegalArgumentException(builder.toString());
        }

        return (creationTime.getNano() == 0) ? creationTime : Instant.ofEpochSecond(creationTime.getEpochSecond());
    }
}
