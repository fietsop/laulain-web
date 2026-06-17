package com.laulain.rentals.controller;

import com.laulain.rentals.dto.ItemDto.*;
import com.laulain.rentals.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public-facing catalog controller.
 * No authentication required — accessible to all visitors.
 */
@Controller
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final ItemService itemService;

    /** Catalog grid — supports category filter + search + pagination */
    @GetMapping
    public String catalog(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model
    ) {
        CatalogFilter filter = CatalogFilter.builder()
                .categoryId(categoryId)
                .search(search)
                .page(page)
                .size(size)
                .build();

        Page<ItemCard> items = itemService.getCatalog(filter);
        model.addAttribute("items", items);
        model.addAttribute("categories", itemService.getActiveCategories());
        model.addAttribute("filter", filter);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", items.getTotalPages());
        model.addAttribute("totalItems", items.getTotalElements());
        model.addAttribute("pageTitle", "Our Rental Collection");
        return "catalog/index";
    }

    /** Individual item detail page */
    @GetMapping("/{slug}")
    public String itemDetail(@PathVariable String slug, Model model) {
        ItemResponse item = itemService.getItemBySlug(slug);
        model.addAttribute("item", item);
        model.addAttribute("categories", itemService.getActiveCategories());
        model.addAttribute("pageTitle", item.getName() + " — Laulain Luxe Rentals");
        return "catalog/detail";
    }
}
