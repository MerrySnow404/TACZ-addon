package com.mafuyu404.taczaddon.ammo.api;

/**
 * Result of one real consumption attempt.
 *
 * <ul>
 *     <li>{@link ConsumptionStatus#CONFIRMED}: an exact amount, including a normal
 *     zero; later sources may still be asked for the remainder;</li>
 *     <li>{@link ConsumptionStatus#STOPPED_CONFIRMED}: an exact amount, but the
 *     source stopped abnormally, so no later source may run for this
 *     request;</li>
 *     <li>{@link ConsumptionStatus#STOPPED_UNKNOWN}: the source may already have
 *     changed state with an unknown amount, so no later source may run and
 *     the remaining deficit must never be assumed to be consumed.</li>
 * </ul>
 */
public enum ConsumptionStatus {
    CONFIRMED,
    STOPPED_CONFIRMED,
    STOPPED_UNKNOWN
}
