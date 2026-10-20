package com.ht.robotvacuum.service;

import com.ht.robotvacuum.exception.ResourceInUseException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Alert;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.Task;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.repository.AlertRepository;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.TaskRepository;
import com.ht.robotvacuum.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crud-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserService.class, RobotService.class, TaskService.class, AlertService.class,
        ServiceValidation.class, CrudIntegrationTests.ValidationConfiguration.class})
class CrudIntegrationTests {

    private static final Instant TIME = Instant.parse("2026-10-20T01:00:00Z");

    @Autowired private UserRepository users;
    @Autowired private RobotRepository robots;
    @Autowired private TaskRepository tasks;
    @Autowired private AlertRepository alerts;
    @Autowired private UserService userService;
    @Autowired private RobotService robotService;
    @Autowired private TaskService taskService;
    @Autowired private AlertService alertService;
    @Autowired private EntityManager entityManager;

    @Test
    void crudPersistsRelationshipsTimestampsUpdatesAndDeletes() {
        User user = userService.create(user("owner"));
        Robot robot = robotService.create(robot("PAIR-1", user));
        Task taskInput = task(robot);
        taskInput.setDuration(3_000_000_000L);
        taskInput.setCreatedAt(Instant.EPOCH);
        Task task = taskService.create(taskInput);
        Alert alertInput = alert(robot);
        alertInput.setCreatedAt(Instant.EPOCH);
        alertInput.setReadAt(TIME);
        Alert alert = alertService.create(alertInput);
        entityManager.flush();
        entityManager.clear();

        assertTrue(user.getId() > 0);
        assertTrue(robot.getId() > 0);
        Instant taskCreatedAt = taskService.getById(task.getId()).getCreatedAt();
        Instant alertCreatedAt = alertService.getById(alert.getId()).getCreatedAt();
        assertNotNull(taskCreatedAt);
        assertNotNull(alertCreatedAt);
        assertNotEquals(Instant.EPOCH, taskCreatedAt);
        assertNotEquals(Instant.EPOCH, alertCreatedAt);
        assertNull(alertService.getById(alert.getId()).getReadAt());
        assertEquals(3_000_000_000L, taskService.getById(task.getId()).getDuration());
        assertEquals(1, userService.getById(user.getId()).getRobots().size());
        assertEquals(1, robotService.getById(robot.getId()).getTasks().size());
        assertEquals(1, robotService.getById(robot.getId()).getAlerts().size());

        User userUpdate = user("owner");
        userUpdate.setId(user.getId());
        userUpdate.setName("Updated owner");
        userUpdate.setPassword(null);
        userService.update(userUpdate);
        Robot robotUpdate = robot("PAIR-UPDATED", user);
        robotUpdate.setId(robot.getId());
        robotService.update(robotUpdate);
        Task taskUpdate = task(robot);
        taskUpdate.setId(task.getId());
        taskUpdate.setName("Updated task");
        taskUpdate.setCreatedAt(Instant.EPOCH);
        taskService.update(taskUpdate);
        Alert alertUpdate = alert(robot);
        alertUpdate.setId(alert.getId());
        alertUpdate.setMessage("Updated message");
        alertUpdate.setCreatedAt(Instant.EPOCH);
        alertUpdate.setReadAt(TIME);
        alertService.update(alertUpdate);
        entityManager.flush();
        entityManager.clear();

        assertEquals("Updated owner", userService.getById(user.getId()).getName());
        assertEquals("supplied-password-hash", userService.getById(user.getId()).getPassword());
        assertEquals("PAIR-UPDATED", robotService.getById(robot.getId()).getPairCode());
        assertEquals("Updated task", taskService.getById(task.getId()).getName());
        assertEquals(taskCreatedAt, taskService.getById(task.getId()).getCreatedAt());
        assertEquals("Updated message", alertService.getById(alert.getId()).getMessage());
        assertEquals(alertCreatedAt, alertService.getById(alert.getId()).getCreatedAt());
        assertEquals(TIME, alertService.getById(alert.getId()).getReadAt());

        var pageable = PageRequest.of(0, 10);
        assertEquals(1, userService.getAll(pageable).getTotalElements());
        assertEquals(1, robotService.getAll(pageable).getTotalElements());
        assertEquals(1, taskService.getAll(pageable).getTotalElements());
        assertEquals(1, alertService.getAll(pageable).getTotalElements());
        taskService.deleteById(task.getId());
        alertService.deleteById(alert.getId());
        robotService.deleteById(robot.getId());
        userService.deleteById(user.getId());
        entityManager.flush();
        entityManager.clear();
        assertEquals(0, tasks.count());
        assertEquals(0, alerts.count());
        assertEquals(0, robots.count());
        assertEquals(0, users.count());
    }

