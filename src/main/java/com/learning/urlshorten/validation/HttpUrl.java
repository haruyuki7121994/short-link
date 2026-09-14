package com.learning.urlshorten.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {
    String message() default "must be an absolute HTTP(S) URL with a valid host";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
