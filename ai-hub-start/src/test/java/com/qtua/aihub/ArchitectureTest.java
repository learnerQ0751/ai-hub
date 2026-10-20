package com.qtua.aihub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

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
}
