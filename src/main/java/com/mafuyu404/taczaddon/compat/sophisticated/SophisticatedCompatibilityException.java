package com.mafuyu404.taczaddon.compat.sophisticated;

/**
 * Raised when a reflection operation that exists purely to bridge or probe the optional
 * Sophisticated ABI fails.
 *
 * <p>The {@link SophisticatedRuntime} guard converts this exception into a BROKEN capability state
 * and a neutral fallback. It is not used to hide ordinary TACZAddon programming errors.
 */
public class SophisticatedCompatibilityException extends RuntimeException {

    public SophisticatedCompatibilityException(String message) {
        super(message);
    }

    public SophisticatedCompatibilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
