package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/maintenance-history")
public class MaintenanceRecordController {

    private final MaintenanceRecordService recordService;

    public MaintenanceRecordController(MaintenanceRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public String history(
            @RequestParam(required = false) @Positive Long equipmentId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        model.addAttribute("page", recordService.getHistoryPage(equipmentId, currentUser.getUserId()));
        model.addAttribute("selectedEquipmentId", equipmentId);
        return "maintenance/list";
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unavailableEquipment() {
        return "maintenance/not-found";
    }
}
