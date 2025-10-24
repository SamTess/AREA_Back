package area.server.AREA_Back.validation;

import area.server.AREA_Back.dto.LocalLoginRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for {@link AtLeastOneIdentifier} annotation.
 * Ensures that at least one of email or username is provided and not blank.
 */
public class AtLeastOneIdentifierValidator implements ConstraintValidator<AtLeastOneIdentifier, LocalLoginRequest> {

    @Override
    public void initialize(AtLeastOneIdentifier constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(LocalLoginRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return false;
        }

        boolean hasEmail = request.getEmail() != null && !request.getEmail().trim().isEmpty();
        boolean hasUsername = request.getUsername() != null && !request.getUsername().trim().isEmpty();

        return hasEmail || hasUsername;
    }
}
