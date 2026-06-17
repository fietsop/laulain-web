package com.laulain.rentals.service;

import com.laulain.rentals.dto.ItemDto.*;
import com.laulain.rentals.exception.DuplicateResourceException;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.Item;
import com.laulain.rentals.model.ItemCategory;
import com.laulain.rentals.repository.ItemCategoryRepository;
import com.laulain.rentals.repository.ItemImageRepository;
import com.laulain.rentals.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService Tests")
class ItemServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock ItemCategoryRepository categoryRepository;
    @Mock ItemImageRepository imageRepository;
    @Mock S3Service s3Service;

    @InjectMocks ItemService itemService;

    private ItemCategory testCategory;
    private Item testItem;
    private UUID itemId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        itemId = UUID.randomUUID();

        testCategory = ItemCategory.builder()
                .id(categoryId)
                .name("Chafing Dishes")
                .active(true)
                .build();

        testItem = Item.builder()
                .id(itemId)
                .name("Stainless Steel Chafing Dish")
                .slug("stainless-steel-chafing-dish")
                .category(testCategory)
                .pricePerDay(new BigDecimal("25.00"))
                .quantityInStock(5)
                .active(true)
                .featured(false)
                .images(List.of())
                .build();
    }

    @Test
    @DisplayName("getItemBySlug — returns item when found")
    void getItemBySlug_found() {
        when(itemRepository.findBySlugAndActiveTrue("stainless-steel-chafing-dish"))
                .thenReturn(Optional.of(testItem));

        ItemResponse response = itemService.getItemBySlug("stainless-steel-chafing-dish");

        assertThat(response.getName()).isEqualTo("Stainless Steel Chafing Dish");
        assertThat(response.getPricePerDay()).isEqualByComparingTo("25.00");
        assertThat(response.getCategory().getName()).isEqualTo("Chafing Dishes");
    }

    @Test
    @DisplayName("getItemBySlug — throws ResourceNotFoundException when not found")
    void getItemBySlug_notFound() {
        when(itemRepository.findBySlugAndActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItemBySlug("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("createItem — creates item and returns response")
    void createItem_success() {
        ItemRequest request = new ItemRequest();
        request.setName("Gold Chafing Dish");
        request.setCategoryId(categoryId);
        request.setPricePerDay(new BigDecimal("35.00"));
        request.setQuantityInStock(3);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(itemRepository.existsBySlug("gold-chafing-dish")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item item = inv.getArgument(0);
            item.setImages(List.of());
            return item;
        });

        ItemResponse response = itemService.createItem(request);

        assertThat(response.getName()).isEqualTo("Gold Chafing Dish");
        assertThat(response.getSlug()).isEqualTo("gold-chafing-dish");
        verify(itemRepository, times(1)).save(any(Item.class));
    }

    @Test
    @DisplayName("createItem — throws DuplicateResourceException when slug exists")
    void createItem_duplicateSlug() {
        ItemRequest request = new ItemRequest();
        request.setName("Stainless Steel Chafing Dish");
        request.setCategoryId(categoryId);
        request.setPricePerDay(new BigDecimal("25.00"));
        request.setQuantityInStock(1);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(itemRepository.existsBySlug("stainless-steel-chafing-dish")).thenReturn(true);

        assertThatThrownBy(() -> itemService.createItem(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateItem — sets active to false")
    void deactivateItem_success() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        itemService.deactivateItem(itemId);

        assertThat(testItem.getActive()).isFalse();
        verify(itemRepository).save(testItem);
    }

    @Test
    @DisplayName("slugify — converts name to valid URL slug")
    void slugify_variousInputs() {
        assertThat(ItemService.slugify("Gold Chafing Dish Set")).isEqualTo("gold-chafing-dish-set");
        assertThat(ItemService.slugify("  Multiple   Spaces  ")).isEqualTo("multiple-spaces");
        assertThat(ItemService.slugify("Special & Characters!")).isEqualTo("special-characters");
        assertThat(ItemService.slugify("Already-a-slug")).isEqualTo("already-a-slug");
    }
}
