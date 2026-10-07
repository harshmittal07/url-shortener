package io.github.harshmittal.urlshortener.app;

import java.util.regex.Pattern;

/**
 * Checks {@code LINK_DAILY_QUOTA} (spec 02 R11, AC19). The property is bound as a string because an
 * {@code int} binding would make Spring's conversion error quote the rejected value; this message
 * names the variable only. At most nine digits keeps every accepted value in {@code int} range.
 */
final class DailyQuotaSetting {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]{1,9}");

    private DailyQuotaSetting() {}

    static int parse(String raw) {
        if (raw == null || !WHOLE_NUMBER.matcher(raw).matches() || Integer.parseInt(raw) < 1) {
            throw new IllegalStateException(
                    "Invalid configuration: LINK_DAILY_QUOTA must be a whole number of at least 1");
        }
        return Integer.parseInt(raw);
    }
}
