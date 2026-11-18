package pt.ua.deti.tqs.p11.adapter.in.web;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.auth.InvalidCredentialsException;
import pt.ua.deti.tqs.p11.application.user.UserNotFoundException;

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
}
