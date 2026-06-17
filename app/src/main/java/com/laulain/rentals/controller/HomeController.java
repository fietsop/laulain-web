package com.laulain.rentals.controller;

import com.laulain.rentals.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ItemService itemService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredItems", itemService.getFeaturedItems());
        model.addAttribute("categories", itemService.getActiveCategories());
        model.addAttribute("pageTitle", "Laulain Luxe Rentals — Dallas Metro Event Rentals");
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
