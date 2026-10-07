package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/** Runs in the default context, with no quota configured, so the shipped default applies. */
@IntegrationTest
class DailyQuotaDefaultIT {

    @Autowired
    Environment environment;

    @Test
    @DisplayName("AC17 (spec 02): with LINK_DAILY_QUOTA unset, the daily quota is 500")
    void defaultQuotaIs500() {
        assertThat(DailyQuotaSetting.parse(environment.getProperty("url-shortener.link-daily-quota")))
                .isEqualTo(500);
    }
}
