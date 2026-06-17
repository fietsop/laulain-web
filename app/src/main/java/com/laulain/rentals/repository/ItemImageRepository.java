package com.laulain.rentals.repository;

import com.laulain.rentals.model.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

    List<ItemImage> findByItemIdOrderBySortOrderAsc(UUID itemId);

    @Modifying
    @Query("UPDATE ItemImage img SET img.isPrimary = false WHERE img.item.id = :itemId")
    void clearPrimaryFlag(@Param("itemId") UUID itemId);

    @Modifying
    @Query("DELETE FROM ItemImage img WHERE img.item.id = :itemId")
    void deleteAllByItemId(@Param("itemId") UUID itemId);
}
