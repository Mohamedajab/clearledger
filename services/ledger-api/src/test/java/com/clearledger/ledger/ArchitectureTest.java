package com.clearledger.ledger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;

@AnalyzeClasses(packages = "com.clearledger.ledger", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule domain_is_independent_of_http =
        noClasses().that().resideInAnyPackage(
                "com.clearledger.ledger.account..",
                "com.clearledger.ledger.ledger..",
                "com.clearledger.ledger.payment..",
                "com.clearledger.ledger.outbox..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule repositories_do_not_leak_into_configuration =
        noClasses().that().resideInAPackage("..config..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
