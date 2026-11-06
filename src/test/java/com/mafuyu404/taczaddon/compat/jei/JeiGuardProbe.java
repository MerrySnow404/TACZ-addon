package com.mafuyu404.taczaddon.compat.jei;

public final class JeiGuardProbe {
    private JeiGuardProbe() {}
    public static boolean runGuarded(java.util.function.BooleanSupplier action) { return JeiCompat.runGuarded(action); }
}
