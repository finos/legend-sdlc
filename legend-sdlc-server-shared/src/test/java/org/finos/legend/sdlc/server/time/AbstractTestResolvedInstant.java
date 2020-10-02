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

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Random;

public abstract class AbstractTestResolvedInstant<T extends ResolvedInstant>
{
    private static final ZoneId[] ZONES = {null, ZoneOffset.UTC, ZoneId.of("America/New_York"), ZoneId.of("Europe/London"), ZoneId.of("Europe/Madrid"), ZoneId.of("Asia/Hong_Kong")};
    private static final int[] YEARS = {-15, 1869, 1899, 1900, 1901, 1999, 2000, 2001, 2011, 2019};
    private static final int DAY_COUNT = 5;
    private static final int HOUR_COUNT = 4;
    private static final int MINUTE_COUNT = 3;
    private static final int SECOND_COUNT = 5;
    private static final Random RANDOM = new Random();

    // Resolution tests

    @Test
    public void testResolveYear()
    {
        for (int year : YEARS)
        {
            assertResolves(Integer.toString(year), getExpected(year, ZoneOffset.UTC), Year.of(year));
        }
    }

    @Test
    public void testResolveYearMonth()
    {
        for (int year : YEARS)
        {
            for (int month = 1; month <= 12; month++)
            {
                YearMonth yearMonth = YearMonth.of(year, month);
                assertResolves(yearMonth.toString(), getExpected(year, month, ZoneOffset.UTC), yearMonth);
            }
        }
    }

    @Test
    public void testResolveLocalDate()
    {
        for (int year : YEARS)
        {
            for (int month = 1; month <= 12; month++)
            {
                YearMonth yearMonth = YearMonth.of(year, month);
                for (int day = 1, length = yearMonth.lengthOfMonth(); day <= length; day++)
                {
                    LocalDate date = yearMonth.atDay(day);
                    assertResolves(date.toString(), getExpected(year, month, day, ZoneOffset.UTC), date);
                }
            }
        }
    }

    @Test
    public void testResolveInstant()
    {
        Instant now = Instant.now();
        assertResolves(now, now);

        assertResolves(Instant.EPOCH, Instant.EPOCH);

        Instant instant = Instant.parse("2019-12-01T13:45:27.123456789Z");
        assertResolves(instant, instant);
    }

    // Parsing tests

