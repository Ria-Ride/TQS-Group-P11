package pt.ua.deti.tqs.p11.adapter.in.web.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** The string, encoded in UTF-8, has at most {@code value} bytes. Null is valid. */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

  int value();

  String message() default "must be at most {value} bytes";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
