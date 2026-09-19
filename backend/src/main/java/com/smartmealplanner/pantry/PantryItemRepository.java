package com.smartmealplanner.pantry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    @Query("""
            select item
            from PantryItem item
            where item.publicId = :publicId
              and item.userId = :userId
            """)
    Optional<PantryItem> findByPublicIdAndUserId(
            @Param("publicId") byte[] publicId,
            @Param("userId") Long userId);

    @Query("""
            select item
            from PantryItem item
            where item.userId = :userId
              and item.status in :statuses
            order by case when item.expiryDate is null then 1 else 0 end asc,
                     item.expiryDate asc,
                     item.createdAt asc,
                     item.publicId asc
            """)
    List<PantryItem> findByUserIdAndStatuses(
            @Param("userId") Long userId,
            @Param("statuses") Collection<PantryItemStatus> statuses);

    @Query("""
            select item
            from PantryItem item
            where item.userId = :userId
            order by case when item.expiryDate is null then 1 else 0 end asc,
                     item.expiryDate asc,
                     item.createdAt asc,
                     item.publicId asc
            """)
    List<PantryItem> findByUserId(
            @Param("userId") Long userId);

    @Query("""
            select item
            from PantryItem item
            where item.userId = :userId
              and item.status = :status
            order by case when item.expiryDate is null then 1 else 0 end asc,
                     item.expiryDate asc,
                     item.createdAt asc,
                     item.publicId asc
            """)
    List<PantryItem> findAvailableByUserId(
            @Param("userId") Long userId,
            @Param("status") PantryItemStatus status);
}
