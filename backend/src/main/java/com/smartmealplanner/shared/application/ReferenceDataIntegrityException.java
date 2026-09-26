package com.smartmealplanner.shared.application;

/**
 * Signals that a reference-query boundary returned structurally inconsistent
 * persisted data.
 *
 * <p>This remains an {@link IllegalStateException} subtype for compatibility
 * with existing callers, while giving application boundaries a precise type
 * they can handle without masking infrastructure failures.</p>
 */
public class ReferenceDataIntegrityException extends IllegalStateException {

    public ReferenceDataIntegrityException(String message) {
        super(message);
    }
}
