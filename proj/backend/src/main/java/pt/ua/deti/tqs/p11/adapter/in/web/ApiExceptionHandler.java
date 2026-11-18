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
import pt.ua.deti.tqs.p11.application.user.UserNotFoundException;
import pt.ua.deti.tqs.p11.domain.user.FieldTechnicianRequiresOperatorException;
import pt.ua.deti.tqs.p11.domain.user.ProtectedAccountException;

/**
 * Maps exceptions to HTTP responses in one place (RFC 9457 problem details). Every error carries an
 * "errors" map of field name to message, so clients can show it next to the field.
 */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
    Map<String, String> errors = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return problem(HttpStatus.BAD_REQUEST, "The request has invalid fields", errors);
  }

  @ExceptionHandler(EmailAlreadyInUseException.class)
  ProblemDetail emailAlreadyInUse(EmailAlreadyInUseException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), Map.of("email", "is already in use"));
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

  @ExceptionHandler(UserNotFoundException.class)
  ProblemDetail userNotFound(UserNotFoundException e) {
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
        HttpStatus.BAD_REQUEST,
        "The request has invalid fields",
        Map.of("fieldTechnician", e.getMessage()));
  }
}
