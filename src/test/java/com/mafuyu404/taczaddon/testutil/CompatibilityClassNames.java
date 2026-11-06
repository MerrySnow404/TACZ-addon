package com.mafuyu404.taczaddon.testutil;

public final class CompatibilityClassNames {
    private CompatibilityClassNames() {}
    public static String compatClassName(String simpleName) {
        String group;
        if (simpleName.startsWith("Sophisticated")) group = "sophisticated";
        else if (simpleName.startsWith("CreateStorage")) group = "createstorage";
        else if (simpleName.startsWith("Beyond")) group = "beyond";
        else if (simpleName.startsWith("Curios")) group = "curios";
        else if (simpleName.startsWith("Jei")) group = "jei";
        else if (simpleName.startsWith("ShoulderSurfing") || simpleName.startsWith("Perspective") || simpleName.startsWith("Leawind")) group = "camera";
        else group = "api";
        return "com.mafuyu404.taczaddon.compat." + group + "." + simpleName;
    }
}
