package dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Records whether a rider's rotation was explicitly written while a
 * {@code startRiding} call was in progress.
 *
 * <p>Vehicles retained inside a sub-level expose their own local coordinate
 * frame to passenger callbacks. If such a callback writes the rider's
 * rotation, that value is already local and must not be transformed a second
 * time when the mount operation returns.</p>
 */
public final class RidingRotationCapture {

    private static final ThreadLocal<Deque<Frame>> ACTIVE = new ThreadLocal<>();

    private RidingRotationCapture() {
    }

    public static void begin(final Object rider) {
        Deque<Frame> frames = ACTIVE.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            ACTIVE.set(frames);
        }
        frames.push(new Frame(rider));
    }

    public static void recordRotationWrite(final Object entity) {
        final Deque<Frame> frames = ACTIVE.get();
        if (frames == null) {
            return;
        }

        for (final Frame frame : frames) {
            if (frame.rider == entity) {
                frame.rotationWritten = true;
                return;
            }
        }
    }

    public static Result finish(final Object rider) {
        final Deque<Frame> frames = ACTIVE.get();
        if (frames == null || frames.isEmpty()) {
            return Result.NOT_CAPTURED;
        }

        final Frame frame = frames.peek();
        if (frame.rider != rider) {
            return Result.NOT_CAPTURED;
        }

        frames.pop();
        if (frames.isEmpty()) {
            ACTIVE.remove();
        }
        return new Result(true, frame.rotationWritten);
    }

    private static final class Frame {
        private final Object rider;
        private boolean rotationWritten;

        private Frame(final Object rider) {
            this.rider = rider;
        }
    }

    public record Result(boolean captured, boolean rotationWritten) {
        private static final Result NOT_CAPTURED = new Result(false, false);
    }
}
