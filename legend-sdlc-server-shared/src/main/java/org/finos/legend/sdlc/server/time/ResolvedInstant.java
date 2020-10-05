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

import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

/**
 * An {@link Instant} that has been resolved from a {@link TemporalAccessor}. For example,
 * the start Instant of a {@link java.time.Year}. The particulars of the resolution are
 * determined by the implementing class.
 */
public abstract class ResolvedInstant
{
    private final Instant resolved;
    private final TemporalAccessor original;

    protected ResolvedInstant(Instant resolved, TemporalAccessor original)
    {
        this.resolved = resolved;
        this.original = original;
    }

    public final Instant getResolvedInstant()
    {
        return this.resolved;
    }

    public final TemporalAccessor getOriginalTemporalAccessor()
    {
        return this.original;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if ((other == null) || (this.getClass() != other.getClass()))
        {
            return false;
        }

        ResolvedInstant that = (ResolvedInstant) other;
        return this.resolved.equals(that.resolved) && this.original.equals(that.original);
    }

    @Override
    public int hashCode()
    {
        return this.resolved.hashCode() + (199 * this.original.hashCode());
    }

    @Override
    public String toString()
    {
        return this.resolved.toString();
    }

    public static Instant getResolvedInstantIfNonNull(ResolvedInstant resolvedInstant)
    {
        return (resolvedInstant == null) ? null : resolvedInstant.getResolvedInstant();
    }

    public abstract static class InstantResolver<T extends ResolvedInstant>
    {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .parseLenient()
                .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .optionalStart()
                .appendLiteral('-')
                .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
                .optionalStart()
                .appendLiteral('-')
                .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                .optionalStart()
                .appendLiteral('T')
                .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
                .optionalStart()
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 1, 2, SignStyle.NOT_NEGATIVE)
                .optionalStart()
                .appendLiteral(':')
                .appendValue(ChronoField.SECOND_OF_MINUTE, 1, 2, SignStyle.NOT_NEGATIVE)
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .optionalEnd()
                .optionalEnd()
                .optionalEnd()
                .optionalEnd()
                .optionalEnd()
                .optionalStart()
                .appendZoneOrOffsetId()
                .toFormatter();

        public T parseAndResolve(String text)
        {
            if (text == null)
            {
                throw new IllegalArgumentException("Cannot parse null");
            }
            ParsePosition position = new ParsePosition(0);
            TemporalAccessor parsed = FORMATTER.parseUnresolved(text, position);
            if ((parsed == null) || (position.getIndex() != text.length()))
            {
                throw new IllegalArgumentException("Could not parse \"" + text + "\"");
            }
            return resolve(parsed);
        }

        public T resolve(TemporalAccessor original)
        {
            if (original == null)
            {
                throw new IllegalArgumentException("Cannot resolve null");
            }
            Instant resolved = resolveToInstant(original);
            if (resolved == null)
            {
                throw new IllegalArgumentException("Could not resolve: " + original);
            }
            return build(resolved, original);
        }

        private Instant resolveToInstant(TemporalAccessor original)
        {
            if (original instanceof Instant)
            {
                return (Instant) original;
            }

            long epochSeconds = resolveEpochSeconds(original);
            int nanosOfSecond = original.isSupported(ChronoField.NANO_OF_SECOND) ? original.get(ChronoField.NANO_OF_SECOND) : resolveNanosOfSecond(epochSeconds);
            return Instant.ofEpochSecond(epochSeconds, nanosOfSecond);
        }

        private long resolveEpochSeconds(TemporalAccessor original)
        {
            if (original.isSupported(ChronoField.INSTANT_SECONDS))
            {
                return original.getLong(ChronoField.INSTANT_SECONDS);
            }

            int year = original.get(ChronoField.YEAR);
            int month = original.isSupported(ChronoField.MONTH_OF_YEAR) ? original.get(ChronoField.MONTH_OF_YEAR) : resolveMonthOfYear(year);
            int day = original.isSupported(ChronoField.DAY_OF_MONTH) ? original.get(ChronoField.DAY_OF_MONTH) : resolveDayOfMonth(year, month);
            int hour = original.isSupported(ChronoField.HOUR_OF_DAY) ? original.get(ChronoField.HOUR_OF_DAY) : resolveHourOfDay(year, month, day);
            int minute = original.isSupported(ChronoField.MINUTE_OF_HOUR) ? original.get(ChronoField.MINUTE_OF_HOUR) : resolveMinuteOfHour(year, month, day, hour);
            int second = original.isSupported(ChronoField.SECOND_OF_MINUTE) ? original.get(ChronoField.SECOND_OF_MINUTE) : resolveSecondOfMinute(year, month, day, hour, minute);
            ZoneId zone = original.query(TemporalQueries.zone());

            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute, second);
            return ZonedDateTime.ofLocal(localDateTime, (zone == null) ? ZoneOffset.UTC : zone, null).toEpochSecond();
        }

        protected abstract int resolveMonthOfYear(int year);

        protected abstract int resolveDayOfMonth(int year, int month);

        protected abstract int resolveHourOfDay(int year, int month, int day);

        protected abstract int resolveMinuteOfHour(int year, int month, int day, int hour);

        protected abstract int resolveSecondOfMinute(int year, int month, int day, int hour, int minute);

        protected abstract int resolveNanosOfSecond(long epochSeconds);

        protected abstract T build(Instant resolved, TemporalAccessor original);
    }
}
