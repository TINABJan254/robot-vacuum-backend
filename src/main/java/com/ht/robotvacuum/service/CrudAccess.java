package com.ht.robotvacuum.service;

import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.Robot;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.UserRepository;
import com.ht.robotvacuum.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
class CrudAccess {

    private final CurrentUser currentUser;
    private final ServiceValidation validation;
    private final UserRepository users;
    private final RobotRepository robots;

    record Actor(long id, boolean admin) {
    }

    Actor actor(Authentication authentication) {
        long id = currentUser.requireId(authentication);
        if (!users.existsById(id)) {
            throw new BadCredentialsException("Account no longer exists");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_USER"))) {
            throw new AccessDeniedException("Access denied");
        }
        return new Actor(id, admin);
    }

    void admin(Actor actor) {
        if (!actor.admin()) {
            throw new AccessDeniedException("Access denied");
        }
    }

    void ownsUser(Actor actor, long userId) {
        validation.requirePositiveId(userId, "User id");
        if (!actor.admin() && actor.id() != userId) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    User user(Actor actor, long id) {
        ownsUser(actor, id);
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    User lockUser(Actor actor, long id) {
        ownsUser(actor, id);
        return users.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    Robot robot(Actor actor, long id) {
        validation.requirePositiveId(id, "Id");
        Robot robot = robots.findById(id).orElseThrow(() -> new ResourceNotFoundException("Robot", id));
        ownsRobot(actor, robot);
        return robot;
    }

    void ownsRobot(Actor actor, Robot robot) {
        if (!actor.admin() && robot.getUser().getId() != actor.id()) {
            throw new ResourceNotFoundException("Robot", robot.getId());
        }
    }

    // All API writes involving a robot hold its lock through commit, including ownership transfer.
    // A child row is locked first on update/delete; robot locks are then acquired in ID order.
    void lockRobots(Actor actor, long... ids) {
        Arrays.stream(ids).distinct().sorted().forEach(id -> {
            validation.requirePositiveId(id, "Id");
            Robot robot = robots.findLockedById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Robot", id));
            ownsRobot(actor, robot);
        });
    }
}
