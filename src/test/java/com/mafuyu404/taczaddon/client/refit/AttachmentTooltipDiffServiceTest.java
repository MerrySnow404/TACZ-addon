package com.mafuyu404.taczaddon.client.refit;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit contract for the attachment tooltip attribute service.
 */
class AttachmentTooltipDiffServiceTest {

    private static AttachmentTooltipDiffService.PropertyDifference diff(
            String diagramKey,
            double delta,
            OptionalDouble relative
    ) {
        return new AttachmentTooltipDiffService.PropertyDifference(
                diagramKey,
                delta,
                relative
        );
    }

    private static AttachmentTooltipDiffService.PropertyDifference diff(
            String diagramKey,
            double delta
    ) {
        return diff(diagramKey, delta, OptionalDouble.empty());
    }

    @Test
    void diagramAndTooltipKeysShareOneNormalizationEntry() {
        assertEquals(
                "hipfire_inaccuracy",
                AttachmentTooltipDiffService.normalizePropertyKey(
                        "inaccuracy"
                )
        );
        assertEquals(
                "hipfire_inaccuracy",
                AttachmentTooltipDiffService.normalizePropertyKey(
                        "hipfire_inaccuracy"
                )
        );
        assertEquals(
                "hipfire_inaccuracy",
                AttachmentTooltipDiffService.normalizeDiagramKey(
                        "gui.tacz.gun_refit.property_diagrams.hipfire_inaccuracy"
                )
        );
        assertEquals(
                "hipfire_inaccuracy",
                AttachmentTooltipDiffService.normalizeTooltipKey(
                        "tooltip.tacz.attachment.inaccuracy.increase"
                )
        );

        assertNull(
                AttachmentTooltipDiffService.normalizeTooltipKey(
                        "tooltip.tacz.attachment.zoom"
                )
        );
        assertNull(
                AttachmentTooltipDiffService.normalizeDiagramKey(
                        "tooltip.tacz.attachment.ads.increase"
                )
        );
        assertNull(
                AttachmentTooltipDiffService.normalizePropertyKey(null)
        );
        assertNull(
                AttachmentTooltipDiffService.normalizePropertyKey("  ")
        );
    }

    @Test
    void otherPropertyKeysKeepTheirMeaning() {
        assertEquals(
                "aim_inaccuracy",
                AttachmentTooltipDiffService.normalizeTooltipKey(
                        "tooltip.tacz.attachment.aim_inaccuracy.decrease"
                )
        );
        assertEquals(
                "sneak_inaccuracy",
                AttachmentTooltipDiffService.normalizeTooltipKey(
                        "tooltip.tacz.attachment.sneak_inaccuracy.increase"
                )
        );
        assertEquals(
                "ads",
                AttachmentTooltipDiffService.normalizeTooltipKey(
                        "tooltip.tacz.attachment.ads.increase"
                )
        );
        assertFalse(
                AttachmentTooltipDiffService.isRatioProperty(
                        "hipfire_inaccuracy"
                )
        );
        assertTrue(
                AttachmentTooltipDiffService.isRatioProperty(
                        "aim_inaccuracy"
                )
        );
    }

    @Test
    void ratioPropertiesScaleToPercentWhileAdsKeepsSeconds() {
        assertEquals(
                "+10.00%",
                AttachmentTooltipDiffService.formatDifference(
                        "armor_ignore",
                        diff("gui.tacz.gun_refit.property_diagrams.armor_ignore", 0.10D)
                )
        );
        assertEquals(
                "+10.00%",
                AttachmentTooltipDiffService.formatDifference(
                        "aim_inaccuracy",
                        diff("gui.tacz.gun_refit.property_diagrams.aim_inaccuracy", 0.10D)
                )
        );
        assertEquals(
                "+0.10s",
                AttachmentTooltipDiffService.formatDifference(
                        "ads",
                        diff("gui.tacz.gun_refit.property_diagrams.ads",
                                0.10D)
                )
        );
    }

