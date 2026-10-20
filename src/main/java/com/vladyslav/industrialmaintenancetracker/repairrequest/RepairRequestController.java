package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/repair-requests")
public class RepairRequestController {

    private final RepairRequestService requestService;

    public RepairRequestController(RepairRequestService requestService) {
        this.requestService = requestService;
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
            return "redirect:/equipment/" + form.getEquipmentId();
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
