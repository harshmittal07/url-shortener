package io.github.harshmittal.urlshortener.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves each architecture rule reports a violation planted in the {@code fixture} package, so a
 * rule with a wrong package pattern cannot pass silently.
 */
class ArchitectureRulesSelfTest {

    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages("io.github.harshmittal.urlshortener.architecture.fixture");
    }

    static Stream<Arguments> rulesAndPlantedViolations() {
        return Stream.of(
                Arguments.of(ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE, "SpringAwareDomain"),
                Arguments.of(ArchitectureRules.DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS, "Instant.now()"),
                Arguments.of(ArchitectureRules.DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS, "LocalDateTime.now()"),
                Arguments.of(ArchitectureRules.DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS, "Random.<init>()"),
                Arguments.of(ArchitectureRules.DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS, "UUID.randomUUID()"),
                Arguments.of(ArchitectureRules.REDIRECT_USES_LINK_ONLY_THROUGH_API, "RedirectReachingLinkDomain"),
                Arguments.of(ArchitectureRules.LINK_DOES_NOT_DEPEND_ON_REDIRECT, "LinkReachingRedirect"),
                Arguments.of(ArchitectureRules.SHARED_DOES_NOT_DEPEND_ON_MODULES, "SharedReachingLink"),
                Arguments.of(ArchitectureRules.IDENTITY_ADAPTERS_ARE_PRIVATE, "LinkReachingIdentityAdapter"),
                Arguments.of(ArchitectureRules.LINK_DOES_NOT_DEPEND_ON_IDENTITY, "LinkReachingIdentityAdapter"),
                Arguments.of(ArchitectureRules.CONFIGURATION_ONLY_IN_APP, "ConfigurationOutsideApp"),
                Arguments.of(ArchitectureRules.BEANS_ONLY_IN_APP, "ConfigurationOutsideApp.bean()"),
                Arguments.of(ArchitectureRules.NO_CYCLES_BETWEEN_MODULES, "Cycle"));
    }

    @ParameterizedTest(name = "{0} catches {1}")
    @MethodSource("rulesAndPlantedViolations")
    @DisplayName("S-20: each architecture rule detects its planted violation")
    void ruleDetectsPlantedViolation(ArchRule rule, String expectedInReport) {
        EvaluationResult result = rule.evaluate(fixtures);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString()).contains(expectedInReport);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allowedDependencies")
    @DisplayName("S-20: the rules allow the dependencies the architecture permits")
    void ruleAllowsPermittedDependency(ArchRule rule, String permittedClass) {
        EvaluationResult result = rule.evaluate(fixtures);

        assertThat(result.getFailureReport().toString()).doesNotContain(permittedClass);
    }

    static Stream<Arguments> allowedDependencies() {
        return Stream.of(
                Arguments.of(ArchitectureRules.REDIRECT_USES_LINK_ONLY_THROUGH_API, "RedirectUsingLinkApi"),
                Arguments.of(ArchitectureRules.IDENTITY_ADAPTERS_ARE_PRIVATE, "AppWiringIdentityAdapter"),
                Arguments.of(ArchitectureRules.CONFIGURATION_ONLY_IN_APP, "AppConfiguration"));
    }
}
