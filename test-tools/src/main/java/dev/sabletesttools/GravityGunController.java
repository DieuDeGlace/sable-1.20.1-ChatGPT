package dev.sabletesttools;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.slf4j.Logger;

final class GravityGunController {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GravityGunController() {
    }

    static void use(final ServerPlayer player) {
        final TestSession state = TestSessions.get(player);
        if (state.heldSubLevel != null) {
            if (player.isShiftKeyDown()) {
                launch(player);
            } else {
                release(player, true);
            }
            return;
        }

        final HitResult hit = player.pick(state.gravityRange, 1.0F, true);
        if (!(hit instanceof final BlockHitResult blockHit)) {
            message(player, "没有瞄准 Sable 物理体。", ChatFormatting.RED);
            return;
        }
        final Object containing = Sable.HELPER.getContaining(player.serverLevel(), blockHit.getBlockPos());
        if (!(containing instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) {
            message(player, "没有瞄准 Sable 物理体。", ChatFormatting.RED);
            return;
        }
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            message(player, "目标没有有效的刚体句柄。", ChatFormatting.RED);
            return;
        }
        stop(handle);
        state.heldSubLevel = subLevel.getUniqueId();
        state.heldDimension = player.serverLevel().dimension();
        final Vec3 hitLocation = blockHit.getLocation();
        // Sable's sub-level-aware clip returns the winning BlockHitResult in
        // the sub-level's plot coordinate space. Applying the inverse pose a
        // second time turns a valid grab point into a distant point and makes
        // the follow controller run continuously at its speed limit.
        state.heldLocalPoint.set(hitLocation.x, hitLocation.y, hitLocation.z);
        state.lastGravityDiagnosticTick = Long.MIN_VALUE;
        LOGGER.info("SABLE-TEST-GRAVITY-GRAB: player={}, sub-level={}, playerPos={}, hitLocal={}, "
                        + "bodyPosition={}, range={}, holdDistance={}",
                player.getGameProfile().getName(), state.heldSubLevel, player.position(), hitLocation,
                subLevel.logicalPose().position(), state.gravityRange, state.holdDistance);
        message(player, "已抓取物理体 " + state.heldSubLevel, ChatFormatting.AQUA);
    }

    static void tick(final ServerPlayer player) {
        final TestSession state = TestSessions.get(player);
        if (state.heldSubLevel == null) {
            return;
        }
        if (!isHoldingGun(player) || state.heldDimension != player.serverLevel().dimension()) {
            release(player, false);
            return;
        }
        final ServerSubLevel subLevel = resolve(player, state);
        if (subLevel == null) {
            state.clearHeldSubLevel();
            return;
        }
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            state.clearHeldSubLevel();
            return;
        }

        final Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(state.holdDistance));
        final Vector3d currentGrabPoint = subLevel.logicalPose().transformPosition(state.heldLocalPoint, new Vector3d());
        final Vector3d desired = new Vector3d(target.x, target.y, target.z)
                .sub(currentGrabPoint).mul(state.followStrength);
        final double speed = desired.length();
        if (speed > state.maxFollowSpeed) {
            desired.mul(state.maxFollowSpeed / speed);
        }
        final Vector3d correction = desired.sub(handle.getLinearVelocity(new Vector3d()));
        final Vector3d stopRotation = handle.getAngularVelocity(new Vector3d()).negate();
        handle.addLinearAndAngularVelocity(correction, stopRotation);

        final long gameTime = player.serverLevel().getGameTime();
        if (state.lastGravityDiagnosticTick == Long.MIN_VALUE
                || gameTime - state.lastGravityDiagnosticTick >= 20L) {
            state.lastGravityDiagnosticTick = gameTime;
            LOGGER.info("SABLE-TEST-GRAVITY-HOLD: player={}, sub-level={}, playerPos={}, eye={}, target={}, "
                            + "grabPhysical={}, bodyPosition={}, velocity={}, correction={}",
                    player.getGameProfile().getName(), state.heldSubLevel, player.position(),
                    player.getEyePosition(), target, currentGrabPoint, subLevel.logicalPose().position(),
                    handle.getLinearVelocity(new Vector3d()), correction);
        }
    }

    static void launch(final ServerPlayer player) {
        final TestSession state = TestSessions.get(player);
        if (state.heldSubLevel == null || !isHoldingGun(player)) {
            return;
        }
        final ServerSubLevel subLevel = resolve(player, state);
        if (subLevel == null) {
            state.clearHeldSubLevel();
            return;
        }
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            state.clearHeldSubLevel();
            return;
        }
        stop(handle);
        final Vec3 look = player.getLookAngle();
        handle.addLinearAndAngularVelocity(
                new Vector3d(look.x, look.y, look.z).mul(state.launchSpeed), new Vector3d());
        final java.util.UUID launched = state.heldSubLevel;
        LOGGER.info("SABLE-TEST-GRAVITY-LAUNCH: player={}, sub-level={}, playerPos={}, look={}, speed={}",
                player.getGameProfile().getName(), launched, player.position(), look, state.launchSpeed);
        state.clearHeldSubLevel();
        message(player, "已发射物理体 " + launched, ChatFormatting.GOLD);
    }

    static void release(final ServerPlayer player, final boolean notify) {
        final TestSession state = TestSessions.get(player);
        if (state.heldSubLevel == null) {
            return;
        }
        final java.util.UUID released = state.heldSubLevel;
        final ServerSubLevel subLevel = resolve(player, state);
        if (subLevel != null) {
            final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle != null && handle.isValid()) {
                stop(handle);
            }
        }
        LOGGER.info("SABLE-TEST-GRAVITY-RELEASE: player={}, sub-level={}, playerPos={}, resolved={}, notify={}",
                player.getGameProfile().getName(), released, player.position(), subLevel != null, notify);
        state.clearHeldSubLevel();
        if (notify) {
            message(player, "已平稳放下物理体。", ChatFormatting.GREEN);
        }
    }

    private static ServerSubLevel resolve(final ServerPlayer player, final TestSession state) {
        if (state.heldDimension == null || player.getServer() == null) {
            return null;
        }
        final ServerLevel heldLevel = player.getServer().getLevel(state.heldDimension);
        if (heldLevel == null) {
            return null;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(heldLevel);
        if (container == null) {
            return null;
        }
        final Object subLevel = container.getSubLevel(state.heldSubLevel);
        return subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()
                ? serverSubLevel : null;
    }

    private static void stop(final RigidBodyHandle handle) {
        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                handle.getAngularVelocity(new Vector3d()).negate());
    }

    static boolean isHoldingGun(final ServerPlayer player) {
        return player.getMainHandItem().getItem() == SableTestTools.GRAVITY_GUN.get()
                || player.getOffhandItem().getItem() == SableTestTools.GRAVITY_GUN.get();
    }

    private static void message(final ServerPlayer player, final String text, final ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Sable Test] " + text).withStyle(color));
    }
}
