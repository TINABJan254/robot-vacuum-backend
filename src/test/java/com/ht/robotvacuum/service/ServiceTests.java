package com.ht.robotvacuum.service;

import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceInUseException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.model.enums.Role;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import com.ht.robotvacuum.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceTests {

    private static final Instant TIME = Instant.parse("2026-10-20T01:00:00Z");
    private static ValidatorFactory validatorFactory;
    private UserRepository users;
    private RobotRepository robots;
    private TaskRepository tasks;
    private AlertRepository alerts;
    private UserService userService;
    private RobotService robotService;
    private TaskService taskService;
    private AlertService alertService;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        robots = mock(RobotRepository.class);
        tasks = mock(TaskRepository.class);
        alerts = mock(AlertRepository.class);
        ServiceValidation validation = new ServiceValidation(validatorFactory.getValidator());
        userService = new UserService(users, robots, validation);
        robotService = new RobotService(robots, users, tasks, alerts, validation);
        taskService = new TaskService(tasks, robots, validation);
        alertService = new AlertService(alerts, robots, validation);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(robots.save(any(Robot.class))).thenAnswer(call -> call.getArgument(0));
        when(tasks.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));
        when(alerts.save(any(Alert.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createUserCopiesAllowedFieldsAndPreservesSuppliedPassword() {
        User input = user(0);
        input.setRole(Role.ADMIN);
        input.setRefreshToken("client-supplied-token");
        input.setRefreshTokenExpiresAt(TIME);
        input.getRobots().add(robot(3));
        User created = userService.create(input);
        assertNotSame(input, created);
        assertEquals(input.getName(), created.getName());
        assertEquals(input.getUsername(), created.getUsername());
        assertEquals(input.getPassword(), created.getPassword());
        assertEquals(0, created.getId());
        assertEquals(Role.USER, created.getRole());
        assertNull(created.getRefreshToken());
        assertNull(created.getRefreshTokenExpiresAt());
        assertTrue(created.getRobots().isEmpty());
        assertEquals(1, input.getRobots().size());
    }

    @Test
    void updateUserKeepsPasswordAndRobotsEvenWhenInputContainsReplacements() {
        User existing = user(1);
        existing.setRole(Role.ADMIN);
        existing.setRefreshToken("server-stored-hash");
        existing.setRefreshTokenExpiresAt(TIME);
        existing.getRobots().add(robot(3));
        when(users.findById(1L)).thenReturn(Optional.of(existing));
        User input = user(1);
        input.setName("New name");
        input.setUsername("new-username");
        input.setPassword("must-not-replace-password");
        User updated = userService.update(input);
        assertSame(existing, updated);
        assertEquals("New name", updated.getName());
        assertEquals("new-username", updated.getUsername());
        assertEquals("controller-supplied-hash", updated.getPassword());
        assertEquals(Role.ADMIN, updated.getRole());
        assertEquals("server-stored-hash", updated.getRefreshToken());
        assertEquals(TIME, updated.getRefreshTokenExpiresAt());
        assertEquals(1, updated.getRobots().size());
    }

    @Test
    void updateUserDoesNotRequirePasswordInInput() {
        User existing = user(1);
        when(users.findById(1L)).thenReturn(Optional.of(existing));
        User input = user(1);
        input.setPassword(null);
        assertEquals(existing.getPassword(), userService.update(input).getPassword());
    }

    @Test
    void duplicateUsernameRejectsCreateAndUpdateWithoutChangingExistingUser() {
        when(users.existsByUsername("owner")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> userService.create(user(0)));
        User existing = user(1);
        when(users.findById(1L)).thenReturn(Optional.of(existing));
        when(users.existsByUsernameAndIdNot("owner", 1L)).thenReturn(true);
        User input = user(1);
        input.setName("Rejected change");
        assertThrows(DuplicateResourceException.class, () -> userService.update(input));
        assertEquals("Owner", existing.getName());
        verify(users, never()).save(any());
    }

    @Test
    void unchangedUsernameAndPairCodeAreAllowedOnUpdate() {
        User existingUser = user(1);
        Robot existingRobot = robot(2);
        when(users.findById(1L)).thenReturn(Optional.of(existingUser));
        when(robots.findById(2L)).thenReturn(Optional.of(existingRobot));
        assertSame(existingUser, userService.update(user(1)));
        assertSame(existingRobot, robotService.update(robot(2)));
        verify(users).existsByUsernameAndIdNot("owner", 1L);
        verify(robots).existsByPairCodeAndIdNot("PAIR-123", 2L);
    }

    @Test
    void createRobotResolvesOwnerAndIgnoresChildCollections() {
        User owner = user(1);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        Robot input = robot(0);
        input.getUser().setName("Must not change owner");
        input.getTasks().add(task(8));
        input.getAlerts().add(alert(9));
        Robot created = robotService.create(input);
        assertNotSame(input, created);
        assertSame(owner, created.getUser());
        assertEquals("Owner", owner.getName());
        assertTrue(created.getTasks().isEmpty());
        assertTrue(created.getAlerts().isEmpty());
    }

    @Test
    void updateRobotCanChangeOwnerAndKeepsChildren() {
        Robot existing = robot(2);
        existing.getTasks().add(task(8));
        existing.getAlerts().add(alert(9));
        User newOwner = user(4);
        when(robots.findById(2L)).thenReturn(Optional.of(existing));
        when(users.findById(4L)).thenReturn(Optional.of(newOwner));
        Robot input = robot(2);
        input.setName("New robot name");
        input.setPairCode("PAIR-NEW");
        input.setUser(user(4));
        Robot updated = robotService.update(input);
        assertEquals("New robot name", updated.getName());
        assertEquals("PAIR-NEW", updated.getPairCode());
        assertSame(newOwner, updated.getUser());
        assertEquals(1, updated.getTasks().size());
        assertEquals(1, updated.getAlerts().size());
    }

    @Test
    void duplicatePairCodeRejectsCreateAndUpdate() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        when(robots.existsByPairCode("PAIR-123")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> robotService.create(robot(0)));
        Robot existing = robot(2);
        when(robots.findById(2L)).thenReturn(Optional.of(existing));
        when(robots.existsByPairCodeAndIdNot("PAIR-123", 2L)).thenReturn(true);
        Robot input = robot(2);
        input.setName("Rejected change");
        assertThrows(DuplicateResourceException.class, () -> robotService.update(input));
        assertEquals("Vacuum", existing.getName());
        verify(robots, never()).save(any());
    }

    @Test
    void createTaskResolvesRobotAndIgnoresCreationTimestamp() {
        Robot parent = robot(2);
        when(robots.findById(2L)).thenReturn(Optional.of(parent));
        Task input = task(0);
        input.setCreatedAt(TIME);
        input.setDuration(3_000_000_000L);
        Task created = taskService.create(input);
        assertNotSame(input, created);
        assertSame(parent, created.getRobot());
        assertNull(created.getCreatedAt());
        assertEquals(3_000_000_000L, created.getDuration());
        assertEquals(input.getStartAt(), created.getStartAt());
    }

    @Test
    void updateTaskChangesAllowedFieldsAndPreservesCreationTimestamp() {
        Task existing = task(8);
        existing.setCreatedAt(TIME);
        Robot parent = robot(4);
        when(tasks.findById(8L)).thenReturn(Optional.of(existing));
        when(robots.findById(4L)).thenReturn(Optional.of(parent));
        Task input = task(8);
        input.setName("Updated task");
        input.setStartAt(TIME.plusSeconds(3600));
        input.setDuration(3600);
        input.setStatus("CUSTOM_STATUS");
        input.setRobot(robot(4));
        input.setCreatedAt(TIME.plusSeconds(100));
        Task updated = taskService.update(input);
        assertSame(existing, updated);
        assertEquals("Updated task", updated.getName());
        assertEquals(input.getStartAt(), updated.getStartAt());
        assertEquals(3600, updated.getDuration());
        assertEquals("CUSTOM_STATUS", updated.getStatus());
        assertSame(parent, updated.getRobot());
        assertEquals(TIME, updated.getCreatedAt());
    }

    @Test
    void createAlertStartsUnreadAndIgnoresSuppliedCreationTimestamp() {
        Robot parent = robot(2);
        when(robots.findById(2L)).thenReturn(Optional.of(parent));
        Alert input = alert(0);
        input.setCreatedAt(TIME);
        input.setReadAt(TIME);
        Alert created = alertService.create(input);
        assertNotSame(input, created);
        assertSame(parent, created.getRobot());
        assertEquals(input.getMessage(), created.getMessage());
        assertNull(created.getCreatedAt());
        assertNull(created.getReadAt());
        assertEquals(TIME, input.getReadAt());
    }

    @Test
    void updateAlertAllowsReadAtToBeSetAndClearedWhilePreservingCreatedAt() {
        Alert existing = alert(9);
        existing.setCreatedAt(TIME);
        Robot parent = robot(4);
        when(alerts.findById(9L)).thenReturn(Optional.of(existing));
        when(robots.findById(4L)).thenReturn(Optional.of(parent));
        Alert input = alert(9);
        input.setType("CUSTOM_TYPE");
        input.setMessage("Updated message");
        input.setRobot(robot(4));
        input.setCreatedAt(TIME.plusSeconds(100));
        input.setReadAt(TIME.plusSeconds(60));
        Alert updated = alertService.update(input);
        assertSame(existing, updated);
        assertEquals("CUSTOM_TYPE", updated.getType());
        assertEquals("Updated message", updated.getMessage());
        assertSame(parent, updated.getRobot());
        assertEquals(TIME, updated.getCreatedAt());
        assertEquals(input.getReadAt(), updated.getReadAt());
        input.setReadAt(null);
        assertNull(alertService.update(input).getReadAt());
    }

    @Test
    void getByIdReturnsExistingEntities() {
        User user = user(1);
        Robot robot = robot(2);
        Task task = task(8);
        Alert alert = alert(9);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(robots.findById(2L)).thenReturn(Optional.of(robot));
        when(tasks.findById(8L)).thenReturn(Optional.of(task));
        when(alerts.findById(9L)).thenReturn(Optional.of(alert));
        assertSame(user, userService.getById(1));
        assertSame(robot, robotService.getById(2));
        assertSame(task, taskService.getById(8));
        assertSame(alert, alertService.getById(9));
    }

    @Test
    void getAllAndParentQueriesPreservePagination() {
        var pageable = PageRequest.of(1, 5);
        Page<User> userPage = new PageImpl<>(List.of(user(1)), pageable, 6);
        Page<Robot> robotPage = new PageImpl<>(List.of(robot(2)), pageable, 6);
        Page<Task> taskPage = new PageImpl<>(List.of(task(8)), pageable, 6);
        Page<Alert> alertPage = new PageImpl<>(List.of(alert(9)), pageable, 6);
        when(users.findAll(pageable)).thenReturn(userPage);
        when(robots.findAll(pageable)).thenReturn(robotPage);
        when(tasks.findAll(pageable)).thenReturn(taskPage);
        when(alerts.findAll(pageable)).thenReturn(alertPage);
        when(users.existsById(1L)).thenReturn(true);
        when(robots.findById(2L)).thenReturn(Optional.of(robot(2)));
        when(robots.findByUserId(1, pageable)).thenReturn(robotPage);
        when(tasks.findByRobotId(2, pageable)).thenReturn(taskPage);
        when(alerts.findByRobotId(2, pageable)).thenReturn(alertPage);
        assertSame(userPage, userService.getAll(pageable));
        assertSame(robotPage, robotService.getAll(pageable));
        assertSame(taskPage, taskService.getAll(pageable));
        assertSame(alertPage, alertService.getAll(pageable));
        assertSame(robotPage, robotService.getByUserId(1, pageable));
        assertSame(taskPage, taskService.getByRobotId(2, pageable));
        assertSame(alertPage, alertService.getByRobotId(2, pageable));
    }

    @Test
    void missingEntitiesRejectReadUpdateAndDelete() {
        assertAll(
                () -> assertThrows(ResourceNotFoundException.class, () -> userService.getById(1)),
                () -> assertThrows(ResourceNotFoundException.class, () -> robotService.getById(2)),
                () -> assertThrows(ResourceNotFoundException.class, () -> taskService.getById(8)),
                () -> assertThrows(ResourceNotFoundException.class, () -> alertService.getById(9)),
                () -> assertThrows(ResourceNotFoundException.class, () -> userService.update(user(1))),
                () -> assertThrows(ResourceNotFoundException.class, () -> robotService.update(robot(2))),
                () -> assertThrows(ResourceNotFoundException.class, () -> taskService.update(task(8))),
                () -> assertThrows(ResourceNotFoundException.class, () -> alertService.update(alert(9))),
                () -> assertThrows(ResourceNotFoundException.class, () -> userService.deleteById(1)),
                () -> assertThrows(ResourceNotFoundException.class, () -> robotService.deleteById(2)),
                () -> assertThrows(ResourceNotFoundException.class, () -> taskService.deleteById(8)),
                () -> assertThrows(ResourceNotFoundException.class, () -> alertService.deleteById(9)));
    }

    @Test
    void missingParentsRejectCreateAndFilteredLists() {
        var pageable = PageRequest.of(0, 10);
        assertAll(
                () -> assertThrows(ResourceNotFoundException.class, () -> robotService.create(robot(0))),
                () -> assertThrows(ResourceNotFoundException.class, () -> taskService.create(task(0))),
                () -> assertThrows(ResourceNotFoundException.class, () -> alertService.create(alert(0))),
                () -> assertThrows(ResourceNotFoundException.class, () -> robotService.getByUserId(1, pageable)),
                () -> assertThrows(ResourceNotFoundException.class, () -> taskService.getByRobotId(2, pageable)),
                () -> assertThrows(ResourceNotFoundException.class, () -> alertService.getByRobotId(2, pageable)));
    }

    @Test
    void missingNewParentRejectsUpdateWithoutChangingExistingEntity() {
        Robot existingRobot = robot(2);
        Task existingTask = task(8);
        Alert existingAlert = alert(9);
        when(robots.findById(2L)).thenReturn(Optional.of(existingRobot));
        when(tasks.findById(8L)).thenReturn(Optional.of(existingTask));
        when(alerts.findById(9L)).thenReturn(Optional.of(existingAlert));
        Robot robotInput = robot(2);
        robotInput.setUser(user(99));
        Task taskInput = task(8);
        taskInput.setRobot(robot(99));
        Alert alertInput = alert(9);
        alertInput.setRobot(robot(99));
        assertThrows(ResourceNotFoundException.class, () -> robotService.update(robotInput));
        assertThrows(ResourceNotFoundException.class, () -> taskService.update(taskInput));
        assertThrows(ResourceNotFoundException.class, () -> alertService.update(alertInput));
        assertEquals(1, existingRobot.getUser().getId());
        assertEquals(2, existingTask.getRobot().getId());
        assertEquals(2, existingAlert.getRobot().getId());
    }

    @Test
    void usersAndRobotsWithChildrenCannotBeDeleted() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        when(robots.existsByUserId(1L)).thenReturn(true);
        assertThrows(ResourceInUseException.class, () -> userService.deleteById(1));
        when(robots.findById(2L)).thenReturn(Optional.of(robot(2)));
        when(tasks.existsByRobotId(2L)).thenReturn(true);
        assertThrows(ResourceInUseException.class, () -> robotService.deleteById(2));
        when(tasks.existsByRobotId(2L)).thenReturn(false);
        when(alerts.existsByRobotId(2L)).thenReturn(true);
        assertThrows(ResourceInUseException.class, () -> robotService.deleteById(2));
        verify(users, never()).delete(any());
        verify(robots, never()).delete(any());
    }

    @Test
    void deletingLeafEntitiesAndParentsWithoutChildrenSucceeds() {
        User user = user(1);
        Robot robot = robot(2);
        Task task = task(8);
        Alert alert = alert(9);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(robots.findById(2L)).thenReturn(Optional.of(robot));
        when(tasks.findById(8L)).thenReturn(Optional.of(task));
        when(alerts.findById(9L)).thenReturn(Optional.of(alert));
        taskService.deleteById(8);
        alertService.deleteById(9);
        robotService.deleteById(2);
        userService.deleteById(1);
        verify(tasks).delete(task);
        verify(alerts).delete(alert);
        verify(robots).delete(robot);
        verify(users).delete(user);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1})
    void createRejectsAnyAssignedId(long id) {
        assertThrows(IllegalArgumentException.class, () -> userService.create(user(id)));
        assertThrows(IllegalArgumentException.class, () -> robotService.create(robot(id)));
        assertThrows(IllegalArgumentException.class, () -> taskService.create(task(id)));
        assertThrows(IllegalArgumentException.class, () -> alertService.create(alert(id)));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void updateAndRelationsRejectNonPositiveIds(long id) {
        assertThrows(IllegalArgumentException.class, () -> userService.update(user(id)));
        assertThrows(IllegalArgumentException.class, () -> robotService.update(robot(id)));
        assertThrows(IllegalArgumentException.class, () -> taskService.update(task(id)));
        assertThrows(IllegalArgumentException.class, () -> alertService.update(alert(id)));
        Robot robot = robot(0);
        robot.setUser(user(id));
        Task task = task(0);
        task.setRobot(robot(id));
        Alert alert = alert(0);
        alert.setRobot(robot(id));
        assertThrows(IllegalArgumentException.class, () -> robotService.create(robot));
        assertThrows(IllegalArgumentException.class, () -> taskService.create(task));
        assertThrows(IllegalArgumentException.class, () -> alertService.create(alert));
    }

    @Test
    void invalidFieldsAndMissingRelationsAreRejectedBeforeSaving() {
        assertThrows(ConstraintViolationException.class, () -> userService.create(new User()));
        assertThrows(ConstraintViolationException.class, () -> robotService.create(new Robot()));
        assertThrows(ConstraintViolationException.class, () -> taskService.create(new Task()));
        assertThrows(ConstraintViolationException.class, () -> alertService.create(new Alert()));
        verify(users, never()).save(any());
        verify(robots, never()).save(any());
        verify(tasks, never()).save(any());
        verify(alerts, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void invalidTaskDurationIsRejectedOnCreateAndUpdate(long duration) {
        Task input = task(0);
        input.setDuration(duration);
        assertThrows(ConstraintViolationException.class, () -> taskService.create(input));
        Task existing = task(8);
        when(tasks.findById(8L)).thenReturn(Optional.of(existing));
        input.setId(8);
        assertThrows(ConstraintViolationException.class, () -> taskService.update(input));
        assertEquals(1800, existing.getDuration());
        verify(tasks, never()).save(any());
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setName("Owner");
        user.setUsername("owner");
        user.setPassword("controller-supplied-hash");
        return user;
    }

    private static Robot robot(long id) {
        Robot robot = new Robot();
        robot.setId(id);
        robot.setName("Vacuum");
        robot.setPairCode("PAIR-123");
        robot.setUser(user(1));
        return robot;
    }

    private static Task task(long id) {
        Task task = new Task();
        task.setId(id);
        task.setName("Cleaning");
        task.setStartAt(TIME);
        task.setDuration(1800);
        task.setStatus("PENDING");
        task.setRobot(robot(2));
        return task;
    }

    private static Alert alert(long id) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setType("LOW_BATTERY");
        alert.setMessage("Battery is low");
        alert.setRobot(robot(2));
        return alert;
    }
}
