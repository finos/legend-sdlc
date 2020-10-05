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

import java.io.Serializable;
import java.time.Instant;

public interface Session extends Serializable
{
    /**
     * Id of the user that owns the session. May be null
     * if the session is not owned by an authenticated user.
     *
     * @return user id
     */
    String getUserId();

    /**
     * Creation time of the session, to second precision.
     * Should never be null.
     *
     * @return creation time of the session
     */
    Instant getCreationTime();

    /**
     * Whether the session is still valid.
     *
     * @return whether the session is still valid
     */
    boolean isValid();

    /**
     * Encode the session in a token builder. Encodes the user id
     * followed by the creation time as a long number of seconds
     * from Java epoch. Implementing classes that override the default
     * must also start by encoding these values in the same way and
     * same order.
     *
     * @param tokenBuilder token builder
     * @return the supplied token builder
     */
    default Token.TokenBuilder encode(Token.TokenBuilder tokenBuilder)
    {
        return tokenBuilder.putString(getUserId()).putLong(getCreationTime().getEpochSecond());
    }

    default String encode()
    {
        return encode(Token.newBuilder()).toTokenString();
    }
}
