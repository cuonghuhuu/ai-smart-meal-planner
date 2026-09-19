package com.smartmealplanner.pantry;

import java.nio.ByteBuffer;
import java.util.UUID;

/** UUID/BINARY(16) conversion kept inside the Pantry module. */
final class PantryIds {

    private PantryIds() {
    }

    static byte[] uuidToBytes(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID is required");
        }
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    static UUID bytesToUuid(byte[] value) {
        if (value == null || value.length != 16) {
            throw new IllegalStateException("Pantry public_id is invalid");
        }
        ByteBuffer bytes = ByteBuffer.wrap(value);
        return new UUID(bytes.getLong(), bytes.getLong());
    }
}
