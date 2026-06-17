package com.laulain.rentals.repository;

import com.laulain.rentals.model.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {

    List<ItemCategory> findByActiveTrueOrderBySortOrderAscNameAsc();

    @Query("""
        SELECT c FROM ItemCategory c
        WHERE c.active = true
          AND EXISTS (SELECT i FROM Item i WHERE i.category = c AND i.active = true)
        ORDER BY c.sortOrder ASC, c.name ASC
        """)
    List<ItemCategory> findCategoriesWithActiveItems();

    boolean existsByNameIgnoreCase(String name);
}
