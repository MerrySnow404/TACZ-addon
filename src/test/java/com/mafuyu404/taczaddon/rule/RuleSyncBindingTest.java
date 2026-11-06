package com.mafuyu404.taczaddon.rule;

import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleSyncBindingTest {
    @BeforeAll
    static void bootstrap() throws Exception { MinecraftTestBootstrap.prepare(); }

    @Test
    void callbacksForwardEachRulesCurrentValueToItsOwnSyncBinding() throws Exception {
        var detailField = RuleRegistry.class.getDeclaredField("detailChanged");
        var liberateField = RuleRegistry.class.getDeclaredField("liberateChanged");
        detailField.setAccessible(true);
        liberateField.setAccessible(true);
        Object previousDetail = detailField.get(null);
        Object previousLiberate = liberateField.get(null);
        List<Boolean> detail = new ArrayList<>();
        List<Boolean> liberate = new ArrayList<>();
        try {
            RuleRegistry.bindSync((server, enabled) -> detail.add(enabled),
                    (server, enabled) -> liberate.add(enabled));
            GameRules rules = new GameRules();
            var detailCallback = RuleRegistry.class.getDeclaredMethod("onShowAttachmentDetailChanged",
                    MinecraftServer.class, GameRules.BooleanValue.class);
            var liberateCallback = RuleRegistry.class.getDeclaredMethod("onLiberateAttachmentChanged",
                    MinecraftServer.class, GameRules.BooleanValue.class);
            detailCallback.setAccessible(true);
            liberateCallback.setAccessible(true);
            for (boolean value : new boolean[]{true, false}) {
                var detailRule = rules.getRule(RuleRegistry.SHOW_ATTACHMENT_DETAIL);
                detailRule.set(value, null);
                detailCallback.invoke(null, null, detailRule);
                var liberateRule = rules.getRule(RuleRegistry.LIBERATE_ATTACHMENT);
                liberateRule.set(!value, null);
                liberateCallback.invoke(null, null, liberateRule);
            }
            assertEquals(List.of(true, false), detail);
            assertEquals(List.of(false, true), liberate);
        } finally {
            detailField.set(null, previousDetail);
            liberateField.set(null, previousLiberate);
        }
    }
}
