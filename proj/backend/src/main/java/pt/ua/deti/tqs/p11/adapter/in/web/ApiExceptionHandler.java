package pt.ua.deti.tqs.p11.adapter.in.web;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.auth.InvalidCredentialsException;
import pt.ua.deti.tqs.p11.application.booking.DepositDeclinedException;
import pt.ua.deti.tqs.p11.application.booking.DepositTimedOutException;
import pt.ua.deti.tqs.p11.application.fleet.HubCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.HubNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleHasOpenTicketException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException;
import pt.ua.deti.tqs.p11.application.maintenance.FaultAlreadyCoveredException;
import pt.ua.deti.tqs.p11.application.maintenance.TicketNotFoundException;
import pt.ua.deti.tqs.p11.application.maintenance.VehicleRetiredException;
import pt.ua.deti.tqs.p11.application.pass.AccommodationNameAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.pass.PassTypeCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.pass.PassTypeNotFoundException;
import pt.ua.deti.tqs.p11.application.pass.PaymentDeclinedException;
import pt.ua.deti.tqs.p11.application.pass.PaymentTimedOutException;
import pt.ua.deti.tqs.p11.application.pass.UnknownAccommodationException;
import pt.ua.deti.tqs.p11.application.pass.UnknownPassTypeException;
import pt.ua.deti.tqs.p11.application.pass.UnknownStayCodeException;
import pt.ua.deti.tqs.p11.application.rules.RulesVersionConflictException;
import pt.ua.deti.tqs.p11.application.user.UserNotFoundException;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException;
import pt.ua.deti.tqs.p11.domain.fleet.CapacityBelowVehiclesHeldException;
import pt.ua.deti.tqs.p11.domain.fleet.ForbiddenTransitionException;
import pt.ua.deti.tqs.p11.domain.fleet.HubFullException;
import pt.ua.deti.tqs.p11.domain.maintenance.ForbiddenTicketTransitionException;
import pt.ua.deti.tqs.p11.domain.maintenance.NotAFieldTechnicianException;
import pt.ua.deti.tqs.p11.domain.pass.StayCodeLinkedToAnotherAccountException;
import pt.ua.deti.tqs.p11.domain.pass.StayCodeNotValidTodayException;
import pt.ua.deti.tqs.p11.domain.user.FieldTechnicianRequiresOperatorException;
import pt.ua.deti.tqs.p11.domain.user.ProtectedAccountException;

/**
 * Maps exceptions to HTTP responses in one place (RFC 9457 problem details). Every error carries an
 * "errors" map of field name to message, so clients can show it next to the field.
 */
@RestControllerAdvice
class ApiExceptionHandler {

