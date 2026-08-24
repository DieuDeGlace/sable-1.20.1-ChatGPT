package dev.ryanhcode.sable.index;

import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public final class SableToasts {
    private SableToasts() {
    }

    public static void addOrUpdateLoadFailure(final ToastComponent toasts, final Component title, final Component message) {
        addOrUpdate(toasts, LoadFailureToast.class, LoadFailureToast::new, title, message);
    }

    public static void addOrUpdateSaveFailure(final ToastComponent toasts, final Component title, final Component message) {
        addOrUpdate(toasts, SaveFailureToast.class, SaveFailureToast::new, title, message);
    }

    public static void addOrUpdatePhysicsFailure(final ToastComponent toasts, final Component title, final Component message) {
        addOrUpdate(toasts, PhysicsFailureToast.class, PhysicsFailureToast::new, title, message);
    }

    private static <T extends SystemToast> void addOrUpdate(
            final ToastComponent toasts,
            final Class<T> toastClass,
            final BiFunction<Component, Component, T> factory,
            final Component title,
            final @Nullable Component message) {
        final T existing = toasts.getToast(toastClass, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION);
        if (existing == null) {
            toasts.addToast(factory.apply(title, message));
        } else {
            existing.reset(title, message);
        }
    }

    private static final class LoadFailureToast extends SystemToast {
        private LoadFailureToast(final Component title, final Component message) {
            super(SystemToastIds.PERIODIC_NOTIFICATION, title, message);
        }
    }

    private static final class SaveFailureToast extends SystemToast {
        private SaveFailureToast(final Component title, final Component message) {
            super(SystemToastIds.PERIODIC_NOTIFICATION, title, message);
        }
    }

    private static final class PhysicsFailureToast extends SystemToast {
        private PhysicsFailureToast(final Component title, final Component message) {
            super(SystemToastIds.PERIODIC_NOTIFICATION, title, message);
        }
    }
}
