package com.mafuyu404.taczaddon.ammo.api;

public record ConsumptionOutcome(int consumed, ConsumptionStatus status) {
    public ConsumptionOutcome {
        consumed = Math.max(0, consumed);
    }

    public static ConsumptionOutcome confirmed(int consumed) {
        return new ConsumptionOutcome(consumed, ConsumptionStatus.CONFIRMED);
    }

    public static ConsumptionOutcome stoppedConfirmed(int consumed) {
        return new ConsumptionOutcome(
                consumed,
                ConsumptionStatus.STOPPED_CONFIRMED
        );
    }

    public static ConsumptionOutcome stoppedUnknown() {
        return new ConsumptionOutcome(0, ConsumptionStatus.STOPPED_UNKNOWN);
    }

    public ConsumptionOutcome withConsumed(int consumed) {
        return new ConsumptionOutcome(consumed, this.status);
    }

    public boolean mayContinue() {
        return this.status == ConsumptionStatus.CONFIRMED;
    }

    public boolean stoppedAbnormally() {
        return this.status != ConsumptionStatus.CONFIRMED;
    }
}
