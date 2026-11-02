package com.qtua.aihub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.qtua.aihub");

    @Test
    void packages_are_free_of_cycles() {
        slices()
                .matching("com.qtua.aihub.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void layer_dependencies_follow_direction() {
        layeredArchitecture()
                .consideringAllDependencies()
                .withOptionalLayers(true)
                .layer("Start").definedBy("com.qtua.aihub.start..")
                .layer("Service").definedBy("com.qtua.aihub.service..")
                .layer("Core").definedBy("com.qtua.aihub.core..")
                .layer("Manager").definedBy("com.qtua.aihub.manager..")
                .layer("Integration").definedBy("com.qtua.aihub.integration..")
                .layer("Api").definedBy("com.qtua.aihub.api..")
                .layer("Dal").definedBy("com.qtua.aihub.dal..")
                .layer("Common").definedBy("com.qtua.aihub.common..")
                .whereLayer("Start").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Start")
                .whereLayer("Core").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Manager").mayOnlyBeAccessedByLayers("Core", "Service")
                // service/gateway 承担网关治理（Redis 限流/预算），允许 Service 访问 Integration。
                .whereLayer("Integration").mayOnlyBeAccessedByLayers("Core", "Service")
                .whereLayer("Api").mayOnlyBeAccessedByLayers("Start", "Service")
                .whereLayer("Dal").mayOnlyBeAccessedByLayers("Manager")
                .whereLayer("Common").mayOnlyBeAccessedByLayers(
                        "Start", "Service", "Core", "Manager", "Integration", "Api", "Dal")
                .check(classes);
    }
}
