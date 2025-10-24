package area.server.AREA_Back.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation annotation to ensure at least one identifier is provided.
 * Used on LocalLoginRequest to ensure either email or username is present.
 */
@Documented
@Constraint(validatedBy = AtLeastOneIdentifierValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AtLeastOneIdentifier {

    String message() default "Either email or username must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