    @Test
    void remainingUnitsArePreserved() {
        assertEquals(
                "-1.25kg",
                AttachmentTooltipDiffService.formatDifference(
                        "weight",
                        diff("gui.tacz.gun_refit.property_diagrams.weight",
                                -1.25D)
                )
        );
        assertEquals(
                "+30.00rpm",
                AttachmentTooltipDiffService.formatDifference(
                        "rpm",
                        diff("gui.tacz.gun_refit.property_diagrams.rpm",
                                30.0D)
                )
        );
        assertEquals(
                "+5.00m",
                AttachmentTooltipDiffService.formatDifference(
                        "effective_range",
                        diff("gui.tacz.gun_refit.property_diagrams.effective_range", 5.0D)
                )
        );
        assertEquals(
                "-0.02",
                AttachmentTooltipDiffService.formatDifference(
                        "hipfire_inaccuracy",
                        diff("gui.tacz.gun_refit.property_diagrams.hipfire_inaccuracy", -0.02D)
                )
        );
    }

    @Test
    void negativeZeroAndNonFiniteValuesAreNeverRendered() {
        assertEquals(
                "0.00%",
                AttachmentTooltipDiffService.formatDifference(
                        "armor_ignore",
                        diff("gui.tacz.gun_refit.property_diagrams.armor_ignore", -0.0D)
                )
        );
        assertNull(
                AttachmentTooltipDiffService.formatDifference(
                        "weight",
                        diff("gui.tacz.gun_refit.property_diagrams.weight",
                                Double.NaN)
                )
        );
        assertNull(
                AttachmentTooltipDiffService.formatDifference(
                        "weight",
                        diff("gui.tacz.gun_refit.property_diagrams.weight",
                                Double.NEGATIVE_INFINITY)
                )
        );
    }

    @Test
    void relativePercentUsesTheRealBaseline() {
        assertEquals(
                25.0D,
                AttachmentTooltipDiffService.relativePercent(
                        4.0D,
                        5.0D
                ).orElseThrow(),
                1.0E-9D
        );
        assertEquals(
                -50.0D,
                AttachmentTooltipDiffService.relativePercent(
                        -4.0D,
                        -6.0D
                ).orElseThrow(),
                1.0E-9D
        );

        assertTrue(
                AttachmentTooltipDiffService.relativePercent(
                        0.0D,
                        3.0D
                ).isEmpty(),
                "zero baseline has no relative change"
        );
        assertTrue(
                AttachmentTooltipDiffService.relativePercent(
                        -0.0D,
                        3.0D
                ).isEmpty(),
                "negative zero baseline has no relative change"
        );
        assertTrue(
                AttachmentTooltipDiffService.relativePercent(
                        null,
                        3.0D
                ).isEmpty()
        );
        assertTrue(
                AttachmentTooltipDiffService.relativePercent(
                        Double.NaN,
                        3.0D
                ).isEmpty()
        );
        assertTrue(
                AttachmentTooltipDiffService.relativePercent(
                        3.0D,
                        Double.POSITIVE_INFINITY
                ).isEmpty()
        );
    }

    @Test
    void relativePercentIsRenderedInParentheses() {
        assertEquals(
                "+10.00% (+25%)",
                AttachmentTooltipDiffService.formatDifference(
                        "armor_ignore",
                        diff(
                                "gui.tacz.gun_refit.property_diagrams.armor_ignore",
                                0.10D,
                                OptionalDouble.of(25.0D)
                        )
                )
        );
        assertEquals(
                "+0.10s (-50%)",
                AttachmentTooltipDiffService.formatDifference(
                        "ads",
                        diff(
                                "gui.tacz.gun_refit.property_diagrams.ads",
                                0.10D,
                                OptionalDouble.of(-50.0D)
                        )
                )
        );
        assertEquals(
                "-5.00%",
                AttachmentTooltipDiffService.formatDifference(
                        "armor_ignore",
                        diff(
                                "gui.tacz.gun_refit.property_diagrams.armor_ignore",
                                -0.05D,
                                OptionalDouble.of(-0.4D)
                        )
                ),
                "a relative change that rounds to zero is omitted"
        );
    }
}
