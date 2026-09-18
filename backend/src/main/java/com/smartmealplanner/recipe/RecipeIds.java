package com.smartmealplanner.recipe;

import java.nio.ByteBuffer;
import java.util.UUID;

/** UUID/BINARY(16) conversion local to the Recipe module. */
final class RecipeIds {

    private RecipeIds() {
    }

    static byte[] uuidToBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    static UUID bytesToUuid(byte[] value) {
        if (value == null || value.length != 16) {
            throw new IllegalArgumentException("publicId must contain 16 bytes");
        }
        ByteBuffer bytes = ByteBuffer.wrap(value);
        return new UUID(bytes.getLong(), bytes.getLong());
    }
}