    @Test
    void derivedQueriesFilterByParentAndPaginate() {
        User first = users.save(user("first"));
        User second = users.save(user("second"));
        Robot firstRobot = robots.save(robot("PAIR-A", first));
        Robot secondRobot = robots.save(robot("PAIR-B", first));
        Robot otherRobot = robots.save(robot("PAIR-C", second));
        tasks.save(task(firstRobot));
        tasks.save(task(firstRobot));
        tasks.save(task(otherRobot));
        alerts.save(alert(firstRobot));
        alerts.save(alert(firstRobot));
        alerts.save(alert(otherRobot));
        entityManager.flush();
        entityManager.clear();

        assertEquals(first.getId(), users.findByUsername("first").orElseThrow().getId());
        assertTrue(users.findByUsername("missing").isEmpty());
        assertTrue(users.existsByUsername("first"));
        assertFalse(users.existsByUsername("missing"));
        assertFalse(users.existsByUsernameAndIdNot("first", first.getId()));
        assertTrue(users.existsByUsernameAndIdNot("first", second.getId()));
        assertEquals(firstRobot.getId(), robots.findByPairCode("PAIR-A").orElseThrow().getId());
        assertTrue(robots.findByPairCode("missing").isEmpty());
        assertTrue(robots.existsByPairCode("PAIR-A"));
        assertFalse(robots.existsByPairCode("missing"));
        assertFalse(robots.existsByPairCodeAndIdNot("PAIR-A", firstRobot.getId()));
        assertTrue(robots.existsByPairCodeAndIdNot("PAIR-A", secondRobot.getId()));
        assertTrue(robots.existsByUserId(first.getId()));
        assertFalse(robots.existsByUserId(Long.MAX_VALUE));
        assertTrue(tasks.existsByRobotId(firstRobot.getId()));
        assertTrue(alerts.existsByRobotId(firstRobot.getId()));
        assertFalse(tasks.existsByRobotId(secondRobot.getId()));
        assertFalse(alerts.existsByRobotId(secondRobot.getId()));

        var firstPage = PageRequest.of(0, 1, Sort.by("id"));
        var secondPage = PageRequest.of(1, 1, Sort.by("id"));
        var robotPage = robotService.getByUserId(first.getId(), secondPage);
        assertEquals(2, robotPage.getTotalElements());
        assertEquals(List.of(secondRobot.getId()), robotPage.map(Robot::getId).getContent());
        var taskPage = taskService.getByRobotId(firstRobot.getId(), firstPage);
        assertEquals(2, taskPage.getTotalElements());
        assertEquals(1, taskPage.getNumberOfElements());
        assertTrue(taskPage.stream().allMatch(value -> value.getRobot().getId() == firstRobot.getId()));
        var alertPage = alertService.getByRobotId(firstRobot.getId(), secondPage);
        assertEquals(2, alertPage.getTotalElements());
        assertEquals(1, alertPage.getNumberOfElements());
        assertTrue(alertPage.stream().allMatch(value -> value.getRobot().getId() == firstRobot.getId()));
        assertTrue(taskService.getByRobotId(secondRobot.getId(), firstPage).isEmpty());
        assertTrue(alertService.getByRobotId(secondRobot.getId(), firstPage).isEmpty());
        User withoutRobots = users.save(user("empty"));
        assertTrue(robotService.getByUserId(withoutRobots.getId(), firstPage).isEmpty());
    }

