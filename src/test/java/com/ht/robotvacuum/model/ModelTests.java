package com.ht.robotvacuum.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModelTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void taskRejectsNonPositiveDuration(long duration) {
        Task task = validTask();
        task.setDuration(duration);
        assertEquals(Set.of("duration"), invalidProperties(task));
    }

    @Test
    void requiredFieldsRejectMissingAndBlankValues() {
        User user = new User();
        user.setName(" ");
        assertEquals(Set.of("name", "username", "password"), invalidProperties(user));
        assertEquals(Set.of("name", "pairCode", "user"), invalidProperties(new Robot()));
        assertEquals(Set.of("name", "startAt", "duration", "status", "robot"),
                invalidProperties(new Task()));
        assertEquals(Set.of("type", "message", "robot"), invalidProperties(new Alert()));
    }

    @Test
    void completeEntitiesAreValidAndUnreadAlertIsAllowed() {
        User user = new User();
        user.setName("Owner");
        user.setUsername("owner");
        user.setPassword("password-hash");
        Robot robot = new Robot();
        robot.setName("Vacuum");
        robot.setPairCode("PAIR-123");
        robot.setUser(user);
        Task task = validTask();
        task.setRobot(robot);
        task.setDuration(3_000_000_000L);
        Alert alert = new Alert();
        alert.setType("LOW_BATTERY");
        alert.setMessage("Battery is low");
        alert.setRobot(robot);

        assertTrue(validator.validate(user).isEmpty());
        assertTrue(validator.validate(robot).isEmpty());
        assertTrue(validator.validate(task).isEmpty());
        assertTrue(validator.validate(alert).isEmpty());
        assertNull(alert.getReadAt());
    }

    @Test
    void creationTimestampsAreInitializedAndExplicitValuesArePreserved() {
        Task task = new Task();
        Alert alert = new Alert();
        task.initializeCreatedAt();
        alert.initializeCreatedAt();
        assertNotNull(task.getCreatedAt());
        assertNotNull(alert.getCreatedAt());

        Instant supplied = Instant.parse("2026-10-20T01:00:00Z");
        task.setCreatedAt(supplied);
        alert.setCreatedAt(supplied);
        task.initializeCreatedAt();
        alert.initializeCreatedAt();
        assertEquals(supplied, task.getCreatedAt());
        assertEquals(supplied, alert.getCreatedAt());
    }

    private static Task validTask() {
        Task task = new Task();
        task.setName("Morning cleaning");
        task.setStartAt(Instant.parse("2026-10-20T01:00:00Z"));
        task.setDuration(1800L);
        task.setStatus("PENDING");
        task.setRobot(new Robot());
        return task;
    }

    private static Set<String> invalidProperties(Object entity) {
        return validator.validate(entity).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
