package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Structural reflection bridge for the volatile {@code PlayerInventoryProvider.runOnBackpacks} ABI.
 *
 * <p>Sophisticated Backpacks <= 3.25.x declares {@code runOnBackpacks(Player,
 * BackpackInventorySlotConsumer)} with return descriptor {@code V}; 3.26.0+ changed it to {@code
 * boolean}. Reflection resolves the Java-level method by name and parameter types, so both
 * generations can be supported by one TACZAddon jar.
 *
 * <p>The complete callback contract is also validated during construction. Runtime argument casting
 * therefore never acts as ABI discovery: an incompatible consumer interface fails during capability
 * probing instead and is converted by SophisticatedRuntime into a BROKEN capability.
 */
public final class PlayerInventoryProviderBridge {

    @FunctionalInterface
    public interface BackpackVisitor {
        /**
         * @return true to stop Sophisticated Backpacks' enumeration
         */
        boolean visit(ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
    }

    private static final String PROVIDER_CLASS_NAME =
            "net.p3pp3rf1y.sophisticatedbackpacks.util." + "PlayerInventoryProvider";

    private static final String CONSUMER_SUFFIX = "$BackpackInventorySlotConsumer";

    private static final String RUN_METHOD_NAME = "runOnBackpacks";

    private static final String ACCEPT_METHOD_NAME = "accept";

    private final ClassLoader loader;
    private final Class<?> consumerClass;

    private final Object providerInstance;

    private final Method runOnBackpacks;
    private final Method consumerAccept;

    public static PlayerInventoryProviderBridge createDefault() {
        return new PlayerInventoryProviderBridge(
                PlayerInventoryProviderBridge.class.getClassLoader(), PROVIDER_CLASS_NAME);
    }

    /** Package-private test seam. Production uses {@link #createDefault()}. */
    PlayerInventoryProviderBridge(ClassLoader loader, String providerClassName) {
        this.loader = loader;

        try {
            Class<?> providerClass = Class.forName(providerClassName, true, loader);

            this.consumerClass = Class.forName(providerClassName + CONSUMER_SUFFIX, true, loader);

            validateConsumerType();

            Method getProvider = providerClass.getMethod("get");

            validateProviderGetter(providerClass, getProvider);

            Object provider = getProvider.invoke(null);

            if (provider == null) {
                throw new SophisticatedCompatibilityException(
                        providerClassName + ".get() returned null");
            }

            this.consumerAccept =
                    consumerClass.getMethod(
                            ACCEPT_METHOD_NAME,
                            ItemStack.class,
                            String.class,
                            String.class,
                            int.class);

            validateConsumerAccept(consumerAccept);

            this.runOnBackpacks =
                    providerClass.getMethod(RUN_METHOD_NAME, Player.class, consumerClass);

            validateRunOnBackpacks(runOnBackpacks);

            this.providerInstance = provider;
        } catch (SophisticatedCompatibilityException exception) {
            throw exception;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new SophisticatedCompatibilityException(
                    "Could not resolve the Sophisticated Backpacks "
                            + "PlayerInventoryProvider bridge contract for "
                            + providerClassName,
                    exception);
        }
    }

    private void validateConsumerType() {
        if (!consumerClass.isInterface()) {
            throw new SophisticatedCompatibilityException(
                    consumerClass.getName() + " is no longer an interface");
        }
    }

    private static void validateProviderGetter(Class<?> providerClass, Method getter) {
        if (!Modifier.isStatic(getter.getModifiers())) {
            throw new SophisticatedCompatibilityException(
                    providerClass.getName() + ".get() is no longer static");
        }

        if (!providerClass.isAssignableFrom(getter.getReturnType())) {
            throw new SophisticatedCompatibilityException(
                    providerClass.getName()
                            + ".get() has unexpected return type "
                            + getter.getReturnType().getName());
        }
    }

    private static void validateConsumerAccept(Method accept) {
        if (Modifier.isStatic(accept.getModifiers())) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) " + "is unexpectedly static");
        }

