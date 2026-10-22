package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestWorkConflictException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestResolutionForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCancellationForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestFilters;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListPage;
import jakarta.servlet.http.HttpServletResponse;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import jakarta.validation.Valid;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RepairRequestController.class);

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
        RepairRequestDetails request = requestService.getDetails(requestId, currentUser.getUserId());
        model.addAttribute("request", request);
        if (request.canCancel() && !model.containsAttribute("cancellationForm")) {
            model.addAttribute("cancellationForm", new RepairRequestCancellationForm(null, request.updatedAt()));
        }
        if (request.canManage()) {
            model.addAttribute("technicianOptions", requestService.availableTechnicians(currentUser.getUserId()));
            model.addAttribute("priorities", RepairRequestPriority.values());
        }
        return "repair-requests/details";
    }

    @PostMapping("/{requestId}/cancel")
    public String cancelRequest(
            @PathVariable Long requestId,
            @Valid @ModelAttribute("cancellationForm") RepairRequestCancellationForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (bindingResult.hasErrors()) {
                requestService.getCancellationForm(requestId, currentUser.getUserId());
                return details(requestId, currentUser, model);
            }
            requestService.cancelRequest(requestId, form, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Request cancelled.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/assign")
    public String assign(
            @PathVariable Long requestId,
            @RequestParam Long technicianId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.assign(requestId, technicianId, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Technician assignment updated.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/unassign")
    public String unassign(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.unassign(requestId, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Assignment removed. Request is NEW again.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/priority")
    public String changePriority(
            @PathVariable Long requestId,
            @RequestParam RepairRequestPriority priority,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.changePriority(requestId, priority, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Priority updated.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/close")
    public String closeRequest(
            @PathVariable Long requestId,
            @RequestParam Instant updatedAt,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.closeRequest(requestId, updatedAt, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Repair closed and recorded in maintenance history.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException exception) {
            log.error("Could not confirm closure of repair request {}", requestId, exception);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not confirm closure. Reload the request before trying again.");
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/return")
    public String returnForRework(
            @PathVariable Long requestId,
            @RequestParam Instant updatedAt,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.returnForRework(requestId, updatedAt, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Repair returned to the technician for rework.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @PostMapping("/{requestId}/start")
    public String startWork(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            requestService.startWork(requestId, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Repair work started.");
        } catch (InvalidRepairRequestActionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/repair-requests/" + requestId;
    }

    @GetMapping("/{requestId}/complete")
    public String showResolutionForm(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            model.addAttribute("resolutionForm", requestService.getResolutionForm(requestId, currentUser.getUserId()));
            workModel(requestId, model);
            return "repair-requests/resolution";
        } catch (RepairRequestWorkConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/repair-requests/" + requestId;
        }
    }

    @PostMapping("/{requestId}/resolution")
    public String saveResolution(
            @PathVariable Long requestId,
            @Valid @ModelAttribute("resolutionForm") RepairRequestResolutionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response
    ) {
        return submitResolution(requestId, form, bindingResult, currentUser, model, redirectAttributes, response, false);
    }

    @PostMapping("/{requestId}/complete")
    public String completeWork(
            @PathVariable Long requestId,
            @Valid @ModelAttribute("resolutionForm") RepairRequestResolutionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response
    ) {
        if (form.getResolution() == null || form.getResolution().isBlank()) {
            bindingResult.rejectValue("resolution", "required", "Describe the repair result before completing the request.");
        }
        return submitResolution(requestId, form, bindingResult, currentUser, model, redirectAttributes, response, true);
    }

    private String submitResolution(
            Long requestId, RepairRequestResolutionForm form, BindingResult bindingResult,
            CustomUserDetails currentUser, Model model, RedirectAttributes redirectAttributes,
            HttpServletResponse response, boolean complete
    ) {
        workModel(requestId, model);
        try {
            if (bindingResult.hasErrors()) {
                requestService.getResolutionForm(requestId, currentUser.getUserId());
                return "repair-requests/resolution";
            }
            if (complete) {
                requestService.completeWork(requestId, form, currentUser.getUserId());
                redirectAttributes.addFlashAttribute("successMessage", "Repair completed and submitted for review.");
                return "redirect:/repair-requests/" + requestId;
            }
            requestService.saveResolution(requestId, form, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Draft saved.");
            return "redirect:/repair-requests/" + requestId + "/complete";
        } catch (RepairRequestWorkConflictException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("canSave", false);
            return "repair-requests/resolution";
        } catch (InvalidRepairRequestActionException exception) {
            bindingResult.rejectValue("resolution", "invalidResult", exception.getMessage());
            return "repair-requests/resolution";
        }
    }

    private void workModel(Long requestId, Model model) {
        model.addAttribute("requestId", requestId);
        model.addAttribute("canSave", true);
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
