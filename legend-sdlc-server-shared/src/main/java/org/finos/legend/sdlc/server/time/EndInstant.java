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
import java.time.YearMonth;
import java.time.temporal.TemporalAccessor;

public class EndInstant extends ResolvedInstant
{
    public static final InstantResolver<EndInstant> RESOLVER = new InstantResolver<EndInstant>()
    {
        @Override
        protected int resolveMonthOfYear(int year)
        {
            return 12;
        }

        @Override
        protected int resolveDayOfMonth(int year, int month)
        {
            return YearMonth.of(year, month).lengthOfMonth();
        }

        @Override
        protected int resolveHourOfDay(int year, int month, int day)
        {
            return 23;
        }

        @Override
        protected int resolveMinuteOfHour(int year, int month, int day, int hour)
        {
            return 59;
        }

        @Override
        protected int resolveSecondOfMinute(int year, int month, int day, int hour, int minute)
        {
            return 59;
        }

        @Override
        protected int resolveNanosOfSecond(long epochSeconds)
        {
            return 999_999_999;
        }

        @Override
        protected EndInstant build(Instant resolved, TemporalAccessor original)
        {
            return new EndInstant(resolved, original);
        }
    };

    private EndInstant(Instant resolved, TemporalAccessor original)
    {
        super(resolved, original);
    }
}
