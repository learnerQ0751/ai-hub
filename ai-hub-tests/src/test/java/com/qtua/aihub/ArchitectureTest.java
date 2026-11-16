package com.qtua.aihub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 模块与分层边界（REQ-5.1 增补数据面/控制面规则）。
 */
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
                .layer("Gateway").definedBy("com.qtua.aihub.gateway..")
                .layer("Console").definedBy("com.qtua.aihub.console..")
                .layer("Start").definedBy("com.qtua.aihub.start..")
                .layer("Service").definedBy("com.qtua.aihub.service..")
                .layer("Core").definedBy("com.qtua.aihub.core..")
                .layer("Manager").definedBy("com.qtua.aihub.manager..")
                .layer("Integration").definedBy("com.qtua.aihub.integration..")
                .layer("Api").definedBy("com.qtua.aihub.api..")
                .layer("Dal").definedBy("com.qtua.aihub.dal..")
                .layer("Common").definedBy("com.qtua.aihub.common..")
                .whereLayer("Gateway").mayNotBeAccessedByAnyLayer()
                .whereLayer("Console").mayNotBeAccessedByAnyLayer()
                .whereLayer("Start").mayOnlyBeAccessedByLayers("Gateway", "Console")
                .whereLayer("Service").mayOnlyBeAccessedByLayers(
                        "Start", "Gateway", "Console")
                .whereLayer("Core").mayOnlyBeAccessedByLayers(
                        "Service", "Gateway", "Console")
                .whereLayer("Manager").mayOnlyBeAccessedByLayers(
                        "Core", "Service", "Gateway", "Console")
                // service/gateway 承担网关治理（Redis 限流/预算），允许 Service 访问 Integration。
                .whereLayer("Integration").mayOnlyBeAccessedByLayers(
                        "Core", "Service", "Gateway", "Console")
                .whereLayer("Api").mayOnlyBeAccessedByLayers(
                        "Start", "Service", "Gateway", "Console")
                .whereLayer("Dal").mayOnlyBeAccessedByLayers("Manager")
                .whereLayer("Common").mayOnlyBeAccessedByLayers(
                        "Gateway", "Console", "Start", "Service", "Core", "Manager",
                        "Integration", "Api", "Dal")
                .check(classes);
    }

    @Test
    void gateway_does_not_depend_on_control_plane() {
        noClasses().that().resideInAPackage("com.qtua.aihub.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.qtua.aihub.console..",
                        "com.qtua.aihub.service.admin..")
                .check(classes);
    }

    @Test
    void console_does_not_depend_on_chat_runtime_core() {
        noClasses().that().resideInAPackage("com.qtua.aihub.console..")
                .should().dependOnClassesThat().haveNameMatching(
                        ".*(ChatCompletionCore|RagRetrievalCore|RagAnswerPrompt|RagPromptBuilder"
                                + "|RagLexicalReranker|McpToolAggregator)")
                .check(classes);
        noClasses().that().resideInAPackage("com.qtua.aihub.console..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.qtua.aihub.core.routing..",
                        "com.qtua.aihub.service.gateway..")
                .check(classes);
    }
}
