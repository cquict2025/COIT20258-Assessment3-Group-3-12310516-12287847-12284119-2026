package au.edu.cqu.drs.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Converts the 24-hour time text used on the forms into a date-time on a given
 * day. Parsing lives here rather than in a controller so that every screen
 * treats an invalid time the same way.
 */
public final class TimeParser {

    /**
     * Prevents instances; every member is static.
     */
    private TimeParser() {
        // Utility class - not instantiated.
    }

    /**
     * Parses a time of day such as 14:30 onto the supplied date.
     *
     * @param text the text entered by the user
     * @param date the day the time belongs to
     * @return the combined date-time, or empty when the text is not a valid
     * time
     */
    public static Optional<LocalDateTime> parse(String text, LocalDate date) {
        if (text == null || text.isBlank() || date == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime
                    .of(date, LocalTime.parse(text.trim())));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses a time of day onto today's date.
     *
     * @param text the time, as HH:mm
     * @return the date-time, or empty if the text is not a valid time
     */
    public static Optional<LocalDateTime> parseToday(String text) {
        return parse(text, LocalDate.now());
    }

    /**
     * Describes what this utility class does.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "TimeParser: turns 24-hour time text into a date-time";
    }
}