  private static final String INVALID_FIELDS = "The request has invalid fields";
  private static final String STAY_CODE = "stayCode";
  private static final String PAYMENT = "payment";
  private static final String DEPOSIT = "deposit";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
    Map<String, String> errors = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return problem(HttpStatus.BAD_REQUEST, INVALID_FIELDS, errors);
  }

  /** A field that is wrong because of another one (e.g. hubId and the transition action). */
  @ExceptionHandler(InvalidFieldException.class)
  ProblemDetail invalidField(InvalidFieldException e) {
    return problem(HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of(e.field(), e.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyInUseException.class)
  ProblemDetail emailAlreadyInUse(EmailAlreadyInUseException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("email", "is already in use"));
  }

  // US14: the request is valid but conflicts with what exists (409), on its field.
  @ExceptionHandler(PassTypeCodeAlreadyInUseException.class)
  ProblemDetail passTypeCodeInUse(PassTypeCodeAlreadyInUseException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("code", "is already in use"));
  }

  @ExceptionHandler(AccommodationNameAlreadyInUseException.class)
  ProblemDetail accommodationNameInUse(AccommodationNameAlreadyInUseException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("name", "is already in use"));
  }

  /** BR21: two managers saved the rules at the same moment; the second must read them again. */
  @ExceptionHandler(RulesVersionConflictException.class)
  ProblemDetail rulesVersionConflict(RulesVersionConflictException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of());
  }

  @ExceptionHandler(UnknownAccommodationException.class)
  ProblemDetail unknownAccommodation(UnknownAccommodationException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        INVALID_FIELDS,
        Map.of("accommodationId", "is not a partner hotel"));
  }

  private static ProblemDetail problem(
      HttpStatus status, String detail, Map<String, String> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail invalidCredentials(InvalidCredentialsException e) {
    return problem(HttpStatus.UNAUTHORIZED, e.getMessage(), Map.of());
  }

  @ExceptionHandler({
    UserNotFoundException.class,
    HubNotFoundException.class,
    VehicleNotFoundException.class,
    TicketNotFoundException.class,
    PassTypeNotFoundException.class
  })
  ProblemDetail notFound(RuntimeException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), Map.of());
  }

  /**
   * Missing, invalid or expired token (US13). One detail for every case, so the client is not told
   * why the token was refused. The Bearer challenge follows RFC 6750.
   */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(problem(HttpStatus.UNAUTHORIZED, "A valid access token is required", Map.of()));
  }

  /** Valid token, but not allowed by the access matrix or the ownership policy (BR23). */
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail accessDenied(AccessDeniedException e) {
    return problem(
        HttpStatus.FORBIDDEN, "You do not have permission to access this resource", Map.of());
  }

  /** Admin accounts are managed in the database (US13): the request conflicts with that. */
  @ExceptionHandler(ProtectedAccountException.class)
  ProblemDetail protectedAccount(ProtectedAccountException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of());
  }

  /** BR20, reported on the field like the other validation errors. */
  @ExceptionHandler(FieldTechnicianRequiresOperatorException.class)
  ProblemDetail fieldTechnicianRequiresOperator(FieldTechnicianRequiresOperatorException e) {
    return problem(
        HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of("fieldTechnician", e.getMessage()));
  }

  // US08: the request is valid but conflicts with the state of the fleet (409), on its field.

  @ExceptionHandler({HubCodeAlreadyInUseException.class, VehicleCodeAlreadyInUseException.class})
  ProblemDetail codeAlreadyInUse(RuntimeException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("code", "is already in use"));
  }

  /** BR14: registering a vehicle at, or moving it to, the hub given in hubId. */
  @ExceptionHandler(HubFullException.class)
  ProblemDetail hubFull(HubFullException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("hubId", e.getMessage()));
  }

  /** BR14. */
  @ExceptionHandler(CapacityBelowVehiclesHeldException.class)
  ProblemDetail capacityBelowVehiclesHeld(CapacityBelowVehiclesHeldException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("capacity", e.getMessage()));
  }

  // US03: a refused purchase. Nothing is charged for a field error; no pass without payment.

  @ExceptionHandler(UnknownPassTypeException.class)
  ProblemDetail unknownPassType(UnknownPassTypeException e) {
    return problem(HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of("passTypeId", "is unknown"));
  }

  /** BR9. The tourist can buy again without the code, at the base price. */
  @ExceptionHandler(UnknownStayCodeException.class)
  ProblemDetail unknownStayCode(UnknownStayCodeException e) {
    return problem(HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of(STAY_CODE, "is unknown"));
  }

  /** BR9: before check-in or after check-out, on the hotel's calendar. */
  @ExceptionHandler(StayCodeNotValidTodayException.class)
  ProblemDetail stayCodeNotValidToday(StayCodeNotValidTodayException e) {
    return problem(HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of(STAY_CODE, "is not valid today"));
  }

  /** BR9. Which account it is linked to is not said. */
  @ExceptionHandler(StayCodeLinkedToAnotherAccountException.class)
  ProblemDetail stayCodeOfAnotherAccount(StayCodeLinkedToAnotherAccountException e) {
    return problem(
        HttpStatus.BAD_REQUEST, INVALID_FIELDS, Map.of(STAY_CODE, "is linked to another account"));
  }

  /** BR10: declined, so no pass. The gateway's reason is not shown to the client. */
  @ExceptionHandler(PaymentDeclinedException.class)
  ProblemDetail paymentDeclined(PaymentDeclinedException e) {
    return problem(
        HttpStatus.PAYMENT_REQUIRED, "The payment was declined", Map.of(PAYMENT, "was declined"));
  }

  /** BR10: no answer in time, so no pass. Trying again is safe (the gateway is idempotent). */
  @ExceptionHandler(PaymentTimedOutException.class)
  ProblemDetail paymentTimedOut(PaymentTimedOutException e) {
    return problem(
        HttpStatus.GATEWAY_TIMEOUT,
        "The payment got no answer in time",
        Map.of(PAYMENT, "got no answer in time; try again"));
  }

  /** BR16: the action is not allowed in the vehicle's current state, or not yet. */
  @ExceptionHandler({ForbiddenTransitionException.class, VehicleHasOpenTicketException.class})
  ProblemDetail transitionRefused(RuntimeException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("action", e.getMessage()));
  }

  // US04: a refused booking. One status and one field per rule, so the client shows it in place.

  /** How one refused rule is told to the client. */
  private record Refusal(HttpStatus status, String field, String message) {}

  /**
   * BR1 to BR6. A field the tourist can correct is 400; a conflict with the state of the fleet, the
   * Pass or the tourist's bookings is 409. A switch with no default, so a new rule does not compile
   * until it is mapped here.
   */
  @ExceptionHandler(BookingRefusedException.class)
  ProblemDetail bookingRefused(BookingRefusedException e) {
    Refusal refusal =
        switch (e.reason()) {
          case DURATION_NOT_ALLOWED ->
              new Refusal(
                  HttpStatus.BAD_REQUEST,
                  "plannedEnd",
                  "must be after now and within the longest booking allowed");
          case RIDE_ENDS_AFTER_PASS ->
              new Refusal(HttpStatus.BAD_REQUEST, "plannedEnd", "is after the end of your pass");
          case VEHICLE_NOT_AVAILABLE ->
              new Refusal(HttpStatus.CONFLICT, "vehicleId", "is not available");
          case NOT_ENOUGH_BATTERY ->
              new Refusal(HttpStatus.CONFLICT, "vehicleId", "has not enough battery for this ride");
          case NO_VALID_PASS -> new Refusal(HttpStatus.CONFLICT, "pass", "you have no valid pass");
          case PASS_DOES_NOT_ALLOW_TYPE ->
              new Refusal(
                  HttpStatus.CONFLICT, "pass", "your pass does not allow this vehicle type");
          case OVERLAPPING_BOOKING ->
              new Refusal(
                  HttpStatus.CONFLICT, "booking", "you already have a booking in that period");
        };
    String detail =
        refusal.status() == HttpStatus.BAD_REQUEST ? INVALID_FIELDS : "The booking is refused";
    return problem(refusal.status(), detail, Map.of(refusal.field(), refusal.message()));
  }

  /** BR11: not held, so no booking. The gateway's reason is not shown to the client. */
  @ExceptionHandler(DepositDeclinedException.class)
  ProblemDetail depositDeclined(DepositDeclinedException e) {
    return problem(
        HttpStatus.PAYMENT_REQUIRED, "The deposit was declined", Map.of(DEPOSIT, "was declined"));
  }

  /** BR11: no answer in time, so no booking; the deposit was released. Trying again is safe. */
  @ExceptionHandler(DepositTimedOutException.class)
  ProblemDetail depositTimedOut(DepositTimedOutException e) {
    return problem(
        HttpStatus.GATEWAY_TIMEOUT,
        "The deposit got no answer in time",
        Map.of(DEPOSIT, "got no answer in time; try again"));
  }

  // US10: maintenance tickets.

  /** BR20: the fault is covered; ticketId is the open ticket, so the operator can go to it. */
  @ExceptionHandler(FaultAlreadyCoveredException.class)
  ProblemDetail faultAlreadyCovered(FaultAlreadyCoveredException e) {
    ProblemDetail problem =
        problem(
            HttpStatus.CONFLICT,
            e.getMessage(),
            Map.of("faultCode", "is already covered by an open ticket"));
    e.existingTicketId().ifPresent(id -> problem.setProperty("ticketId", id));
    return problem;
  }

  @ExceptionHandler(VehicleRetiredException.class)
  ProblemDetail vehicleRetired(VehicleRetiredException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("vehicleId", "is retired"));
  }

  /** BR20. Whether the account exists is not said. */
  @ExceptionHandler(NotAFieldTechnicianException.class)
  ProblemDetail notAFieldTechnician(NotAFieldTechnicianException e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        INVALID_FIELDS,
        Map.of("technicianId", "is not an active field technician"));
  }

  /** The ticket's state no longer allows the change (e.g. the work has started). */
  @ExceptionHandler(ForbiddenTicketTransitionException.class)
  ProblemDetail ticketTransitionRefused(ForbiddenTicketTransitionException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("state", e.getMessage()));
  }
}
