package com.mafuyu404.taczaddon.ammo.api;

public class IncompleteConsumptionException extends RuntimeException {
    public IncompleteConsumptionException(LinkageError cause) {
        super("External ammo consumption did not complete", cause);
    }
}
