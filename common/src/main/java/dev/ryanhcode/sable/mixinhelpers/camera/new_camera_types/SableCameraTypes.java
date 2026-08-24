package dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types;

import net.minecraft.client.CameraType;

import java.util.Arrays;

public final class SableCameraTypes {

    /*
     * CameraTypeMixin is admitted together with its zoom/clip dependency closure.
     * Until then camera rotation must remain usable with the three vanilla modes.
     * Looking up by values preserves the exact enum instances once that closure is present.
     */
    public static final CameraType SUB_LEVEL_VIEW = sable$find("SUB_LEVEL_VIEW");
    public static final CameraType SUB_LEVEL_VIEW_UNLOCKED = sable$find("SUB_LEVEL_VIEW_UNLOCKED");

    private static CameraType sable$find(final String name) {
        return Arrays.stream(CameraType.values())
                .filter(cameraType -> cameraType.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private SableCameraTypes() {
    }
}
