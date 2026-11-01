package com.company.leadershipintelligence;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Layering rules (decision D31): api -> service -> domain, service -> repository -> domain.
 * The domain depends on no other layer of this application and on no framework.
 */
class ArchitectureTest {

    private static final String BASE = "com.company.leadershipintelligence";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void applicationClassesAreImported() {
        org.junit.jupiter.api.Assertions.assertFalse(classes.isEmpty(), "no production classes imported");
    }

    @Test
    void layersOnlyDependDownward() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Api").definedBy(BASE + ".api..")
                .layer("Service").definedBy(BASE + ".service..")
                .layer("Domain").definedBy(BASE + ".domain..")
                .layer("Repository").definedBy(BASE + ".repository..")
                .whereLayer("Api").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Api")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Service", "Repository")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses().that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "org.postgresql..",
                        "software.amazon..",
                        "redis..",
                        "io.lettuce..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnOtherLayers() {
        noClasses().that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".api..",
                        BASE + ".service..",
                        BASE + ".repository..",
                        BASE + ".config..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void apiDoesNotUseRepositoryOrPersistenceTypes() {
        noClasses().that().resideInAPackage(BASE + ".api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".repository..", "jakarta.persistence..", "org.hibernate..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void serviceDoesNotUseApi() {
        noClasses().that().resideInAPackage(BASE + ".service..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".api..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void explanationOnlyArrangesFactsAndCallsNoScoringService() {
        String scoring = BASE + ".domain.service.";
        noClasses().that().resideInAPackage(scoring + "explain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        scoring + "normalization..", scoring + "aggregation..", scoring + "coverage..",
                        scoring + "patterns..", scoring + "penalties..", scoring + "overall..",
                        scoring + "imbalance..", scoring + "interpretation..", scoring + "signals..",
                        BASE + ".domain.strategy..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void persistenceEntitiesStayInRepositoryLayer() {
        noClasses().that().resideOutsideOfPackage(BASE + ".repository..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
