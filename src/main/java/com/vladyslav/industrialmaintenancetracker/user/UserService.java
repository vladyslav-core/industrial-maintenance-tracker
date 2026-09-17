package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;
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
}