package com.vladyslav.industrialmaintenancetracker.common.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        boolean canViewMaintenanceHistory = authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_TECHNICIAN"));
        model.addAttribute("canViewMaintenanceHistory", canViewMaintenanceHistory);
        return "home";
    }
}
