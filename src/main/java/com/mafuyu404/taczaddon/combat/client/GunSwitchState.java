package com.mafuyu404.taczaddon.combat.client;

import java.util.*;

public final class GunSwitchState {
    private GunSwitchState() {}

    private static List<String> gunSwitchList = List.of();

    public static void clear() {
        gunSwitchList = List.of();
    }

    public static List<String> getGunSwitchList() {
        return gunSwitchList;
    }

    public static void setGunSwitchList(List<String> gunIds) {
        gunSwitchList = List.copyOf(gunIds);
    }
}
