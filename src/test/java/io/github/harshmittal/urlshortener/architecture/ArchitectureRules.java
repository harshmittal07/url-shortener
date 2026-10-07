package io.github.harshmittal.urlshortener.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Architecture rules from AGENTS.md §6 and plan §11 (S-20). Package patterns are relative (for
 * example {@code ..link..}) so {@link ArchitectureRulesSelfTest} can prove each rule against
 * fixture classes.
 */
final class ArchitectureRules {

    private static final List<String> MODULES = List.of("app", "shared", "link", "redirect", "analytics");

    private ArchitectureRules() {}

    /** Rule 1: domain code is plain Java and depends only inward. */
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..adapter..", "..app..")
            .as("rule 1: domain does not depend on Spring, JPA, adapters or app");

    /** Rule 2: time comes from an injected Clock and randomness from ports. */
    static final ArchRule DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callConstructor(Random.class)
            .orShould()
            .callMethod(UUID.class, "randomUUID")
            .as("rule 2: domain does not call Instant.now(), LocalDateTime.now(), new Random() or UUID.randomUUID()");

    /** Rule 3a: redirect reaches link only through its public api package. */
    static final ArchRule REDIRECT_USES_LINK_ONLY_THROUGH_API = noClasses()
            .that()
            .resideInAPackage("..redirect..")
            .should()
            .dependOnClassesThat(resideInAPackage("..link..").and(not(resideInAPackage("..link.api.."))))
            .as("rule 3: redirect depends on link only through link.api");

    /** Rule 3b: link does not know about redirect. */
    static final ArchRule LINK_DOES_NOT_DEPEND_ON_REDIRECT = noClasses()
            .that()
            .resideInAPackage("..link..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..redirect..")
            .as("rule 3: link does not depend on redirect");

    /** Rule 3c: the shared kernel holds no business modules' code. */
    static final ArchRule SHARED_DOES_NOT_DEPEND_ON_MODULES = noClasses()
            .that()
            .resideInAPackage("..shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..link..", "..redirect..", "..analytics..")
            .as("rule 3: shared does not depend on any module");

    /** Rule 4a: identity adapters are wired only by app. */
    static final ArchRule IDENTITY_ADAPTERS_ARE_PRIVATE = noClasses()
            .that()
            .resideOutsideOfPackages("..shared.identity..", "..app..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..shared.identity.adapter..")
            .as("rule 4: nothing outside shared.identity (except app) depends on its adapters");

    /** Rule 4b: the link module never reads identity (R5, AC7). */
    static final ArchRule LINK_DOES_NOT_DEPEND_ON_IDENTITY = noClasses()
            .that()
            .resideInAPackage("..link..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..shared.identity..")
            .as("rule 4: link does not depend on shared.identity");

    /** Rule 5a: app is the only composition root. */
    static final ArchRule CONFIGURATION_ONLY_IN_APP = noClasses()
            .that()
            .resideOutsideOfPackage("..app..")
            .should()
            .beMetaAnnotatedWith(Configuration.class)
            .as("rule 5: @Configuration classes live only in app");

    /** Rule 5b: beans are declared only in app. */
    static final ArchRule BEANS_ONLY_IN_APP = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..app..")
            .should()
            .beAnnotatedWith(Bean.class)
            .as("rule 5: @Bean methods live only in app");

    /** Rule 6: no cycles between module slices. */
    static final ArchRule NO_CYCLES_BETWEEN_MODULES =
            slices().assignedFrom(new ModuleSlices()).should().beFreeOfCycles().as("rule 6: no cycles between modules");

    static List<ArchRule> all() {
        return List.of(
                DOMAIN_IS_FRAMEWORK_FREE,
                DOMAIN_USES_NO_AMBIENT_TIME_OR_RANDOMNESS,
                REDIRECT_USES_LINK_ONLY_THROUGH_API,
                LINK_DOES_NOT_DEPEND_ON_REDIRECT,
                SHARED_DOES_NOT_DEPEND_ON_MODULES,
                IDENTITY_ADAPTERS_ARE_PRIVATE,
                LINK_DOES_NOT_DEPEND_ON_IDENTITY,
                CONFIGURATION_ONLY_IN_APP,
                BEANS_ONLY_IN_APP,
                NO_CYCLES_BETWEEN_MODULES);
    }

    /** Assigns each class to the first package segment that names a module. */
    private static final class ModuleSlices implements SliceAssignment {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            for (String segment : javaClass.getPackageName().split("\\.")) {
                if (MODULES.contains(segment)) {
                    return SliceIdentifier.of(segment);
                }
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "modules " + MODULES;
        }
    }
}
