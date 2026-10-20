package com.ht.robotvacuum.service;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@RequiredArgsConstructor
class ServiceValidation {

    private final Validator validator;

    void validate(Object entity) {
        var violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    void requireNewId(long id) {
        Assert.isTrue(id == 0, "A new entity must have id 0");
    }

    void requirePositiveId(long id, String field) {
        Assert.isTrue(id > 0, field + " must be positive");
    }
}
