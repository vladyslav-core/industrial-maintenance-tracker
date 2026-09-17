package com.vladyslav.industrialmaintenancetracker.user;

import com.vladyslav.industrialmaintenancetracker.user.dto.UserListItem;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}