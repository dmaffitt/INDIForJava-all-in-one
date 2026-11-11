package org.indilib.i4j;

/*
 * #%L
 * INDI for Java Base Library
 * %%
 * Copyright (C) 2012 - 2014 indiforjava
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Formats and parses the timestamps of the INDI protocol. They are in UTC,
 * whatever the time zone of the machine, in the format
 * yyyy-MM-ddTHH:mm:ss with an optional fraction of a second.
 *
 * @author S. Alonso (Zerjillo) [zerjioi at ugr.es]
 */
public final class INDIDateFormat {

    /**
     * the format for writing timestamps.
     */
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    /**
     * the instance, java.time formatters are thread safe.
     */
    private static final INDIDateFormat INSTANCE = new INDIDateFormat();

    /**
     * private constructor, use {@link #dateFormat()}.
     */
    private INDIDateFormat() {
    }

    /**
     * @return the INDI date format.
     */
    public static INDIDateFormat dateFormat() {
        return INSTANCE;
    }

    /**
     * Parses a timestamp. Missing or invalid timestamps return the current
     * time.
     *
     * @param time the timestamp, in UTC.
     * @return the parsed instant.
     */
    public Instant parseInstant(final String time) {
        if (time != null && !time.isEmpty()) {
            String utc = time.trim();
            if (utc.endsWith("Z")) {
                utc = utc.substring(0, utc.length() - 1);
            }
            try {
                return LocalDateTime.parse(utc, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                // Not correct format, returning current timestamp.
            }
        }
        return Instant.now();
    }

    /**
     * Parses a timestamp. Missing or invalid timestamps return the current
     * time.
     *
     * @param time the timestamp, in UTC.
     * @return the parsed date.
     */
    public Date parseTimestamp(final String time) {
        return Date.from(parseInstant(time));
    }

    /**
     * @param timestamp the instant to format.
     * @return the timestamp in UTC, with milliseconds.
     */
    public String formatInstant(final Instant timestamp) {
        return FORMAT.format(timestamp);
    }

    /**
     * @param timestamp the date to format.
     * @return the timestamp in UTC, with milliseconds.
     */
    public String formatTimestamp(final Date timestamp) {
        return formatInstant(timestamp.toInstant());
    }

    /**
     * @return the current time as a timestamp.
     */
    public String getCurrentTimestamp() {
        return formatInstant(Instant.now());
    }
}