    @Test
    void databaseRejectsDuplicateUsername() {
        users.saveAndFlush(user("owner"));
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(user("owner")));
    }

    @Test
    void databaseRejectsDuplicatePairCode() {
        User user = users.save(user("owner"));
        robots.saveAndFlush(robot("PAIR-1", user));
        assertThrows(DataIntegrityViolationException.class,
                () -> robots.saveAndFlush(robot("PAIR-1", user)));
    }

    @Test
    void databaseForeignKeyProtectsUserWithRobot() {
        User user = users.save(user("owner"));
        robots.saveAndFlush(robot("PAIR-1", user));
        assertThrows(DataIntegrityViolationException.class,
                () -> users.deleteAllByIdInBatch(List.of(user.getId())));
    }

    @Test
    void databaseForeignKeyProtectsRobotWithTask() {
        User user = users.save(user("owner"));
        Robot robot = robots.save(robot("PAIR-1", user));
        tasks.saveAndFlush(task(robot));
        assertThrows(DataIntegrityViolationException.class,
                () -> robots.deleteAllByIdInBatch(List.of(robot.getId())));
    }

    @Test
    void databaseForeignKeyProtectsRobotWithAlert() {
        User user = users.save(user("owner"));
        Robot robot = robots.save(robot("PAIR-1", user));
        alerts.saveAndFlush(alert(robot));
        assertThrows(DataIntegrityViolationException.class,
                () -> robots.deleteAllByIdInBatch(List.of(robot.getId())));
    }

    // Each failure gets its own test transaction: a Service exception marks it rollback-only.
    @Test
    void servicePreventsDeletingUserWithRobots() {
        User user = users.save(user("owner"));
        robots.saveAndFlush(robot("PAIR-1", user));
        assertThrows(ResourceInUseException.class, () -> userService.deleteById(user.getId()));
    }

    @Test
    void servicePreventsDeletingRobotWithTasks() {
        User user = users.save(user("owner"));
        Robot robot = robots.save(robot("PAIR-1", user));
        tasks.saveAndFlush(task(robot));
        assertThrows(ResourceInUseException.class, () -> robotService.deleteById(robot.getId()));
    }

    @Test
    void servicePreventsDeletingRobotWithAlerts() {
        User user = users.save(user("owner"));
        Robot robot = robots.save(robot("PAIR-1", user));
        alerts.saveAndFlush(alert(robot));
        assertThrows(ResourceInUseException.class, () -> robotService.deleteById(robot.getId()));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void serviceProxyRejectsNonPositiveReadDeleteAndFilterIds(long id) {
        assertTrue(AopUtils.isAopProxy(userService));
        assertTrue(AopUtils.isAopProxy(robotService));
        assertTrue(AopUtils.isAopProxy(taskService));
        assertTrue(AopUtils.isAopProxy(alertService));
        var pageable = PageRequest.of(0, 10);
        assertAll(
                () -> assertThrows(ConstraintViolationException.class, () -> userService.getById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.getById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.getById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.getById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> userService.deleteById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.deleteById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.deleteById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.deleteById(id)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.getByUserId(id, pageable)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.getByRobotId(id, pageable)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.getByRobotId(id, pageable)));
    }

    @Test
    void serviceProxyRejectsNullObjectsAndPageable() {
        assertAll(
                () -> assertThrows(ConstraintViolationException.class, () -> userService.create(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.create(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.create(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.create(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> userService.update(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.update(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.update(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.update(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> userService.getAll(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.getAll(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.getAll(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.getAll(null)),
                () -> assertThrows(ConstraintViolationException.class, () -> robotService.getByUserId(1, null)),
                () -> assertThrows(ConstraintViolationException.class, () -> taskService.getByRobotId(1, null)),
                () -> assertThrows(ConstraintViolationException.class, () -> alertService.getByRobotId(1, null)));
    }

    @Test
    void filteredQueryRejectsMissingParent() {
        assertThrows(ResourceNotFoundException.class,
                () -> robotService.getByUserId(Long.MAX_VALUE, PageRequest.of(0, 10)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationConfiguration {

        @Bean
        static LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }

        @Bean
        static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }
    }

    private static User user(String username) {
        User user = new User();
        user.setName("Owner");
        user.setUsername(username);
        user.setPassword("supplied-password-hash");
        return user;
    }

    private static Robot robot(String code, User user) {
        Robot robot = new Robot();
        robot.setName("Vacuum");
        robot.setPairCode(code);
        robot.setUser(user);
        return robot;
    }

    private static Task task(Robot robot) {
        Task task = new Task();
        task.setName("Cleaning");
        task.setStartAt(TIME);
        task.setDuration(1800);
        task.setStatus("PENDING");
        task.setRobot(robot);
        return task;
    }

    private static Alert alert(Robot robot) {
        Alert alert = new Alert();
        alert.setType("LOW_BATTERY");
        alert.setMessage("Battery is low");
        alert.setRobot(robot);
        return alert;
    }
}
