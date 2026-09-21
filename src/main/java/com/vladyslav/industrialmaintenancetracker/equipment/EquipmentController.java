package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentCreateForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateInventoryNumberException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidEquipmentStatusTransitionException;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public String listEquipment(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EquipmentStatus status,
            Model model
    ) {
        List<EquipmentListItem> equipment =
                equipmentService.getEquipmentList(search, status);

        model.addAttribute("equipment", equipment);
        model.addAttribute("search", search);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", EquipmentStatus.values());

        return "equipment/list";
    }

    @GetMapping("/{equipmentId}")
    public String showEquipmentDetails(
            @PathVariable Long equipmentId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            EquipmentDetails equipment =
                    equipmentService.getEquipmentDetails(equipmentId);

            model.addAttribute("equipment", equipment);
            model.addAttribute(
                    "statuses",
                    EquipmentStatus.values()
            );

            return "equipment/details";
        } catch (EquipmentNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/equipment";
        }
    }

    @GetMapping("/new")
    public String showCreateEquipmentForm(Model model) {
        model.addAttribute(
                "equipmentCreateForm",
                new EquipmentCreateForm()
        );

        return "equipment/create";
    }

    @PostMapping
    public String createEquipment(
            @Valid
            @ModelAttribute("equipmentCreateForm")
            EquipmentCreateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "equipment/create";
        }

        try {
            Equipment equipment =
                    equipmentService.createEquipment(form);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Equipment created successfully."
            );

            return "redirect:/equipment/" + equipment.getId();
        } catch (DuplicateInventoryNumberException exception) {
            bindingResult.rejectValue(
                    "inventoryNumber",
                    "duplicateInventoryNumber",
                    "Equipment with this inventory number already exists."
            );

            return "equipment/create";
        }
    }

    @GetMapping("/{equipmentId}/edit")
    public String showEditEquipmentForm(
            @PathVariable Long equipmentId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            EquipmentEditForm form =
                    equipmentService.getEquipmentForEditing(equipmentId);

            model.addAttribute("equipmentId", equipmentId);
            model.addAttribute("equipmentEditForm", form);

            return "equipment/edit";
        } catch (EquipmentNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/equipment";
        }
    }

    @PostMapping("/{equipmentId}/edit")
    public String updateEquipment(
            @PathVariable Long equipmentId,
            @Valid
            @ModelAttribute("equipmentEditForm")
            EquipmentEditForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("equipmentId", equipmentId);

            return "equipment/edit";
        }

        try {
            equipmentService.updateEquipment(equipmentId, form);
        } catch (DuplicateInventoryNumberException exception) {
            bindingResult.rejectValue(
                    "inventoryNumber",
                    "duplicateInventoryNumber",
                    "Equipment with this inventory number already exists."
            );
            model.addAttribute("equipmentId", equipmentId);

            return "equipment/edit";
        } catch (EquipmentNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/equipment";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Equipment updated successfully."
        );

        return "redirect:/equipment/" + equipmentId;
    }

    @PostMapping("/{equipmentId}/status")
    public String changeEquipmentStatus(
            @PathVariable Long equipmentId,
            @RequestParam EquipmentStatus status,
            RedirectAttributes redirectAttributes
    ) {
        try {
            equipmentService.changeEquipmentStatus(
                    equipmentId,
                    status
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Equipment status updated successfully."
            );

            return "redirect:/equipment/" + equipmentId;
        } catch (EquipmentNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/equipment";
        } catch (
                InvalidEquipmentStatusTransitionException exception
        ) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/equipment/" + equipmentId;
        }
    }

    @ModelAttribute("canManageEquipment")
    public boolean canManageEquipment(
            Authentication authentication
    ) {
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );
    }
}