package com.vladyslav.industrialmaintenancetracker.common.controller;

import com.vladyslav.industrialmaintenancetracker.dashboard.DashboardService;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DashboardService dashboardService;

    public HomeController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser == null) {
            throw new AccessDeniedException("Account is unavailable");
        }
        model.addAttribute("dashboard", dashboardService.getDashboard(currentUser.getUserId(), currentUser.getRole()));
        return "home";
    }
}
