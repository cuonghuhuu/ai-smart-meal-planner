package com.smartmealplanner.mealplanning.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

final class PublicIds {
    private PublicIds() { }

    static byte[] bytes(UUID id) {
        if (id == null) { throw new IllegalArgumentException("publicId is required"); }
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }

    static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
