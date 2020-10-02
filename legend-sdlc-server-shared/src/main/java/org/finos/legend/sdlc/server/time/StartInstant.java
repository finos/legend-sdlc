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

import java.time.Instant;
import java.time.temporal.TemporalAccessor;

public class StartInstant extends ResolvedInstant
{
    public static final InstantResolver<StartInstant> RESOLVER = new InstantResolver<StartInstant>()
    {
        @Override
        protected int resolveMonthOfYear(int year)
        {
            return 1;
        }

        @Override
        protected int resolveDayOfMonth(int year, int month)
        {
            return 1;
        }

        @Override
        protected int resolveHourOfDay(int year, int month, int day)
        {
            return 0;
        }

        @Override
        protected int resolveMinuteOfHour(int year, int month, int day, int hour)
        {
            return 0;
        }

        @Override
        protected int resolveSecondOfMinute(int year, int month, int day, int hour, int minute)
        {
            return 0;
        }

        @Override
        protected int resolveNanosOfSecond(long epochSeconds)
        {
            return 0;
        }

        @Override
        protected StartInstant build(Instant resolved, TemporalAccessor original)
        {
            return new StartInstant(resolved, original);
        }
    };

    private StartInstant(Instant resolved, TemporalAccessor original)
    {
        super(resolved, original);
    }
}
