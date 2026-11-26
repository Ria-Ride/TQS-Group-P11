package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.pass.PartnerAccommodationRepository;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/**
 * Partner hotels and stays made by the client manager (US14, BR9), against a real PostgreSQL. A
 * test removes what it made (names starting with IT HOTEL, codes with IT-), so the seeded demo
 * hotel and stays are the only ones left for the rest of the suite.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StayManagementPersistenceIT {

  private static final UUID HOTEL_ID = UUID.fromString("5a7e0000-0000-4000-8000-0000000000b1");
  private static final UUID STAY_ID = UUID.fromString("57a90000-0000-4000-8000-0000000000b1");
  private static final LocalDate IN = LocalDate.of(2026, 12, 1);
  private static final LocalDate OUT = LocalDate.of(2026, 12, 5);

  @Autowired private PartnerAccommodationRepository accommodations;
  @Autowired private StayRepository stays;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void removeWhatThisTestMade() {
    jdbc.update("DELETE FROM stays WHERE code LIKE 'IT-%'");
    jdbc.update("DELETE FROM partner_accommodations WHERE name LIKE 'IT HOTEL%'");
  }

  private static PartnerAccommodation hotel() {
    return new PartnerAccommodation(HOTEL_ID, "IT HOTEL Laguna", 15);
  }

  // ------------------------------------------------------------- hotels

  @Test
  void saveHotel_isStoredWithItsDiscountAndFound() {
    PartnerAccommodation saved = accommodations.save(hotel());

    assertThat(saved).isEqualTo(hotel());
    assertThat(accommodations.findById(HOTEL_ID)).contains(hotel());
    assertThat(accommodations.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void existsByName_isTrueOnlyForStoredNames() {
    accommodations.save(hotel());

    assertThat(accommodations.existsByName("IT HOTEL Laguna")).isTrue();
    assertThat(accommodations.existsByName("Hotel Ria Azul")).isTrue();
    assertThat(accommodations.existsByName("IT HOTEL Nope")).isFalse();
  }

  @Test
  void findAllHotels_isOrderedByName_andIncludesTheSeededOne() {
    accommodations.save(hotel());

    assertThat(accommodations.findAll())
        .extracting(PartnerAccommodation::name)
        .containsSubsequence("Hotel Ria Azul", "IT HOTEL Laguna");
  }

  @Test
  void database_refusesASecondHotelWithTheSameName() {
    accommodations.save(hotel());

    assertThatThrownBy(() ->
            accommodations.save(new PartnerAccommodation(UUID.randomUUID(), "IT HOTEL Laguna", 5)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ------------------------------------------------------------- stays

  @Test
  void addStay_isStoredUpperCaseAndNotRedeemed() {
    accommodations.save(hotel());

    stays.add(new Stay(STAY_ID, "it-7k3m", hotel(), IN, OUT, null));

    Stay stored = stays.findByCode("IT-7K3M").orElseThrow();
    assertThat(stored.id()).isEqualTo(STAY_ID);
    assertThat(stored.accommodation()).isEqualTo(hotel());
    assertThat(stored.checkIn()).isEqualTo(IN);
    assertThat(stored.checkOut()).isEqualTo(OUT);
    assertThat(stored.redeemedBy()).isEmpty();
  }

  @Test
  void existsByCode_isTrueOnlyForStoredCodes_whateverTheCase() {
    accommodations.save(hotel());
    stays.add(new Stay(STAY_ID, "IT-7K3M", hotel(), IN, OUT, null));

    assertThat(stays.existsByCode("IT-7K3M")).isTrue();
    assertThat(stays.existsByCode("RIA-DEMO-01")).isTrue();
    assertThat(stays.existsByCode("IT-NOPE")).isFalse();
  }

  @Test
  void findAllStays_isNewestCheckInFirst() {
    accommodations.save(hotel());
    stays.add(
        new Stay(
            STAY_ID, "IT-LATE", hotel(), LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 3), null));

    assertThat(stays.findAll()).first().extracting(Stay::code).isEqualTo("IT-LATE");
    assertThat(stays.findAll()).extracting(Stay::code).contains("RIA-DEMO-01");
  }

  @Test
  void addStay_codeAlreadyStored_isRefused() {
    accommodations.save(hotel());
    stays.add(new Stay(STAY_ID, "IT-7K3M", hotel(), IN, OUT, null));

    assertThatThrownBy(
            () -> stays.add(new Stay(UUID.randomUUID(), "IT-7K3M", hotel(), IN, OUT, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
