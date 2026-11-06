package com.mafuyu404.taczaddon.compat.curios;

public final class CuriosGuardProbe {
    private CuriosGuardProbe() {}
    public static boolean runGuarded(java.util.function.BooleanSupplier action) { return CuriosCompat.runGuarded(action); }
}
