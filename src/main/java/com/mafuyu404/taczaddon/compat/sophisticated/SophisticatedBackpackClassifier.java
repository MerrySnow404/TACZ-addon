package com.mafuyu404.taczaddon.compat.sophisticated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classifies a runtime Sophisticated backpack wrapper without assuming that a
 * concrete implementation class name stays stable across generations.
 *
 * <p>Classification runs inside the optional Sophisticated boundary and only
 * inspects the wrapper's own class:
 *
 * <ol>
 *     <li>a known generation-specific wrapper class name, or</li>
 *     <li>any type referenced by the wrapper class itself (declared interfaces,
 *     fields and method signatures) that belongs to a linked-storage
 *     namespace.</li>
 * </ol>
 *
 * <p>The wrapper's inherited {@code IBackpackWrapper} interface declares
 * linked-storage members on 3.26 for every backpack, so inherited members must
 * never be used for classification. Only members and interfaces declared on the
 * concrete wrapper class count.
 *
 * <p>{@link Kind#UNCLASSIFIED} means the shape could not be inspected. Callers
 * must fail closed for that backpack instead of falling back to ordinary
 * contents semantics.
 */
final class SophisticatedBackpackClassifier {
    /**
     * Wrapper classes known to carry linked-storage contents on a specific
     * generation. Kept only as a fast path; the structural scan below is the
     * generation-independent safety net.
     */
    private static final Set<String> KNOWN_LINKED_WRAPPERS = Set.of(
            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper."
                    + "LinkedStorageBackpackWrapper"
    );

    /**
     * Marks any type that belongs to linked-storage machinery. Matching is
     * package/substring based so a future generation that keeps the naming
     * convention still fails closed instead of looking ordinary.
     */
    private static final String LINKED_TYPE_MARKER = "linkedstorage";

    /**
     * Wrapper superclasses above this boundary are shared by ordinary
     * backpacks and must not contribute linked markers.
     */
    private static final Set<String> SHARED_WRAPPER_TYPES = Set.of(
            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper."
                    + "IBackpackWrapper"
    );

    private static final Map<Class<?>, Kind> CACHE =
            new ConcurrentHashMap<>();

    private SophisticatedBackpackClassifier() {
    }

    enum Kind {
        /** Ordinary contents semantics (BackpackStorage + contents UUID). */
        ORDINARY,
        /** Linked-storage contents: never use ordinary contents semantics. */
        LINKED,
        /** Shape could not be inspected: fail closed for this backpack. */
        UNCLASSIFIED
    }

    /**
     * What the addon may do with one backpack.
     *
     * <p>Both storage families are legitimate: an ordinary backpack keeps its
     * contents in BackpackStorage, while a healthy linked backpack's inventory
     * handler proxies to the canonical linked contents, so mutation through
     * that handler is exactly what Sophisticated itself does. Everything that
     * cannot be proven falls into a blocked state instead.
     */
    enum Access {
        /** Ordinary contents semantics: allowed to read, mutate and publish. */
        ORDINARY,
        /** Known healthy linked contents: mutate through the handler. */
        KNOWN_LINKED,
        /** Linked endpoint data is present but unusable. */
        MALFORMED_LINKED,
        /** The wrapper shape or generation could not be verified. */
        UNKNOWN,
        /** The installed generation is not a verified Sophisticated generation. */
        UNSUPPORTED_GENERATION,
        /** The verified linked backend itself broke at runtime. */
        LINKAGE_BROKEN;

        /** Whether the backpack may be read at all. */
        boolean readable() {
            return this == ORDINARY || this == KNOWN_LINKED;
        }

        /** Whether ammo may be extracted from this backpack. */
        boolean mutationAllowed() {
            return readable();
        }

        /** Whether ordinary BackpackStorage/contents-UUID semantics apply. */
        boolean ordinaryContents() {
            return this == ORDINARY;
        }

        /** Whether the linked snapshot protocol applies. */
        boolean linkedContents() {
            return this == KNOWN_LINKED;
        }
    }

    /**
     * Resolves the access policy for one wrapper.
     *
     * @param kind     structural wrapper classification
     * @param endpoint endpoint resolution reported by the linked bridge
     * @param generation detected Sophisticated generation
     */
    static Access resolveAccess(
            Kind kind,
            EndpointResolution endpoint,
            SophisticatedBackpackGeneration generation
    ) {
        if (kind == null || endpoint == null || generation == null) {
            return Access.UNKNOWN;
        }
        if (generation == SophisticatedBackpackGeneration.UNKNOWN) {
            /*
             * Neither the ordinary nor the linked contract was verified, so no
             * storage family may be assumed for any backpack. This is an
             * unknown-API state, not a linked-capability failure: the ordinary
             * backend itself cannot be trusted to link.
             */
            return Access.UNSUPPORTED_GENERATION;
        }
        /*
         * The wrapper's own shape decides which capability is at stake. A
         * broken linked bridge may only close linked-specific capability: a
         * wrapper that is independently proven ordinary keeps working, which is
         * the core isolation rule of this compatibility layer.
         */
        return switch (kind) {
            case ORDINARY -> {
                if (endpoint.kind()
                        == ResolutionKind
                        .MALFORMED_LINKED) {
                    /*
                     * The upstream classifier reports linked contents for this
                     * stack, which contradicts the ordinary wrapper shape.
                     */
                    yield Access.MALFORMED_LINKED;
                }
                if (endpoint.linked()) {
                    /*
                     * The verified linked bridge owns the classification of
                     * the stack it was asked about, so an ordinary-shaped
                     * wrapper holding a linked endpoint still routes through
                     * the linked contents.
                     */
                    yield generation.linkedStorageSupported()
                            ? Access.KNOWN_LINKED
                            : Access.UNKNOWN;
                }
                yield Access.ORDINARY;
            }
            case LINKED -> {
                if (!generation.linkedStorageSupported()) {
                    yield Access.UNKNOWN;
                }
                if (endpoint.kind()
                        == ResolutionKind
                        .MALFORMED_LINKED) {
                    yield Access.MALFORMED_LINKED;
                }
                if (endpoint.linked()) {
                    yield Access.KNOWN_LINKED;
                }
                if (endpoint.bridgeUnavailable()) {
                    /*
                     * The verified linked backend broke after being detected.
                     * Endpoints can no longer be told from ordinary stacks, so
                     * linked wrappers stop here.
                     */
                    yield Access.LINKAGE_BROKEN;
                }
                /*
                 * Linked-shaped wrapper whose stack is not an endpoint (the
                 * canonical host wrapper). Mutation through the wrapper
                 * handler is the documented upstream behavior.
                 */
                yield Access.KNOWN_LINKED;
            }
            case UNCLASSIFIED -> {
                /*
                 * Endpoint classification alone does not prove that an unknown
                 * concrete wrapper exposes the verified mutation semantics.
                 *
                 * In particular, a stack may carry a perfectly valid linked-storage
                 * endpoint while wrapper inspection failed because its runtime shape
                 * belongs to an unknown generation. Promoting that state to
                 * KNOWN_LINKED would turn a storage-family observation into an ABI
                 * guarantee that we do not actually have.
                 *
                 * Therefore every unclassified wrapper fails closed. Preserve
                 * MALFORMED_LINKED only because it is a stronger diagnostic state.
                 */
                if (endpoint.kind()
                        == ResolutionKind
                        .MALFORMED_LINKED) {
                    yield Access.MALFORMED_LINKED;
                }
                yield Access.UNKNOWN;
            }
        };
    }

    static Kind classify(Object wrapper) {
        if (wrapper == null) {
            return Kind.UNCLASSIFIED;
        }
        Class<?> type = wrapper.getClass();
        Kind cached = CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        Kind resolved = resolve(type);
        CACHE.put(type, resolved);
        return resolved;
    }

    private static Kind resolve(Class<?> type) {
        if (KNOWN_LINKED_WRAPPERS.contains(type.getName())) {
            return Kind.LINKED;
        }
        try {
            if (referencesLinkedType(type)) {
                return Kind.LINKED;
            }
        } catch (LinkageError | RuntimeException unreadable) {
            /*
             * A wrapper whose own shape cannot be resolved is not proven
             * ordinary, so it must not reach the contents-UUID protocol.
             */
            return Kind.UNCLASSIFIED;
        }
        return Kind.ORDINARY;
    }

    private static boolean referencesLinkedType(Class<?> type) {
        for (Class<?> declared : type.getInterfaces()) {
            if (isLinkedType(declared.getName())) {
                return true;
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (isLinkedType(field.getType().getName())) {
                return true;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isLinkedType(
                    method.getReturnType().getName()
            )) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (isLinkedType(parameter.getName())) {
                    return true;
                }
            }
        }
        /*
         * Walk the concrete superclass chain until the shared wrapper
         * boundary, so a future subclass of an ordinary wrapper stays
         * ordinary while a linked host subclass is still detected.
         */
        Class<?> parent = type.getSuperclass();
        while (parent != null
                && !SHARED_WRAPPER_TYPES.contains(parent.getName())) {
            if (KNOWN_LINKED_WRAPPERS.contains(parent.getName())) {
                return true;
            }
            for (Class<?> declared : parent.getInterfaces()) {
                if (isLinkedType(declared.getName())) {
                    return true;
                }
            }
            parent = parent.getSuperclass();
        }
        return false;
    }

    private static boolean isLinkedType(String binaryName) {
        return binaryName != null
                && binaryName.toLowerCase(java.util.Locale.ROOT)
                .contains(LINKED_TYPE_MARKER);
    }
}
