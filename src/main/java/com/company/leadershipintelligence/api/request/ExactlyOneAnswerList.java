package com.company.leadershipintelligence.api.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A calculate request carries its answers either as {@code inputs} or as {@code responses}, with at least one answer and
 * never both (decision D45). The violation is reported on {@code inputs}, the field the earlier contract reported when no
 * answers were sent, so that error shape does not change.
 */
@Documented
@Constraint(validatedBy = ExactlyOneAnswerList.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExactlyOneAnswerList {

    String message() default "send either inputs or responses, with at least one answer, and not both";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ExactlyOneAnswerList, CalculateRequest> {

        @Override
        public boolean isValid(CalculateRequest request, ConstraintValidatorContext context) {
            if (request == null) {
                return true;
            }
            boolean hasInputs = request.inputs() != null && !request.inputs().isEmpty();
            boolean hasResponses = request.responses() != null && !request.responses().isEmpty();
            if (hasInputs != hasResponses) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("inputs").addConstraintViolation();
            return false;
        }
    }
}
