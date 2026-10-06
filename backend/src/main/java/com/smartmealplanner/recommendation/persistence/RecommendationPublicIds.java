package com.smartmealplanner.recommendation.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

final class RecommendationPublicIds {
    private RecommendationPublicIds() { }

    static byte[] bytes(UUID id) {
        if (id == null) { throw new IllegalArgumentException("publicId is required"); }
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }

    static UUID uuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("publicId bytes must contain 16 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
