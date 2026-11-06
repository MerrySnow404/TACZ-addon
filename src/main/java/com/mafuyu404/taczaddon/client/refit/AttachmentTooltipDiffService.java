package com.mafuyu404.taczaddon.client.refit;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * Calculates attribute differences between two gun-attachment states.
 *
 * Uses structured {@code DiagramsData.modifier()} values rather than
 * parsing display strings.
 */
@OnlyIn(Dist.CLIENT)
public final class AttachmentTooltipDiffService {
    private static final double EPSILON = 1.0E-6D;

    private static final String DIAGRAM_PREFIX =
            "gui.tacz.gun_refit.property_diagrams.";

    private static final String TOOLTIP_PREFIX =
            "tooltip.tacz.attachment.";

    /**
     * The chart entry for hipfire spread is {@code hipfire_inaccuracy} while
     * the attachment tooltip names the same property {@code inaccuracy}.
     * Both spellings must resolve to one canonical property key.
     */
    private static final String HIPFIRE_ALIAS = "inaccuracy";
    private static final String HIPFIRE_KEY = "hipfire_inaccuracy";

    private AttachmentTooltipDiffService() {
    }

    public record PropertyDifference(
            String diagramTitleKey,
            double absoluteDelta,
            OptionalDouble relativePercent
    ) {
    }

    /**
     * Renders one difference with its real unit.
     *
     * <p>{@code armor_ignore} and {@code aim_inaccuracy} are stored as raw
     * ratios and are therefore scaled by 100 before the {@code %} suffix,
     * while ADS time, weight, RPM, range and the remaining properties keep
     * their own units. Negative zero and non-finite values are never shown.
     *
     * @return the formatted difference, or null when it is not renderable
     */
    public static String formatDifference(
            String propertyKey,
            PropertyDifference diff
    ) {
        double delta = diff.absoluteDelta();
        if (!Double.isFinite(delta)) {
            return null;
        }

        double scaled = isRatioProperty(propertyKey)
                ? delta * 100.0D
                : delta;
        if (scaled == 0.0D) {
            // Never render -0.00.
            scaled = 0.0D;
        }

        String sign = scaled > 0.0D ? "+" : "";
        String formatted = sign + String.format(
                Locale.ROOT,
                "%.2f",
                scaled
        );

        if (isRatioProperty(propertyKey)) {
            formatted += "%";
        } else if ("weight".equals(propertyKey)) {
            formatted += "kg";
        } else if ("ads".equals(propertyKey)
                || propertyKey.contains("time")) {
            formatted += "s";
        } else if ("rpm".equals(propertyKey)) {
            formatted += "rpm";
        } else if ("effective_range".equals(propertyKey)) {
            formatted += "m";
        } else if (propertyKey.contains("ammo_speed")) {
            formatted += "m/s";
        }

        OptionalDouble relative = diff.relativePercent();
        if (relative.isPresent()) {
            double percent = relative.getAsDouble();
            long rounded = Math.round(percent);
            if (Double.isFinite(percent) && rounded != 0L) {
                formatted += " ("
                        + (percent > 0.0D ? "+" : "")
                        + String.format(Locale.ROOT, "%.0f", percent)
                        + "%)";
            }
        }

        return formatted;
    }

    static boolean isRatioProperty(String propertyKey) {
        return "armor_ignore".equals(propertyKey)
                || "aim_inaccuracy".equals(propertyKey);
    }

