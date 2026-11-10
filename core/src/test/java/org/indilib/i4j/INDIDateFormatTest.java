package org.indilib.i4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

import static org.indilib.i4j.INDIDateFormat.dateFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INDI timestamps are UTC, whatever the time zone of the machine.
 */
class INDIDateFormatTest {

    private TimeZone defaultTimeZone;

    @BeforeEach
    void useANonUtcTimeZone() {
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    void formatsInUtc() {
        Date timestamp = Date.from(Instant.parse("2026-11-09T12:34:56.789Z"));
        assertEquals("2026-11-09T12:34:56.789", dateFormat().formatTimestamp(timestamp));
    }

    @Test
    void parsesAsUtc() {
        assertEquals(Instant.parse("2026-11-09T12:34:56.789Z"), dateFormat().parseTimestamp("2026-11-09T12:34:56.789").toInstant());
        assertEquals(Instant.parse("2026-11-09T12:34:56Z"), dateFormat().parseTimestamp("2026-11-09T12:34:56").toInstant());
    }

    @Test
    void parsesFractionsOfASecond() {
        // ".7" is 700 milliseconds, not 7
        assertEquals(Instant.parse("2026-11-09T12:34:56.700Z"), dateFormat().parseTimestamp("2026-11-09T12:34:56.7").toInstant());
        assertEquals(Instant.parse("2026-11-09T12:34:56.120Z"), dateFormat().parseTimestamp("2026-11-09T12:34:56.12").toInstant());
    }

    @Test
    void roundTrips() {
        String timestamp = "2026-01-31T23:59:59.999";
        assertEquals(timestamp, dateFormat().formatTimestamp(dateFormat().parseTimestamp(timestamp)));
    }

    @Test
    void currentTimestampIsUtcNow() {
        Instant now = dateFormat().parseTimestamp(dateFormat().getCurrentTimestamp()).toInstant();
        assertTrue(Duration.between(now, Instant.now()).abs().toSeconds() < 5, "current timestamp " + now);
    }

    @Test
    void invalidOrMissingTimestampsMeanNow() {
        for (String invalid : new String[]{null, "", "yesterday"}) {
            Instant parsed = dateFormat().parseTimestamp(invalid).toInstant();
            assertTrue(Duration.between(parsed, Instant.now()).abs().toSeconds() < 5, "parsed " + invalid);
        }
    }
}
