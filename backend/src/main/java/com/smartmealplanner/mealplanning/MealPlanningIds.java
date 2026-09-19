package com.smartmealplanner.mealplanning;

import java.nio.ByteBuffer;
import java.util.UUID;

final class MealPlanningIds {
    private MealPlanningIds() {
    }

    static byte[] uuidToBytes(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("publicId is required");
        }
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    static UUID bytesToUuid(byte[] value) {
        if (value == null || value.length != 16) {
            throw new IllegalStateException("publicId is invalid");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
