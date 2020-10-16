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
import java.time.format.DateTimeFormatter;

public abstract class SessionBuilder<T extends Session>
{
    private String userId;
    private Instant creationTime;

    /**
     * Get the user id that has been set, if any.
     *
     * @return user id or null if not set
     */
    public String getUserId()
    {
        return this.userId;
    }

    /**
     * Set the user id, and return the builder.
     *
     * @param userId user id
     * @return this builder
     */
    public SessionBuilder<T> withUserId(String userId)
    {
        this.userId = userId;
        return this;
    }

    /**
     * Get the creation time that has been set, if any.
     *
     * @return creation time or null if not set
     */
    public Instant getCreationTime()
    {
        return this.creationTime;
    }

    /**
     * Set the creation time, and return the builder. Creation time is only stored to second precision. Any sub-second
     * information is dropped.
     *
     * @param creationTime creation time
     * @return this builder
     */
    public SessionBuilder<T> withCreationTime(Instant creationTime)
    {
        this.creationTime = ((creationTime != null) && (creationTime.getNano() != 0)) ? Instant.ofEpochSecond(creationTime.getEpochSecond()) : creationTime;
        return this;
    }

    /**
     * Set the builder state from the given token string, and return the builder.
     *
     * @param tokenString token string
     * @return this builder
     */
    public SessionBuilder<T> fromToken(String tokenString)
    {
        return fromToken(Token.newReader(tokenString));
    }

    /**
     * Set the builder state from the given token reader, and return the builder.
     *
     * @param reader token reader
     * @return this builder
     */
    public SessionBuilder<T> fromToken(Token.TokenReader reader)
    {
        String userIdFromToken = reader.getString();
        Instant creationTimeFromToken = Instant.ofEpochSecond(reader.getLong());
        return withUserId(userIdFromToken).withCreationTime(creationTimeFromToken);
    }

    /**
     * Reset the builder to its initial state, and return the builder.
     *
     * @return this builder
     */
    public SessionBuilder<T> reset()
    {
        this.userId = null;
        this.creationTime = null;
        return this;
    }

    /**
     * Validate that the state of the builder is suitable for building the given type of session. Throws an
     * IllegalStateException if not.
     *
     * @throws IllegalStateException if the state of the builder is not suitable for building a new session
     */
    public void validate()
    {
        if ((getCreationTime() != null) && Instant.now().isBefore(getCreationTime()))
        {
            StringBuilder builder = new StringBuilder("Invalid creation time (in the future): ");
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(getCreationTime(), builder);
            throw new IllegalStateException(builder.toString());
        }
    }

    /**
     * Build a new session.
     *
     * @return new session
     * @throws IllegalStateException if the state of the builder is not suitable for building a new session
     */
    public final T build()
    {
        validate();
        return newSession();
    }

    /**
     * Build the new session. Called after {@link #validate()} by {@link #build()}.
     *
     * @return new session
     */
    protected abstract T newSession();
}
