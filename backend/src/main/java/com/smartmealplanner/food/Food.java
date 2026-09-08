package com.smartmealplanner.food;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;

/** Representative P2 mapping; not a catalog CRUD implementation. */
@Entity
@Table(name = "foods")
class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_category_id")
    private FoodCategory category;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Food() {}

    Food(String displayName, FoodCategory category) {
        rename(displayName);
        this.category = category;
        UUID uuid = UUID.randomUUID();
        this.publicId = ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    void rename(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Invalid food name");
        }
        displayName = name;
    }

    Long internalId() { return id; }
    byte[] publicIdBytes() { return publicId.clone(); }
    UUID publicId() {
        ByteBuffer bytes = ByteBuffer.wrap(publicId);
        return new UUID(bytes.getLong(), bytes.getLong());
    }
    String displayName() { return displayName; }
    FoodCategory category() { return category; }
    Long version() { return version; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
}