    /**
     * Calculates property differences between a baseline gun state
     * (same-type attachment removed if present) and a candidate gun
     * state (hovered attachment installed).
     *
     * @param heldGun              the current main-hand gun (copied, not
     *                             mutated)
     * @param candidateAttachment  the hovered attachment stack
     * @return ordered map of normalized property key to difference
     */
    public static Map<String, PropertyDifference> calculate(
            ItemStack heldGun,
            ItemStack candidateAttachment
    ) {
        Map<String, PropertyDifference> result =
                new LinkedHashMap<>();

        IGun gun = IGun.getIGunOrNull(heldGun);
        if (gun == null) {
            return result;
        }

        IAttachment attachmentApi =
                IAttachment.getIAttachmentOrNull(candidateAttachment);

        if (attachmentApi == null) {
            return result;
        }

        AttachmentType type =
                attachmentApi.getType(candidateAttachment);

        ResourceLocation gunId =
                gun.getGunId(heldGun);

        GunData gunData = TimelessAPI
                .getCommonGunIndex(gunId)
                .map(i -> i.getGunData())
                .orElse(null);

        if (gunData == null) {
            return result;
        }

        /* ---- baseline gun ---- */
        ItemStack baselineGun = heldGun.copy();
        IGun baselineIGun =
                IGun.getIGunOrNull(baselineGun);

        if (baselineIGun == null) {
            return result;
        }

        ItemStack oldSameType =
                baselineIGun.getAttachment(baselineGun, type);

        if (!oldSameType.isEmpty()) {
            baselineIGun.unloadAttachment(baselineGun, type);
        }

        Map<String, Double> baselineValues =
                evaluateGun(baselineGun, gunData);
        Map<String, Double> baselineActual =
                evaluateActualValues(baselineGun, gunData);

        /* ---- candidate gun ---- */
        if (!baselineIGun.allowAttachment(
                baselineGun,
                candidateAttachment
        )) {
            return result;
        }

        ItemStack candidateGun = baselineGun.copy();
        IGun candidateIGun =
                IGun.getIGunOrNull(candidateGun);

        if (candidateIGun == null) {
            return result;
        }

        candidateIGun.installAttachment(
                candidateGun,
                candidateAttachment.copy()
        );

        Map<String, Double> candidateValues =
                evaluateGun(candidateGun, gunData);
        Map<String, Double> candidateActual =
                evaluateActualValues(candidateGun, gunData);

        /* ---- compute differences ---- */
        for (Map.Entry<String, Double> entry :
                candidateValues.entrySet()) {
            String key = entry.getKey();
            double candidateValue = entry.getValue();
            Double baselineValue = baselineValues.get(key);

            if (baselineValue == null) {
                continue;
            }

            double delta = candidateValue - baselineValue;

            if (Double.isNaN(delta)
                    || Double.isInfinite(delta)
                    || Math.abs(delta) < EPSILON) {
                continue;
            }

            String propertyKey = normalizePropertyKey(key);
            if (propertyKey == null) {
                continue;
            }

            String diagramKey =
                    DIAGRAM_PREFIX + propertyKey;

            result.put(
                    propertyKey,
                    new PropertyDifference(
                            diagramKey,
                            delta,
                            relativePercent(
                                    baselineActual.get(propertyKey),
                                    candidateActual.get(propertyKey)
                            )
                    )
            );
        }

        return result;
    }

