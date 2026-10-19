package com.qtua.aihub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.qtua.aihub");

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
