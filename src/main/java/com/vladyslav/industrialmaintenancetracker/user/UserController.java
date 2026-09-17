package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.exception.DuplicateEmailException;
import com.vladyslav.industrialmaintenancetracker.exception.LastActiveAdminException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(Model model) {
        List<UserListItem> users = userService.getAllUsers();

        model.addAttribute("users", users);

        return "users/list";
    }

    @GetMapping("/new")
    public String showCreateUserForm(Model model) {
        model.addAttribute(
                "userCreateForm",
                new UserCreateForm()
        );
        model.addAttribute("roles", Role.values());

        return "users/create";
    }

    @PostMapping
    public String createUser(
            @Valid
            @ModelAttribute("userCreateForm")
            UserCreateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());

            return "users/create";
        }

        try {
            userService.createUser(form);
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue(
                    "email",
                    "duplicateEmail",
                    "A user with this email already exists."
            );
            model.addAttribute("roles", Role.values());

            return "users/create";
        }

        return "redirect:/users";
    }

    @PostMapping("/{userId}/activate")
    public String activateUser(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.activateUser(userId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "User activated successfully."
            );
        } catch (UserNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/users";
    }

    @PostMapping("/{userId}/deactivate")
    public String deactivateUser(
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.deactivateUser(userId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "User deactivated successfully."
            );
        } catch (
                LastActiveAdminException
                | UserNotFoundException exception
        ) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/users";
    }
}