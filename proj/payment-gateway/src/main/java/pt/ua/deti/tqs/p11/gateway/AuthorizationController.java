package pt.ua.deti.tqs.p11.gateway;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deposits. POST /authorizations: 201 when the amount is held, 402 when declined; a tok_timeout
 * authorization holds the amount but answers only after the configured delay. POST
 * /authorizations/{key}/capture and /release: 200; 404 for an unknown key, 409 when the step does
 * not fit the deposit, 400 for a capture above the amount held.
 */
@RestController
@RequestMapping("/authorizations")
class AuthorizationController {

  private final AuthorizationBook book;
  private final GatewayProperties properties;

  AuthorizationController(AuthorizationBook book, GatewayProperties properties) {
    this.book = book;
    this.properties = properties;
  }

  @PostMapping
  ResponseEntity<Map<String, Object>> authorize(@Valid @RequestBody AuthorizationRequest request)
      throws InterruptedException {
    Authorization authorization = book.authorize(request);
    if (authorization.late()) {
      Thread.sleep(properties.timeoutDelay());
    }
    HttpStatus status =
        authorization.status() == AuthorizationStatus.DECLINED
            ? HttpStatus.PAYMENT_REQUIRED
            : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(body(authorization));
  }

  @PostMapping("/{key}/capture")
  Map<String, Object> capture(@PathVariable String key, @Valid @RequestBody CaptureRequest request) {
    return body(book.capture(key, request.amount()));
  }

  @PostMapping("/{key}/release")
  Map<String, Object> release(@PathVariable String key) {
    return body(book.release(key));
  }

  @ExceptionHandler(AuthorizationNotFoundException.class)
  ProblemDetail notFound(AuthorizationNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({IdempotencyKeyReusedException.class, AuthorizationStateConflictException.class})
  ProblemDetail conflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(CaptureExceedsAuthorizationException.class)
  ProblemDetail tooMuch(CaptureExceedsAuthorizationException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "The request has invalid fields");
  }

  /** Amounts travel as strings ("30.00"), so no client turns them into a floating point number. */
  private static Map<String, Object> body(Authorization authorization) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", authorization.id());
    body.put("status", authorization.status().name());
    body.put("amount", money(authorization.amount()));
    body.put("currency", authorization.currency());
    if (authorization.status() == AuthorizationStatus.CAPTURED) {
      body.put("captured", money(authorization.captured()));
    }
    if (authorization.status() == AuthorizationStatus.CAPTURED
        || authorization.status() == AuthorizationStatus.RELEASED) {
      body.put("released", money(authorization.released()));
    }
    if (authorization.reason() != null) {
      body.put("reason", authorization.reason());
    }
    return body;
  }

  private static String money(java.math.BigDecimal amount) {
    return amount.setScale(2).toPlainString();
  }
}