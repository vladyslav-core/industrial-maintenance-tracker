package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserEditForm;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(UserCreateForm form) {
        String fullName = form.getFullName().trim();
        String email = form.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }

        String passwordHash = passwordEncoder.encode(form.getPassword());

        User user = new User(
                fullName,
                email,
                passwordHash,
                form.getRole()
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserListItem> getAllUsers() {
        Sort sort = Sort.by(Sort.Direction.ASC, "fullName");

        return userRepository.findAll(sort)
                .stream()
                .map(user -> new UserListItem(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole(),
                        user.isActive(),
                        user.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserEditForm getUserForEditing(Long userId) {
        User user = findUser(userId);

        return new UserEditForm(
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public void updateUser(
            Long userId,
            UserEditForm form
    ) {
        User user = findUser(userId);

        String fullName = form.getFullName().trim();
        String email = form.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        boolean emailChanged =
                !user.getEmail().equalsIgnoreCase(email);

        if (
                emailChanged
                        && userRepository.existsByEmailIgnoreCase(email)
        ) {
            throw new DuplicateEmailException(email);
        }

        boolean removesLastActiveAdminRole =
                user.isActive()
                        && user.getRole() == Role.ADMIN
                        && form.getRole() != Role.ADMIN
                        && userRepository.countByRoleAndActiveTrue(
                        Role.ADMIN
                ) <= 1;

        if (removesLastActiveAdminRole) {
            throw new LastActiveAdminException();
        }

        user.updateProfile(
                fullName,
                email,
                form.getRole()
        );
    }

    @Transactional
    public void activateUser(Long userId) {
        User user = findUser(userId);

        user.activate();
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = findUser(userId);

        if (!user.isActive()) {
            return;
        }

        boolean isLastActiveAdmin =
                user.getRole() == Role.ADMIN
                        && userRepository.countByRoleAndActiveTrue(
                        Role.ADMIN
                ) <= 1;

        if (isLastActiveAdmin) {
            throw new LastActiveAdminException();
        }

        user.deactivate();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(
                        () -> new UserNotFoundException(userId)
                );
    }
}