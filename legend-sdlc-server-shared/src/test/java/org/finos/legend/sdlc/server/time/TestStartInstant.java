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

package org.finos.legend.sdlc.server.time;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TestStartInstant extends AbstractTestResolvedInstant<StartInstant>
{
    @Test
    @Override
    public void testParseInstant()
    {
        super.testParseInstant();
        assertParses(Instant.parse("2020-01-30T19:17:04.000000000Z"), "2020-01-30T19:17:04Z");
    }

    @Override
    protected ResolvedInstant.InstantResolver<StartInstant> getResolver()
    {
        return StartInstant.RESOLVER;
    }

    @Override
    protected Instant getExpected(int year, ZoneId zone)
    {
        return getExpected(year, 1, zone);
    }

    @Override
    protected Instant getExpected(int year, int month, ZoneId zone)
    {
        return getExpected(year, month, 1, zone);
    }

    @Override
    protected Instant getExpected(int year, int month, int day, ZoneId zone)
    {
        return getExpected(year, month, day, 0, zone);
    }

    @Override
    protected Instant getExpected(int year, int month, int day, int hour, ZoneId zone)
    {
        return getExpected(year, month, day, hour, 0, zone);
    }

    @Override
    protected Instant getExpected(int year, int month, int day, int hour, int minute, ZoneId zone)
    {
        return getExpected(year, month, day, hour, minute, 0, zone);
    }

    @Override
    protected Instant getExpected(int year, int month, int day, int hour, int minute, int second, ZoneId zone)
    {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, resolveZone(zone)).toInstant();
    }
}
