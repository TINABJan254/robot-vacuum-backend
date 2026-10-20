package com.ht.robotvacuum.service;

import com.ht.robotvacuum.dto.response.*;
import com.ht.robotvacuum.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import com.ht.robotvacuum.exception.DuplicateResourceException;
import com.ht.robotvacuum.exception.ResourceInUseException;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.model.User;
import com.ht.robotvacuum.model.enums.Role;
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
    private final CrudAccess access;
    private final UserMapper mapper;

    @Transactional
    public User create(@NotNull User user) {
        validation.requireNewId(user.getId());
        User created = new User();
        created.setName(user.getName());
        created.setUsername(user.getUsername());
        created.setPassword(user.getPassword());
        created.setRole(Role.USER);
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

    @Transactional
    public User update(@NotNull User user) {
        validation.requirePositiveId(user.getId(), "User id");
        User existing = findUser(user.getId());
        User candidate = new User();
        candidate.setName(user.getName());
        candidate.setUsername(user.getUsername());
        candidate.setPassword(existing.getPassword());
        candidate.setRole(existing.getRole());
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

    // These overloads keep authorization, persistence and DTO mapping in one transaction.
    @Transactional
    public ResCreateUserDTO create(@NotNull User input, Authentication authentication) {
        var actor = access.actor(authentication);
        access.admin(actor);
        return mapper.toCreate(this.create(input));
    }

    public ResGetUserDTO getById(@Positive long id, Authentication authentication) {
        return mapper.toGet(access.user(access.actor(authentication), id));
    }

    public ResPageDTO<ResGetUserDTO> getAll(@NotNull Pageable pageable, Authentication authentication) {
        access.admin(access.actor(authentication));
        return ResPageDTO.from(this.getAll(pageable).map(mapper::toGet));
    }

    @Transactional
    public ResUpdateUserDTO update(@NotNull User input, Authentication authentication) {
        access.lockUser(access.actor(authentication), input.getId());
        return mapper.toUpdate(this.update(input));
    }

    @Transactional
    public void deleteById(@Positive long id, Authentication authentication) {
        var actor = access.actor(authentication);
        access.admin(actor);
        access.lockUser(actor, id);
        this.deleteById(id);
    }

    private User findUser(long id) {
        validation.requirePositiveId(id, "User id");
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
