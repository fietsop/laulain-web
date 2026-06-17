package com.laulain.rentals.service;

import com.laulain.rentals.dto.ItemDto.*;
import com.laulain.rentals.exception.DuplicateResourceException;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.Item;
import com.laulain.rentals.model.ItemCategory;
import com.laulain.rentals.model.ItemImage;
import com.laulain.rentals.repository.ItemCategoryRepository;
import com.laulain.rentals.repository.ItemImageRepository;
import com.laulain.rentals.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemCategoryRepository categoryRepository;
    private final ItemImageRepository imageRepository;
    private final S3Service s3Service;

    // ============================================================
    //  PUBLIC CATALOG
    // ============================================================

    /** Paginated catalog with optional category filter and search */
    @Cacheable(value = "catalog", key = "#filter.categoryId + '_' + #filter.search + '_' + #filter.page")
    public Page<ItemCard> getCatalog(CatalogFilter filter) {
        Pageable pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by(filter.getSort()).ascending()
        );

        Page<Item> items = itemRepository.findCatalog(
                filter.getCategoryId(),
                filter.getSearch(),
                pageable
        );

        return items.map(this::toItemCard);
    }

    /** Featured items for homepage */
    @Cacheable("featured-items")
    public List<ItemCard> getFeaturedItems() {
        return itemRepository.findByFeaturedTrueAndActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toItemCard)
                .collect(Collectors.toList());
    }

    /** Full item detail by slug */
    public ItemResponse getItemBySlug(String slug) {
        Item item = itemRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + slug));
        return toItemResponse(item);
    }

    /** Categories that have at least one active item — for filter sidebar */
    @Cacheable("active-categories")
    public List<CategorySummary> getActiveCategories() {
        return categoryRepository.findCategoriesWithActiveItems()
                .stream()
                .map(c -> CategorySummary.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** All active items — for booking form item selector */
    public List<ItemCard> getAllActiveItems() {
        return itemRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toItemCard)
                .collect(Collectors.toList());
    }

    // ============================================================
    //  ADMIN — ITEMS
    // ============================================================

    public Page<ItemResponse> getAllItemsForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return itemRepository.findAll(pageable).map(this::toItemResponse);
    }

    public ItemResponse getItemById(UUID id) {
        Item item = itemRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));
        return toItemResponse(item);
    }

    @Transactional
    @CacheEvict(value = {"catalog", "featured-items", "active-categories"}, allEntries = true)
    public ItemResponse createItem(ItemRequest request) {
        ItemCategory category = findCategory(request.getCategoryId());

        String slug = resolveSlug(request.getSlug(), request.getName(), null);

        Item item = Item.builder()
                .name(request.getName())
                .slug(slug)
                .category(category)
                .description(request.getDescription())
                .pricePerDay(request.getPricePerDay())
                .depositAmount(request.getDepositAmount())
                .quantityInStock(request.getQuantityInStock())
                .minRentalDays(request.getMinRentalDays() != null ? request.getMinRentalDays() : 1)
                .dimensions(request.getDimensions())
                .color(request.getColor())
                .material(request.getMaterial())
                .careInstructions(request.getCareInstructions())
                .active(request.getActive() != null ? request.getActive() : true)
                .featured(request.getFeatured() != null ? request.getFeatured() : false)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        Item saved = itemRepository.save(item);
        log.info("Created item: {} ({})", saved.getName(), saved.getId());
        return toItemResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"catalog", "featured-items", "active-categories"}, allEntries = true)
    public ItemResponse updateItem(UUID id, ItemRequest request) {
        Item item = itemRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

        ItemCategory category = findCategory(request.getCategoryId());
        String slug = resolveSlug(request.getSlug(), request.getName(), id);

        item.setName(request.getName());
        item.setSlug(slug);
        item.setCategory(category);
        item.setDescription(request.getDescription());
        item.setPricePerDay(request.getPricePerDay());
        item.setDepositAmount(request.getDepositAmount());
        item.setQuantityInStock(request.getQuantityInStock());
        item.setMinRentalDays(request.getMinRentalDays() != null ? request.getMinRentalDays() : 1);
        item.setDimensions(request.getDimensions());
        item.setColor(request.getColor());
        item.setMaterial(request.getMaterial());
        item.setCareInstructions(request.getCareInstructions());
        item.setActive(request.getActive() != null ? request.getActive() : true);
        item.setFeatured(request.getFeatured() != null ? request.getFeatured() : false);
        item.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        log.info("Updated item: {} ({})", item.getName(), item.getId());
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    @CacheEvict(value = {"catalog", "featured-items", "active-categories"}, allEntries = true)
    public void deactivateItem(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));
        item.setActive(false);
        itemRepository.save(item);
        log.info("Deactivated item: {}", id);
    }

    // ============================================================
    //  ADMIN — IMAGES
    // ============================================================

    @Transactional
    @CacheEvict(value = {"catalog", "featured-items"}, allEntries = true)
    public ImageResponse uploadItemImage(UUID itemId, MultipartFile file, boolean setPrimary) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        String s3Key = s3Service.uploadItemImage(file, itemId);
        String s3Url = s3Service.generatePublicUrl(s3Key);

        if (setPrimary) {
            imageRepository.clearPrimaryFlag(itemId);
        }

        ItemImage image = ItemImage.builder()
                .item(item)
                .s3Key(s3Key)
                .s3Url(s3Url)
                .altText(item.getName())
                .isPrimary(setPrimary || item.getImages().isEmpty())
                .sortOrder(item.getImages().size())
                .build();

        ItemImage saved = imageRepository.save(image);
        log.info("Uploaded image for item {}: {}", itemId, s3Key);

        return ImageResponse.builder()
                .id(saved.getId())
                .s3Url(saved.getS3Url())
                .altText(saved.getAltText())
                .isPrimary(saved.getIsPrimary())
                .sortOrder(saved.getSortOrder())
                .build();
    }

    @Transactional
    @CacheEvict(value = {"catalog", "featured-items"}, allEntries = true)
    public void deleteItemImage(UUID itemId, UUID imageId) {
        ItemImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        if (!image.getItem().getId().equals(itemId)) {
            throw new ResourceNotFoundException("Image does not belong to this item");
        }

        s3Service.deleteFile(image.getS3Key());
        imageRepository.delete(image);
        log.info("Deleted image {} from item {}", imageId, itemId);
    }

    @Transactional
    public void setPrimaryImage(UUID itemId, UUID imageId) {
        imageRepository.clearPrimaryFlag(itemId);
        ItemImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        image.setIsPrimary(true);
        imageRepository.save(image);
    }

    // ============================================================
    //  MAPPING HELPERS
    // ============================================================

    private ItemCard toItemCard(Item item) {
        ItemImage primary = item.getPrimaryImage();
        return ItemCard.builder()
                .id(item.getId())
                .name(item.getName())
                .slug(item.getSlug())
                .pricePerDay(item.getPricePerDay())
                .primaryImageUrl(primary != null ? primary.getS3Url() : null)
                .categoryName(item.getCategory() != null ? item.getCategory().getName() : null)
                .featured(item.getFeatured())
                .quantityInStock(item.getQuantityInStock())
                .build();
    }

    private ItemResponse toItemResponse(Item item) {
        ItemImage primary = item.getPrimaryImage();

        List<ImageResponse> images = item.getImages().stream()
                .map(img -> ImageResponse.builder()
                        .id(img.getId())
                        .s3Url(img.getS3Url())
                        .altText(img.getAltText())
                        .isPrimary(img.getIsPrimary())
                        .sortOrder(img.getSortOrder())
                        .build())
                .collect(Collectors.toList());

        CategorySummary categorySummary = null;
        if (item.getCategory() != null) {
            categorySummary = CategorySummary.builder()
                    .id(item.getCategory().getId())
                    .name(item.getCategory().getName())
                    .description(item.getCategory().getDescription())
                    .build();
        }

        return ItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .slug(item.getSlug())
                .description(item.getDescription())
                .pricePerDay(item.getPricePerDay())
                .quantityInStock(item.getQuantityInStock())
                .minRentalDays(item.getMinRentalDays())
                .dimensions(item.getDimensions())
                .color(item.getColor())
                .material(item.getMaterial())
                .careInstructions(item.getCareInstructions())
                .active(item.getActive())
                .featured(item.getFeatured())
                .category(categorySummary)
                .images(images)
                .primaryImageUrl(primary != null ? primary.getS3Url() : null)
                .createdAt(item.getCreatedAt())
                .build();
    }

    private ItemCategory findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private String resolveSlug(String requestedSlug, String name, UUID existingId) {
        String slug = (requestedSlug != null && !requestedSlug.isBlank())
                ? slugify(requestedSlug)
                : slugify(name);

        boolean duplicate = (existingId == null)
                ? itemRepository.existsBySlug(slug)
                : itemRepository.existsBySlugAndIdNot(slug, existingId);

        if (duplicate) {
            throw new DuplicateResourceException("An item with slug '" + slug + "' already exists");
        }
        return slug;
    }

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input.toLowerCase().trim(), Normalizer.Form.NFD);
        return NON_ALPHANUMERIC.matcher(normalized).replaceAll("-")
                .replaceAll("^-|-$", "");
    }
}
