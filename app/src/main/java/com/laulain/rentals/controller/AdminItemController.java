package com.laulain.rentals.controller;

import com.laulain.rentals.dto.ItemDto.*;
import com.laulain.rentals.repository.ItemCategoryRepository;
import com.laulain.rentals.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Admin controller — item management (CRUD + images).
 * Requires ROLE_ADMIN.
 */
@Controller
@RequestMapping("/admin/items")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminItemController {

    private final ItemService itemService;
    private final ItemCategoryRepository categoryRepository;

    /** Item list */
    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        model.addAttribute("items", itemService.getAllItemsForAdmin(page, size));
        model.addAttribute("pageTitle", "Manage Items");
        return "admin/items/list";
    }

    /** New item form */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("item", new ItemRequest());
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
        model.addAttribute("pageTitle", "Add New Item");
        model.addAttribute("mode", "create");
        return "admin/items/form";
    }

    /** Create item */
    @PostMapping
    public String create(
            @Valid @ModelAttribute("item") ItemRequest request,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs
    ) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
            model.addAttribute("mode", "create");
            return "admin/items/form";
        }
        ItemResponse created = itemService.createItem(request);
        redirectAttrs.addFlashAttribute("successMessage",
                "Item \"" + created.getName() + "\" created successfully.");
        return "redirect:/admin/items/" + created.getId();
    }

    /**
     * Item edit form.
     *
     * Loads the ItemResponse for display (images, category name etc.) but also
     * populates a fresh ItemRequest for th:object form binding.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        ItemResponse itemResponse = itemService.getItemById(id);

        // Build an ItemRequest pre-populated from the existing item so the form fields are filled
        ItemRequest formRequest = new ItemRequest();
        formRequest.setName(itemResponse.getName());
        formRequest.setSlug(itemResponse.getSlug());
        formRequest.setCategoryId(itemResponse.getCategory() != null ? itemResponse.getCategory().getId() : null);
        formRequest.setDescription(itemResponse.getDescription());
        formRequest.setPricePerDay(itemResponse.getPricePerDay());
        formRequest.setQuantityInStock(itemResponse.getQuantityInStock());
        formRequest.setMinRentalDays(itemResponse.getMinRentalDays());
        formRequest.setDimensions(itemResponse.getDimensions());
        formRequest.setColor(itemResponse.getColor());
        formRequest.setMaterial(itemResponse.getMaterial());
        formRequest.setCareInstructions(itemResponse.getCareInstructions());
        formRequest.setActive(itemResponse.getActive());
        formRequest.setFeatured(itemResponse.getFeatured());

        // Pass both — the form binds to 'item' (ItemRequest), display uses 'itemDetail' (ItemResponse)
        model.addAttribute("item", formRequest);
        model.addAttribute("itemDetail", itemResponse);
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
        model.addAttribute("pageTitle", "Edit: " + itemResponse.getName());
        model.addAttribute("mode", "edit");
        return "admin/items/form";
    }

    /** Update item */
    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("item") ItemRequest request,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs
    ) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
            model.addAttribute("mode", "edit");
            // Re-fetch images etc. for display
            try {
                model.addAttribute("itemDetail", itemService.getItemById(id));
            } catch (Exception ignored) {}
            return "admin/items/form";
        }
        ItemResponse updated = itemService.updateItem(id, request);
        redirectAttrs.addFlashAttribute("successMessage",
                "Item \"" + updated.getName() + "\" updated successfully.");
        return "redirect:/admin/items/" + id;
    }

    /** Deactivate item (soft delete) */
    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable UUID id, RedirectAttributes redirectAttrs) {
        itemService.deactivateItem(id);
        redirectAttrs.addFlashAttribute("successMessage", "Item deactivated.");
        return "redirect:/admin/items";
    }

    // ---- Image management (AJAX endpoints) ----

    @PostMapping("/{id}/images")
    @ResponseBody
    public ResponseEntity<ImageResponse> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean setPrimary
    ) {
        return ResponseEntity.ok(itemService.uploadItemImage(id, file, setPrimary));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseBody
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        itemService.deleteItemImage(id, imageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images/{imageId}/primary")
    @ResponseBody
    public ResponseEntity<Void> setPrimary(@PathVariable UUID id, @PathVariable UUID imageId) {
        itemService.setPrimaryImage(id, imageId);
        return ResponseEntity.ok().build();
    }
}
