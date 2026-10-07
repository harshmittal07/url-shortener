package io.github.harshmittal.urlshortener.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Stops startup when a required environment variable is missing or malformed (R22, AC36). Every
 * problem is reported at once, by variable name only: values never appear in the message, because
 * startup failures are logged.
 */
final class RequiredEnvironmentCheck {

    private static final int MIN_SALT_LENGTH = 32;
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private RequiredEnvironmentCheck() {}

    /**
     * @param variables looks a variable up by name; {@code null} or blank means missing
     */
    static Settings verify(UnaryOperator<String> variables) {
        List<String> problems = new ArrayList<>();

        String publicBaseUrl = required(variables, "PUBLIC_BASE_URL", problems);
        if (publicBaseUrl != null && !isHttpUrlWithHost(publicBaseUrl)) {
            problems.add("PUBLIC_BASE_URL must be an http or https URL with a host");
        }
        String ipHashSalt = required(variables, "IP_HASH_SALT", problems);
        if (ipHashSalt != null && ipHashSalt.length() < MIN_SALT_LENGTH) {
            problems.add("IP_HASH_SALT must be at least " + MIN_SALT_LENGTH + " characters");
        }
        String adminKeyHash = required(variables, "BOOTSTRAP_ADMIN_KEY_HASH", problems);
        if (adminKeyHash != null && !SHA256_HEX.matcher(adminKeyHash).matches()) {
            problems.add("BOOTSTRAP_ADMIN_KEY_HASH must be 64 lowercase hex characters");
        }
        required(variables, "DB_APP_USER", problems);
        required(variables, "DB_APP_PASSWORD", problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid configuration: " + String.join("; ", problems));
        }
        return new Settings(publicBaseUrl, ipHashSalt, adminKeyHash);
    }

    private static String required(UnaryOperator<String> variables, String name, List<String> problems) {
        String value = variables.apply(name);
        if (value == null || value.isBlank()) {
            problems.add("missing required environment variable " + name);
            return null;
        }
        return value;
    }

    private static boolean isHttpUrlWithHost(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The verified values the application reads; database credentials go to the datasource directly. */
    record Settings(String publicBaseUrl, String ipHashSalt, String bootstrapAdminKeyHash) {
        @Override
        public String toString() {
            return "Settings[publicBaseUrl=<redacted>, ipHashSalt=<redacted>, bootstrapAdminKeyHash=<redacted>]";
        }
    }
}
