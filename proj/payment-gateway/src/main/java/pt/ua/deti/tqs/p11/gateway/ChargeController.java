package pt.ua.deti.tqs.p11.gateway;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /charges: 201 when approved, 402 when declined. A tok_timeout charge answers only after the
 * configured delay. GET /health for the container healthcheck.
 */
@RestController
class ChargeController {

  private final ChargeBook book;
  private final GatewayProperties properties;

  ChargeController(ChargeBook book, GatewayProperties properties) {
    this.book = book;
    this.properties = properties;
  }

  @PostMapping("/charges")
  ResponseEntity<Map<String, Object>> charge(@Valid @RequestBody ChargeRequest request)
      throws InterruptedException {
    Charge charge = book.charge(request);
    if (charge.late()) {
      Thread.sleep(properties.timeoutDelay());
    }
    HttpStatus status =
        charge.status() == ChargeStatus.APPROVED ? HttpStatus.CREATED : HttpStatus.PAYMENT_REQUIRED;
    return ResponseEntity.status(status).body(body(charge));
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @ExceptionHandler(IdempotencyKeyReusedException.class)
  ProblemDetail keyReused(IdempotencyKeyReusedException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "The charge has invalid fields");
  }

  /** Amounts travel as strings ("9.00"), so no client turns them into a floating point number. */
  private static Map<String, Object> body(Charge charge) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", charge.id());
    body.put("status", charge.status().name());
    body.put("amount", charge.amount().setScale(2).toPlainString());
    body.put("currency", charge.currency());
    if (charge.reason() != null) {
      body.put("reason", charge.reason());
    }
    return body;
  }
}
