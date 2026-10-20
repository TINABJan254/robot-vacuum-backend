package com.ht.robotvacuum.service;

import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceInUseException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.repository.RobotRepository;
import com.ht.robotvacuum.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RobotRepository robotRepository;
    private final ServiceValidation validation;

    /** The caller supplies the password hash; this service does not hash passwords. */
    @Transactional
    public User create(@NotNull User user) {
        validation.requireNewId(user.getId());
        User created = new User();
        created.setName(user.getName());
        created.setUsername(user.getUsername());
        created.setPassword(user.getPassword());
        validation.validate(created);
        if (userRepository.existsByUsername(created.getUsername())) {
            throw new DuplicateResourceException("Username is already in use");
        }
        return userRepository.save(created);
    }

    public User getById(@Positive long id) {
        return findUser(id);
    }

    public Page<User> getAll(@NotNull Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /** Updates profile fields only; the existing password and robots are preserved. */
    @Transactional
    public User update(@NotNull User user) {
        validation.requirePositiveId(user.getId(), "User id");
        User existing = findUser(user.getId());
        User candidate = new User();
        candidate.setName(user.getName());
        candidate.setUsername(user.getUsername());
        candidate.setPassword(existing.getPassword());
        validation.validate(candidate);
        if (userRepository.existsByUsernameAndIdNot(candidate.getUsername(), existing.getId())) {
            throw new DuplicateResourceException("Username is already in use");
        }
        existing.setName(candidate.getName());
        existing.setUsername(candidate.getUsername());
        return userRepository.save(existing);
    }

    @Transactional
    public void deleteById(@Positive long id) {
        User user = findUser(id);
        if (robotRepository.existsByUserId(id)) {
            throw new ResourceInUseException("Cannot delete a user that still has robots");
        }
        userRepository.delete(user);
    }

    private User findUser(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
