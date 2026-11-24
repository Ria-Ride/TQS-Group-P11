package pt.ua.deti.tqs.p11;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Hexagonal architecture: dependencies point inwards (adapter, application, domain). */
@AnalyzeClasses(
    packages = "pt.ua.deti.tqs.p11",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_knows_no_framework_and_nothing_outside =
      noClasses()
          .that()
          .resideInAPackage("..p11.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "jakarta..", "..p11.application..", "..p11.adapter..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_knows_no_adapter =
      noClasses()
          .that()
          .resideInAPackage("..p11.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..p11.adapter..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule feature_modules_form_no_cycles =
      slices()
          .matching("pt.ua.deti.tqs.p11.domain.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true);

  /**
   * K1: the simulator can be replaced by changing only its adapter. The core (domain and
   * application) knows no MQTT client and no JSON library, whatever simulator or format comes next.
   */
  @ArchTest
  static final ArchRule core_knows_no_mqtt_client_and_no_json_library =
      noClasses()
          .that()
          .resideInAnyPackage("..p11.domain..", "..p11.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.hivemq..", "tools.jackson..", "com.fasterxml.jackson..")
          .allowEmptyShould(true);
}
