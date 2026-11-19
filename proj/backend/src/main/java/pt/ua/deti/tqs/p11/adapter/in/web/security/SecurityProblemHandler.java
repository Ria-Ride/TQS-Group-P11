package pt.ua.deti.tqs.p11.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands Spring Security's 401 and 403 to the MVC exception handling, so ApiExceptionHandler builds
 * them like every other API error. Security rejects a request before any controller runs, so
 * without this the advice would never see these exceptions.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver resolver;

  SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  /** No token, or an invalid one: 401. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e) {
    resolver.resolveException(request, response, null, e);
  }

  /** Valid token, but the role may not call this endpoint: 403. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) {
    resolver.resolveException(request, response, null, e);
  }
}
