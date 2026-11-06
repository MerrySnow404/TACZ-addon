package com.mafuyu404.taczaddon.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-authoritative nearby-container source policy shared by supported
 * TaCZ workstation and refit integrations.
 */
public final class CommonConfig {
    public static final int MIN_CONTAINER_SCAN_RADIUS = 1;
    public static final int MAX_CONTAINER_SCAN_RADIUS = 16;
    public static final int MIN_BATCH_CRAFT = 1;
    public static final int MAX_BATCH_CRAFT = 64;

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAINER_READER;
    public static final ForgeConfigSpec.IntValue CONTAINER_SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue BATCH_CRAFT_MAX;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SHOOT_WHILE_RELOADING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FAST_SWAP_GUN;

    static {
        ForgeConfigSpec.Builder builder =
                new ForgeConfigSpec.Builder();

        builder.push("GunSmithTable");

        ENABLE_CONTAINER_READER = builder
                .comment(
                        "Allow supported TaCZ crafting/refit features to use",
                        "nearby loaded block inventories. When false, player",
                        "inventory sources remain available but nearby block",
                        "sources are disabled for both gunsmith crafting and",
                        "the refit external candidate list. Carried Sophisticated",
                        "Backpacks remain available for refitting."
                )
                .define("enableContainerReader", true);

        CONTAINER_SCAN_RADIUS = builder
                .comment(
                        "Horizontal radius around the authoritative anchor.",
                        "Gunsmith uses the workbench root position; refit uses",
                        "the server player position. Only loaded positions",
                        "from Y-1 through Y+1 are inspected."
                )
                .defineInRange(
                        "containerScanRadius",
                        3,
                        MIN_CONTAINER_SCAN_RADIUS,
                        MAX_CONTAINER_SCAN_RADIUS
                );

        BATCH_CRAFT_MAX = builder
                .comment(
                        "Maximum number of craft executions accepted from one shift-click request."
                )
                .defineInRange(
                        "batchCraftMax",
                        64,
                        MIN_BATCH_CRAFT,
                        MAX_BATCH_CRAFT
                );

        builder.pop();

        builder.push("Gameplay");

        ENABLE_SHOOT_WHILE_RELOADING = builder
                .comment(
                        "Allow shooting to immediately interrupt a tactical reload",
                        "when the gun still has fireable ammunition."
                )
                .define("enableShootWhileReloading", true);

        ENABLE_FAST_SWAP_GUN = builder
                .comment(
                        "Skip TaCZ's put-away animation before the next draw.",
                        "This is a gameplay policy owned by the server: the value",
                        "is synchronized to every client, and client-side cooldown",
                        "prediction uses the synchronized value. A dedicated server",
                        "reads this file, never a client configuration."
                )
                .define("enableFastSwapGun", true);

        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    public static boolean enableContainerReader() {
        return ENABLE_CONTAINER_READER.get();
    }

    public static int getContainerScanRadius() {
        return CONTAINER_SCAN_RADIUS.get();
    }

    /**
     * Neutral name for the shared nearby-container source policy.
     */
    public static boolean enableNearbyContainerSources() {
        return ENABLE_CONTAINER_READER.get();
    }

    /**
     * Neutral name for the shared nearby-container source scan radius.
     */
    public static int getNearbyContainerScanRadius() {
        return CONTAINER_SCAN_RADIUS.get();
    }

    public static int getBatchCraftMax() {
        return BATCH_CRAFT_MAX.get();
    }

    public static boolean enableShootWhileReloading() {
        return ENABLE_SHOOT_WHILE_RELOADING.get();
    }

    public static boolean enableFastSwapGun() {
        return ENABLE_FAST_SWAP_GUN.get();
    }
}
