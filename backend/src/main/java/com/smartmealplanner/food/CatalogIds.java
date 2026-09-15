package com.smartmealplanner.food;

import java.nio.ByteBuffer;
import java.util.UUID;

/** UUID/BINARY(16) conversion shared only inside the Food catalog module. */
final class CatalogIds {

    private CatalogIds() {
    }

    static byte[] uuidToBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
