package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestFilters;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListPage;
import jakarta.servlet.http.HttpServletResponse;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/repair-requests")
public class RepairRequestController {

    private final RepairRequestService requestService;

    public RepairRequestController(RepairRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public String list(
            @ModelAttribute("filters") RepairRequestFilters filters,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        RepairRequestListPage page = requestService.getListPage(filters, currentUser.getUserId());
        model.addAttribute("page", page);
        model.addAttribute("requests", page.requests());
        model.addAttribute("statuses", RepairRequestStatus.values());
        model.addAttribute("priorities", RepairRequestPriority.values());
        return "repair-requests/list";
    }

    @GetMapping("/{requestId}")
    public String details(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        model.addAttribute("request", requestService.getDetails(requestId, currentUser.getUserId()));
        return "repair-requests/details";
    }

    @GetMapping("/{requestId}/edit")
    public String showEditForm(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            model.addAttribute("editForm", requestService.getEditForm(requestId, currentUser.getUserId()));
            editModel(requestId, model);
            return "repair-requests/edit";
        } catch (RepairRequestEditConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/repair-requests/" + requestId;
        }
    }

    @PostMapping("/{requestId}/edit")
    public String updateDetails(
            @PathVariable Long requestId,
            @Valid @ModelAttribute("editForm") RepairRequestEditForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response
    ) {
        editModel(requestId, model);
        try {
            if (bindingResult.hasErrors()) {
                requestService.getEditForm(requestId, currentUser.getUserId());
                return "repair-requests/edit";
            }
            requestService.updateDetails(requestId, form, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Request details updated.");
            return "redirect:/repair-requests/" + requestId;
        } catch (RepairRequestEditConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("canSave", false);
            return "repair-requests/edit";
        }
    }

    private void editModel(Long requestId, Model model) {
        model.addAttribute("requestId", requestId);
        model.addAttribute("canSave", true);
    }

    @ExceptionHandler(RepairRequestNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unavailableRequest() {
        return "repair-requests/not-found";
    }

    @GetMapping("/new")
    public String showCreateForm(
            @RequestParam(required = false) Long equipmentId,
            Model model
    ) {
        RepairRequestCreateForm form = new RepairRequestCreateForm();
        form.setEquipmentId(equipmentId);
        model.addAttribute("repairRequestCreateForm", form);
        addEquipmentOptions(model);
        return "repair-requests/create";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("repairRequestCreateForm") RepairRequestCreateForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addEquipmentOptions(model);
            return "repair-requests/create";
        }

        try {
            RepairRequest request = requestService.create(form, currentUser.getUserId());
            redirectAttributes.addFlashAttribute(
                    "successMessage", "Repair request #" + request.getId() + " created."
            );
            return "redirect:/repair-requests/" + request.getId();
        } catch (EquipmentNotFoundException | EquipmentNotAvailableException exception) {
            bindingResult.rejectValue("equipmentId", "invalidEquipment", exception.getMessage());
            addEquipmentOptions(model);
            return "repair-requests/create";
        }
    }

    private void addEquipmentOptions(Model model) {
        model.addAttribute("equipmentOptions", requestService.availableEquipment());
        model.addAttribute("priorities", RepairRequestPriority.values());
    }
}
