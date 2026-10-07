package io.github.harshmittal.urlshortener.architecture.fixture.link.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

public class WallClockDomain {
    Object[] ambient() {
        return new Object[] {Instant.now(), LocalDateTime.now(), new Random(), UUID.randomUUID()};
    }
}