    @Test
    public void testParseYear()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                String text = year + ((zone == null) ? "" : zone.getId());
                assertParses(getExpected(year, zone), text);
            }
        }
    }

    @Test
    public void testParseMonth()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                for (int month = 1; month <= 12; month++)
                {
                    String text = year + "-" + month + ((zone == null) ? "" : zone.getId());
                    assertParses(getExpected(year, month, zone), text);
                }
            }
        }
    }

    @Test
    public void testParseDay()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                for (int month = 1; month <= 12; month++)
                {
                    int monthLength = YearMonth.of(year, month).lengthOfMonth();
                    for (int d = 0; d < DAY_COUNT; d++)
                    {
                        int day = RANDOM.nextInt(monthLength) + 1;
                        String text = year + "-" + month + "-" + day + ((zone == null) ? "" : zone.getId());
                        assertParses(getExpected(year, month, day, zone), text);
                    }
                }
            }
        }
    }

    @Test
    public void testParseHour()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                for (int month = 1; month <= 12; month++)
                {
                    int monthLength = YearMonth.of(year, month).lengthOfMonth();
                    for (int d = 0; d < DAY_COUNT; d++)
                    {
                        int day = RANDOM.nextInt(monthLength) + 1;
                        for (int h = 0; h < HOUR_COUNT; h++)
                        {
                            int hour = RANDOM.nextInt(24);
                            String text = year + "-" + month + "-" + day + "T" + hour + ((zone == null) ? "" : zone.getId());
                            assertParses(getExpected(year, month, day, hour, zone), text);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testParseMinute()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                for (int month = 1; month <= 12; month++)
                {
                    int monthLength = YearMonth.of(year, month).lengthOfMonth();
                    for (int d = 0; d < DAY_COUNT; d++)
                    {
                        int day = RANDOM.nextInt(monthLength) + 1;
                        for (int h = 0; h < HOUR_COUNT; h++)
                        {
                            int hour = RANDOM.nextInt(24);
                            for (int m = 0; m < MINUTE_COUNT; m++)
                            {
                                int minute = RANDOM.nextInt(60);
                                String text = year + "-" + month + "-" + day + "T" + hour + ":" + minute + ((zone == null) ? "" : zone.getId());
                                assertParses(getExpected(year, month, day, hour, minute, zone), text);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testParseSecond()
    {
        for (ZoneId zone : ZONES)
        {
            for (int year : YEARS)
            {
                for (int month = 1; month <= 12; month++)
                {
                    int monthLength = YearMonth.of(year, month).lengthOfMonth();
                    for (int d = 0; d < DAY_COUNT; d++)
                    {
                        int day = RANDOM.nextInt(monthLength) + 1;
                        for (int h = 0; h < HOUR_COUNT; h++)
                        {
                            int hour = RANDOM.nextInt(24);
                            for (int m = 0; m < MINUTE_COUNT; m++)
                            {
                                int minute = RANDOM.nextInt(60);
                                for (int s = 0; s < SECOND_COUNT; s++)
                                {
                                    int second = RANDOM.nextInt(60);
                                    String text = year + "-" + month + "-" + day + "T" + hour + ":" + minute + ":" + second + ((zone == null) ? "" : zone.getId());
                                    assertParses(getExpected(year, month, day, hour, minute, second, zone), text);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testParseInstant()
    {
        Instant now = Instant.now();
        String nowString = now.toString();
        if (!nowString.contains("."))
        {
            nowString = nowString.substring(0, nowString.length() - 1) + ".0Z";
        }
        assertParses(now, nowString);

        assertParses(Instant.EPOCH, "1970-01-01T00:00:00.000000000Z");

        assertParses(Instant.parse("2019-12-01T13:45:27.123456789Z"), "2019-12-01T13:45:27.123456789Z");
        assertParses(Instant.parse("0018-02-01T03:05:07.123456789Z"), "18-2-1T3:5:7.123456789Z");
        assertParses(Instant.parse("2018-02-01T03:05:07.123456789Z"), "2018-2-1T3:5:7.123456789Z");
        assertParses(Instant.parse("2018-02-01T03:05:07.123456Z"), "2018-2-1T3:5:7.123456");
        assertParses(Instant.parse("2020-01-30T19:17:04.0Z"), "2020-01-30T19:17:04.0Z");
    }

    @Test
    public void testParsingErrors()
    {
        assertDoesNotParse(null);
        assertDoesNotParse("abcd");
        assertDoesNotParse("2010-abcd");
        assertDoesNotParse("abcd-2010");
        assertDoesNotParse("2019-03-22T12/13/14");
        assertDoesNotParse("2019/03/22");
    }

    protected T parseAndResolve(String text)
    {
        return getResolver().parseAndResolve(text);
    }

    protected T resolve(TemporalAccessor original)
    {
        return getResolver().resolve(original);
    }

    protected void assertParses(Instant expected, String text)
    {
        T resolved = parseAndResolve(text);
        Assert.assertEquals(text, expected, resolved.getResolvedInstant());
    }

    protected void assertDoesNotParse(String text)
    {
        try
        {
            T resolved = parseAndResolve(text);
            Assert.fail("Expected an error parsing \"" + text + "\", got: " + resolved.getOriginalTemporalAccessor() + " (original); " + resolved.getResolvedInstant() + " (resolved)");
        }
        catch (Exception ignore)
        {
            // success
        }
    }

    protected void assertResolves(Instant expected, TemporalAccessor original)
    {
        assertResolves(null, expected, original);
    }

    protected void assertResolves(String message, Instant expected, TemporalAccessor original)
    {
        T resolved = resolve(original);
        Assert.assertEquals(message, expected, resolved.getResolvedInstant());
        Assert.assertSame(message, original, resolved.getOriginalTemporalAccessor());
    }

    protected abstract ResolvedInstant.InstantResolver<T> getResolver();

    protected abstract Instant getExpected(int year, ZoneId zoneId);

    protected abstract Instant getExpected(int year, int month, ZoneId zoneId);

    protected abstract Instant getExpected(int year, int month, int day, ZoneId zoneId);

    protected abstract Instant getExpected(int year, int month, int day, int hour, ZoneId zoneId);

    protected abstract Instant getExpected(int year, int month, int day, int hour, int minute, ZoneId zoneId);

    protected abstract Instant getExpected(int year, int month, int day, int hour, int minute, int second, ZoneId zoneId);

    protected ZoneId resolveZone(ZoneId zone)
    {
        return (zone == null) ? getDefaultZone() : zone;
    }

    protected ZoneId getDefaultZone()
    {
        return ZoneOffset.UTC;
    }
}
