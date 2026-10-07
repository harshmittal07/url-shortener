package io.github.harshmittal.urlshortener.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Applies the architecture rules to production code (S-20). */
class ArchitectureTest {

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.harshmittal.urlshortener");
    }

    @Test
    @DisplayName("S-20: domain code does not depend on Spring, JPA, adapters or app")
    void domainIsFrameworkFree() {
        ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(production);
    }

    @Test
    @DisplayName("S-20: domain code takes time and randomness only from ports")
    void domainUsesNoAmbientTimeOrRandomness() {
        ArchitectureRules.DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS.check(production);
    }

    @Test
    @DisplayName("AC25, S-20: redirect depends on link only through link.api")
    void redirectUsesLinkOnlyThroughApi() {
        ArchitectureRules.REDIRECT_USES_LINK_ONLY_THROUGH_API.check(production);
    }

    @Test
    @DisplayName("S-20: link does not depend on redirect")
    void linkDoesNotDependOnRedirect() {
        ArchitectureRules.LINK_DOES_NOT_DEPEND_ON_REDIRECT.check(production);
    }

    @Test
    @DisplayName("S-20: the shared kernel does not depend on any module")
    void sharedDoesNotDependOnModules() {
        ArchitectureRules.SHARED_DOES_NOT_DEPEND_ON_MODULES.check(production);
    }

    @Test
    @DisplayName("AC7, S-20: identity adapters are used only inside shared.identity and by app")
    void identityAdaptersArePrivate() {
        ArchitectureRules.IDENTITY_ADAPTERS_ARE_PRIVATE.check(production);
    }

    @Test
    @DisplayName("AC7, S-20: the link module does not depend on shared.identity")
    void linkDoesNotDependOnIdentity() {
        ArchitectureRules.LINK_DOES_NOT_DEPEND_ON_IDENTITY.check(production);
    }

    @Test
    @DisplayName("S-20: @Configuration classes live only in app")
    void configurationOnlyInApp() {
        ArchitectureRules.CONFIGURATION_ONLY_IN_APP.check(production);
    }

    @Test
    @DisplayName("S-20: @Bean methods live only in app")
    void beansOnlyInApp() {
        ArchitectureRules.BEANS_ONLY_IN_APP.check(production);
    }

    @Test
    @DisplayName("S-20: there are no cycles between modules")
    void noCyclesBetweenModules() {
        ArchitectureRules.NO_CYCLES_BETWEEN_MODULES.check(production);
    }
}