    /**
     * Relative change of one property between the two real gun states.
     *
     * <p>{@code relativePercent = 100 * (candidate - baseline) / abs(baseline)}
     * is only reported when the baseline is a finite, non-zero, structured
     * value and the candidate is finite as well. Custom properties without a
     * reliable structured baseline stay empty.
     */
    static OptionalDouble relativePercent(
            Double baselineActual,
            Double candidateActual
    ) {
        if (baselineActual == null || candidateActual == null) {
            return OptionalDouble.empty();
        }

        double baseline = baselineActual;
        double candidate = candidateActual;

        if (!Double.isFinite(baseline)
                || !Double.isFinite(candidate)
                || baseline == 0.0D) {
            return OptionalDouble.empty();
        }

        double percent = 100.0D
                * (candidate - baseline)
                / Math.abs(baseline);

        if (!Double.isFinite(percent)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(percent);
    }

    /**
     * Evaluates the real structured value of every built-in gun property for
     * one gun state.
     *
     * <p>Values come from the same {@link AttachmentCacheProperty} the game
     * uses. Display-only ratios such as {@code DiagramsData.defaultPercent}
     * and {@code DiagramsData.modifierPercent} are never used here, and the
     * modifier delta is never treated as the baseline value.
     */
    private static Map<String, Double> evaluateActualValues(
            ItemStack gunStack,
            GunData gunData
    ) {
        Map<String, Double> values = new LinkedHashMap<>();

        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        try {
            cache.eval(gunStack, gunData);
        } catch (RuntimeException ignored) {
            return values;
        }

        putNumber(values, "ads", () ->
                cache.getCache(GunProperties.ADS_TIME));
        putNumber(values, "armor_ignore", () ->
                cache.getCache(GunProperties.ARMOR_IGNORE));
        putNumber(values, "ammo_speed", () ->
                cache.getCache(GunProperties.AMMO_SPEED));
        putNumber(values, "effective_range", () ->
                cache.getCache(GunProperties.EFFECTIVE_RANGE));
        putNumber(values, "head_shot", () ->
                cache.getCache(GunProperties.HEADSHOT_MULTIPLIER));
        putNumber(values, "knockback", () ->
                cache.getCache(GunProperties.KNOCKBACK));
        putNumber(values, "weight", () ->
                cache.getCache(GunProperties.WEIGHT));
        putNumber(values, "pierce", () ->
                cache.getCache(GunProperties.PIERCE));
        putNumber(values, "rpm", () ->
                cache.getCache(GunProperties.ROUNDS_PER_MINUTE));

        Map<InaccuracyType, Float> inaccuracy =
                cachedInaccuracy(cache);
        putInaccuracy(values, HIPFIRE_KEY, inaccuracy,
                InaccuracyType.STAND);
        putInaccuracy(values, "sneak_inaccuracy", inaccuracy,
                InaccuracyType.SNEAK);
        putInaccuracy(values, "lie_inaccuracy", inaccuracy,
                InaccuracyType.LIE);

        /*
         * TaCZ charts aim inaccuracy as a stability ratio: the chart value is
         * 1 - inaccuracy clamped to [0, 1]. Mirror that display semantics
         * instead of inventing an unclamped raw value.
         */
        Double aimInaccuracy = accuracyValue(inaccuracy);
        if (aimInaccuracy != null) {
            values.put(
                    "aim_inaccuracy",
                    1.0D - Mth.clamp(
                            aimInaccuracy.floatValue(),
                            0.0F,
                            1.0F
                    )
            );
        }

        Double damage = damageValue(cache);
        if (damage != null) {
            values.put("damage", damage);
        }

        GunRecoil recoil = gunData != null
                ? gunData.getRecoil()
                : null;
        Double pitch = recoilValue(cache, recoil, true);
        if (pitch != null) {
            values.put("pitch", pitch);
        }
        Double yaw = recoilValue(cache, recoil, false);
        if (yaw != null) {
            values.put("yaw", yaw);
        }

        return values;
    }

    private static Map<InaccuracyType, Float> cachedInaccuracy(
            AttachmentCacheProperty cache
    ) {
        try {
            Map<InaccuracyType, Float> map =
                    cache.getCache(GunProperties.INACCURACY);
            return map != null && !map.isEmpty() ? map : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Double accuracyValue(
            Map<InaccuracyType, Float> inaccuracy
    ) {
        if (inaccuracy == null) {
            return null;
        }
        Float value = inaccuracy.get(InaccuracyType.AIM);
        return value != null && Float.isFinite(value)
                ? (double) value
                : null;
    }

    private static Double damageValue(
            AttachmentCacheProperty cache
    ) {
        try {
            LinkedList<com.tacz.guns.resource.pojo.data.gun
                    .ExtraDamage.DistanceDamagePair> list =
                    cache.getCache(GunProperties.DAMAGE);
            if (list == null || list.isEmpty()) {
                return null;
            }
            float damage = list.getFirst().getDamage();
            return Float.isFinite(damage)
                    ? (double) damage
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Double recoilValue(
            AttachmentCacheProperty cache,
            GunRecoil recoil,
            boolean pitch
    ) {
        if (recoil == null) {
            return null;
        }
        GunRecoilKeyFrame[] frames = pitch
                ? recoil.getPitch()
                : recoil.getYaw();
        if (frames == null || frames.length == 0) {
            return null;
        }

        float[] keyFrameValue = frames[0].getValue();
        if (keyFrameValue == null || keyFrameValue.length < 2) {
            return null;
        }
        double reference = Math.max(
                Math.abs(keyFrameValue[0]),
                Math.abs(keyFrameValue[1])
        );

        try {
            ParameterizedCachePair<Float, Float> pair =
                    cache.getCache(GunProperties.RECOIL);
            if (pair == null) {
                return null;
            }

            com.tacz.guns.api.modifier.ParameterizedCache<Float> side =
                    pitch ? pair.left() : pair.right();
            if (side == null) {
                return null;
            }

            double value = side.eval(reference);
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void putInaccuracy(
            Map<String, Double> values,
            String key,
            Map<InaccuracyType, Float> inaccuracy,
            InaccuracyType type
    ) {
        if (inaccuracy == null) {
            return;
        }
        Float value = inaccuracy.get(type);
        if (value != null && Float.isFinite(value)) {
            values.put(key, (double) value);
        }
    }

    private static void putNumber(
            Map<String, Double> values,
            String key,
            ValueSupplier supplier
    ) {
        try {
            Number value = supplier.get();
            if (value != null
                    && Double.isFinite(value.doubleValue())) {
                values.put(key, value.doubleValue());
            }
        } catch (RuntimeException ignored) {
            // A missing cache entry simply means no reliable baseline.
        }
    }

    @FunctionalInterface
    private interface ValueSupplier {
        Number get();
    }

    /**
     * Evaluates all attachment modifiers for the given gun stack and
     * returns a map of normalized property keys to structured modifier
     * values.
     */
    private static Map<String, Double> evaluateGun(
            ItemStack gunStack,
            GunData gunData
    ) {
        Map<String, Double> values = new LinkedHashMap<>();

        AttachmentCacheProperty cacheProperty =
                new AttachmentCacheProperty();

        cacheProperty.eval(gunStack, gunData);

        AttachmentPropertyManager.getModifiers()
                .forEach((modifierKey, modifier) -> {
                    try {
                        modifier.getPropertyDiagramsData(
                                        gunStack,
                                        gunData,
                                        cacheProperty
                                )
                                .forEach(diagram -> {
                                    String normalizedKey =
                                            normalizeDiagramKey(
                                                    diagram.titleKey()
                                            );

                                    if (normalizedKey == null) {
                                        return;
                                    }

                                    Number modifierValue =
                                            diagram.modifier();

                                    if (modifierValue == null) {
                                        return;
                                    }

                                    double value =
                                            modifierValue
                                                    .doubleValue();

                                    if (Double.isNaN(value)
                                            || Double.isInfinite(
                                            value
                                    )) {
                                        return;
                                    }

                                    values.put(
                                            normalizedKey,
                                            value
                                    );
                                });
                    } catch (RuntimeException | LinkageError ignored) {
                        /*
                         * One optional/third-party modifier is
                         * binary-incompatible. Tooltip calculation is
                         * read-only, so skipping that modifier is the safest
                         * degradation boundary.
                         */
                    }
                });

        return values;
    }

    /**
     * Single normalization entry point shared by diagram title keys and
     * attachment tooltip translation keys.
     *
     * @return the canonical property key, or null when the raw key is blank
     */
    public static String normalizePropertyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        String key = rawKey.trim();
        if (HIPFIRE_ALIAS.equals(key)) {
            return HIPFIRE_KEY;
        }
        return key;
    }

    /**
     * Extracts a normalized property key from a diagram title key.
     *
     * Expected prefix: {@code gui.tacz.gun_refit.property_diagrams.}
     *
     * @return the normalized key, or null if the prefix doesn't match
     */
    static String normalizeDiagramKey(String titleKey) {
        if (titleKey == null || titleKey.isBlank()) {
            return null;
        }

        if (!titleKey.startsWith(DIAGRAM_PREFIX)) {
            return null;
        }

        return normalizePropertyKey(
                titleKey.substring(DIAGRAM_PREFIX.length())
        );
    }

    /**
     * Extracts a normalized property key from a tooltip translation key.
     *
     * Expected prefix: {@code tooltip.tacz.attachment.}
     *
     * @return the normalized key, or null if the prefix doesn't match
     */
    public static String normalizeTooltipKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            return null;
        }

        if (!translationKey.startsWith(TOOLTIP_PREFIX)) {
            return null;
        }

        String remainder = translationKey
                .substring(TOOLTIP_PREFIX.length());

        // The first dot-separated segment after the prefix is the
        // property name.
        int dot = remainder.indexOf('.');
        if (dot > 0) {
            return normalizePropertyKey(
                    remainder.substring(0, dot)
            );
        }

        return null;
    }
}

