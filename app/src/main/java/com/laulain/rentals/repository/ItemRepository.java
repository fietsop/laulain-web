package com.laulain.rentals.repository;

import com.laulain.rentals.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    // ---- Public catalog queries ----

    Optional<Item> findBySlugAndActiveTrue(String slug);

    List<Item> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Item> findByCategoryIdAndActiveTrueOrderBySortOrderAsc(UUID categoryId);

    List<Item> findByFeaturedTrueAndActiveTrueOrderBySortOrderAsc();

    Page<Item> findByActiveTrue(Pageable pageable);

    // ---- Category-filtered paged query ----
    @Query("""
        SELECT i FROM Item i
        JOIN FETCH i.category c
        LEFT JOIN FETCH i.images img
        WHERE i.active = true
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY i.sortOrder ASC, i.name ASC
        """)
    Page<Item> findCatalog(
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            Pageable pageable
    );

    // ---- Check available quantity on a date range ----
    @Query("""
        SELECT i FROM Item i
        WHERE i.active = true
          AND i.id = :itemId
          AND (i.quantityInStock - COALESCE(
              (SELECT SUM(ab.quantity) FROM AvailabilityBlock ab
               WHERE ab.item.id = :itemId
                 AND ab.blockDate BETWEEN :startDate AND :endDate),
              0)) >= :requestedQty
        """)
    Optional<Item> findAvailableItem(
            @Param("itemId") UUID itemId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("requestedQty") int requestedQty
    );

    // ---- Admin queries ----
    Page<Item> findAll(Pageable pageable);

    @Query("SELECT i FROM Item i JOIN FETCH i.category WHERE i.id = :id")
    Optional<Item> findByIdWithCategory(@Param("id") UUID id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
