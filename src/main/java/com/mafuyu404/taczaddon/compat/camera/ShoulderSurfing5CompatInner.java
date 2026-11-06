package com.mafuyu404.taczaddon.compat.camera;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.Perspective;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ShoulderSurfing5CompatInner {
    private ShoulderSurfing5CompatInner() {
    }

    public static boolean isShoulderSurfing() {
        return IShoulderSurfing.getInstance().isShoulderSurfing();
    }

    public static boolean isFreeLooking() {
        return IShoulderSurfing.getInstance().isFreeLooking();
    }

    public static void forceFirstPerson() {
        IShoulderSurfing.getInstance().changePerspective(
                Perspective.FIRST_PERSON
        );
    }

    public static void enableShoulderSurfing() {
        IShoulderSurfing.getInstance().changePerspective(
                Perspective.SHOULDER_SURFING
        );
    }
}
