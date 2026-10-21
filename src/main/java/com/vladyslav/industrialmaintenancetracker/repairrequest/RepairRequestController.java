package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
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
    public String list(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("requests", requestService.getList(currentUser.getUserId()));
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