        if (accept.getReturnType() != boolean.class) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) "
                            + "has unexpected return type "
                            + accept.getReturnType().getName());
        }
    }

    /**
     * Deliberately accepts both the known historical {@code void} return and the current {@code
     * boolean} return, but fails closed for any other contract.
     */
    private static void validateRunOnBackpacks(Method method) {
        Class<?> returnType = method.getReturnType();

        if (returnType != void.class && returnType != boolean.class) {
            throw new SophisticatedCompatibilityException(
                    "PlayerInventoryProvider.runOnBackpacks(...) "
                            + "has unsupported return type "
                            + returnType.getName());
        }

        if (Modifier.isStatic(method.getModifiers())) {
            throw new SophisticatedCompatibilityException(
                    "PlayerInventoryProvider.runOnBackpacks(...) " + "is unexpectedly static");
        }
    }

    /**
     * Invokes the cached two-argument runOnBackpacks overload.
     *
     * <p>The result is intentionally ignored. This keeps both:
     *
     * <ul>
     *   <li>3.25.x: {@code (...)V}
     *   <li>3.26.x: {@code (...)Z}
     * </ul>
     *
     * binary generations compatible with the same bridge.
     */
    public void forEachBackpack(Player player, BackpackVisitor visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("BackpackVisitor must not be null");
        }

        Object consumer = newConsumer(visitor);

        try {
            runOnBackpacks.invoke(providerInstance, player, consumer);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                /*
                 * In particular, LinkageError remains intact here so
                 * SophisticatedRuntime can mark the owning capability BROKEN.
                 */
                throw error;
            }

            throw new SophisticatedCompatibilityException(
                    "Unexpected checked failure inside runOnBackpacks", cause);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new SophisticatedCompatibilityException(
                    "runOnBackpacks invocation no longer matches " + "the cached bridge contract",
                    exception);
        }
    }

    private Object newConsumer(BackpackVisitor visitor) {
        InvocationHandler handler =
                (proxy, method, args) -> {

                    /*
                     * Compare the complete reflected Method, not merely its
                     * name. A future overload or altered accept(...) contract
                     * must never accidentally enter this branch.
                     */
                    if (method.equals(consumerAccept)) {
                        return invokeVisitor(method, args, visitor);
                    }

                    /*
                     * java.lang.Object methods are expected on every proxy
                     * even though they are not part of the Sophisticated
                     * consumer contract.
                     */
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> args != null && args.length == 1 && proxy == args[0];

                            case "hashCode" -> System.identityHashCode(proxy);

                            case "toString" -> "TaczAddonBackpackInventorySlotConsumerProxy";

                            default ->
                                    throw new SophisticatedCompatibilityException(
                                            "Unexpected Object method on "
                                                    + "BackpackInventorySlotConsumer proxy: "
                                                    + method);
                        };
                    }

                    /*
                     * Fail closed if Sophisticated adds another method to the
                     * callback interface and starts invoking it. Returning null
                     * here could otherwise hide an ABI/semantic change until a
                     * later unrelated failure.
                     */
                    throw new SophisticatedCompatibilityException(
                            "Unexpected BackpackInventorySlotConsumer "
                                    + "method invoked: "
                                    + method);
                };

        try {
            return Proxy.newProxyInstance(loader, new Class<?>[] {consumerClass}, handler);
        } catch (IllegalArgumentException | LinkageError exception) {
            throw new SophisticatedCompatibilityException(
                    "Could not create a proxy for " + consumerClass.getName(), exception);
        }
    }

    private static boolean invokeVisitor(Method method, Object[] args, BackpackVisitor visitor) {
        /*
         * The shape has already been reflected and validated, but validate
         * actual runtime arguments as well. Any mismatch is a compatibility
         * failure, not a raw ClassCastException escaping the integration
         * boundary.
         */
        if (args == null || args.length != 4) {
            throw new SophisticatedCompatibilityException("Unexpected arguments for " + method);
        }

        if (!(args[0] instanceof ItemStack backpack)) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) "
                            + "received a non-ItemStack backpack argument");
        }

        if (!(args[1] instanceof String inventoryHandlerName)) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) "
                            + "received a non-String inventory handler name");
        }

        if (!(args[2] instanceof String identifier)) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) "
                            + "received a non-String identifier");
        }

        if (!(args[3] instanceof Integer slot)) {
            throw new SophisticatedCompatibilityException(
                    "BackpackInventorySlotConsumer.accept(...) " + "received a non-int slot");
        }

        return visitor.visit(backpack, inventoryHandlerName, identifier, slot);
    }
}
