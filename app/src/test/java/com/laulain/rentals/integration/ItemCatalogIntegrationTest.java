package com.laulain.rentals.integration;

import com.laulain.rentals.dto.ItemDto.*;
import com.laulain.rentals.model.Item;
import com.laulain.rentals.model.ItemCategory;
import com.laulain.rentals.repository.ItemCategoryRepository;
import com.laulain.rentals.repository.ItemRepository;
import com.laulain.rentals.service.ItemService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Item Catalog Integration Tests")
class ItemCatalogIntegrationTest extends BaseIntegrationTest {

    @Autowired ItemService itemService;
    @Autowired ItemRepository itemRepository;
    @Autowired ItemCategoryRepository categoryRepository;

    private ItemCategory category;

    @BeforeEach
    @Transactional
    void setUp() {
        // Use seeded category from Flyway V1
        category = categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().findFirst()
                .orElseGet(() -> categoryRepository.save(ItemCategory.builder()
                        .name("Test Category").active(true).sortOrder(99).build()));
    }

    @Test
    @DisplayName("Create item → retrieve by slug")
    @Transactional
    void createAndRetrieveBySlug() {
        ItemRequest request = new ItemRequest();
        request.setName("Integration Test Chafing Dish");
        request.setCategoryId(category.getId());
        request.setPricePerDay(new BigDecimal("45.00"));
        request.setQuantityInStock(3);
        request.setActive(true);
        request.setFeatured(false);

        ItemResponse created = itemService.createItem(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getSlug()).isEqualTo("integration-test-chafing-dish");
        assertThat(created.getPricePerDay()).isEqualByComparingTo("45.00");

        // Retrieve by slug
        ItemResponse found = itemService.getItemBySlug("integration-test-chafing-dish");
        assertThat(found.getName()).isEqualTo("Integration Test Chafing Dish");
        assertThat(found.getCategory().getId()).isEqualTo(category.getId());
    }

    @Test
    @DisplayName("Catalog pagination returns correct page size")
    @Transactional
    void catalogPagination() {
        // Create 5 items
        for (int i = 1; i <= 5; i++) {
            ItemRequest req = new ItemRequest();
            req.setName("Paged Item " + i + " " + UUID.randomUUID().toString().substring(0, 8));
            req.setCategoryId(category.getId());
            req.setPricePerDay(new BigDecimal("10.00"));
            req.setQuantityInStock(1);
            req.setActive(true);
            req.setFeatured(false);
            itemService.createItem(req);
        }

        CatalogFilter filter = CatalogFilter.builder()
                .page(0).size(2).sort("name").build();
        Page<ItemCard> page = itemService.getCatalog(filter);

        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Deactivate item removes it from catalog")
    @Transactional
    void deactivateRemovesFromCatalog() {
        String uniqueName = "Deactivate Test " + UUID.randomUUID().toString().substring(0, 8);
        ItemRequest req = new ItemRequest();
        req.setName(uniqueName);
        req.setCategoryId(category.getId());
        req.setPricePerDay(new BigDecimal("20.00"));
        req.setQuantityInStock(1);
        req.setActive(true);
        req.setFeatured(false);

        ItemResponse item = itemService.createItem(req);
        String slug = item.getSlug();

        // Confirm it's visible
        assertThat(itemService.getItemBySlug(slug)).isNotNull();

        // Deactivate
        itemService.deactivateItem(item.getId());

        // Slug lookup should now throw
        assertThatThrownBy(() -> itemService.getItemBySlug(slug))
                .isInstanceOf(com.laulain.rentals.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Duplicate slug throws DuplicateResourceException")
    void duplicateSlug() {
        String uniqueName = "Duplicate Test " + UUID.randomUUID().toString().substring(0, 8);

        ItemRequest req1 = new ItemRequest();
        req1.setName(uniqueName);
        req1.setCategoryId(category.getId());
        req1.setPricePerDay(new BigDecimal("30.00"));
        req1.setQuantityInStock(1);
        req1.setActive(true);
        req1.setFeatured(false);
        itemService.createItem(req1);

        // Same name → same slug
        ItemRequest req2 = new ItemRequest();
        req2.setName(uniqueName);
        req2.setCategoryId(category.getId());
        req2.setPricePerDay(new BigDecimal("35.00"));
        req2.setQuantityInStock(1);
        req2.setActive(true);
        req2.setFeatured(false);

        assertThatThrownBy(() -> itemService.createItem(req2))
                .isInstanceOf(com.laulain.rentals.exception.DuplicateResourceException.class);
    }
}
