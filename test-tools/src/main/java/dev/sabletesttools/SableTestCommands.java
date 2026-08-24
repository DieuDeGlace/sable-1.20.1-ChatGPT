package dev.sabletesttools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SableTestCommands {
    private static final int HARD_MAX_BLOCKS = 65_536;

    private SableTestCommands() {
    }

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sabletest")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("give").executes(SableTestCommands::giveWand))
                .then(Commands.literal("status").executes(SableTestCommands::status))
                .then(Commands.literal("clear").executes(SableTestCommands::clear))
                .then(Commands.literal("assemble").executes(SableTestCommands::assemble))
                .then(gravityCommands())
                .then(Commands.literal("mode")
                        .then(Commands.literal("cuboid").executes(ctx -> setMode(ctx, TestSession.Mode.CUBOID)))
                        .then(Commands.literal("connected").executes(ctx -> setMode(ctx, TestSession.Mode.CONNECTED))))
                .then(Commands.literal("config")
                        .then(Commands.literal("reset").executes(SableTestCommands::resetConfig))
                        .then(Commands.literal("max_blocks")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, HARD_MAX_BLOCKS))
                                        .executes(SableTestCommands::setMaxBlocks)))
                        .then(Commands.literal("name")
                                .then(Commands.literal("clear").executes(SableTestCommands::clearName))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(SableTestCommands::setName)))
                        .then(vectorSetting("linear_velocity", SableTestCommands::setLinearVelocity))
                        .then(vectorSetting("angular_velocity", SableTestCommands::setAngularVelocity))
                        .then(vectorSetting("offset", SableTestCommands::setOffset))
                        .then(Commands.literal("rotation")
                                .then(Commands.argument("yaw", DoubleArgumentType.doubleArg(-3600.0D, 3600.0D))
                                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg(-3600.0D, 3600.0D))
                                                .then(Commands.argument("roll", DoubleArgumentType.doubleArg(-3600.0D, 3600.0D))
                                                        .executes(SableTestCommands::setRotation))))));
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gravityCommands() {
        return Commands.literal("gravity")
                .then(Commands.literal("status").executes(SableTestCommands::gravityStatus))
                .then(Commands.literal("release").executes(SableTestCommands::gravityRelease))
                .then(Commands.literal("reset").executes(SableTestCommands::gravityReset))
                .then(Commands.literal("range")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0D, 100.0D))
                                .executes(ctx -> setGravityValue(ctx, GravityValue.RANGE))))
                .then(Commands.literal("hold_distance")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0D, 16.0D))
                                .executes(ctx -> setGravityValue(ctx, GravityValue.HOLD_DISTANCE))))
                .then(Commands.literal("launch_speed")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                .executes(ctx -> setGravityValue(ctx, GravityValue.LAUNCH_SPEED))))
                .then(Commands.literal("follow_strength")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1D, 20.0D))
                                .executes(ctx -> setGravityValue(ctx, GravityValue.FOLLOW_STRENGTH))))
                .then(Commands.literal("max_follow_speed")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0D, 100.0D))
                                .executes(ctx -> setGravityValue(ctx, GravityValue.MAX_FOLLOW_SPEED))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> vectorSetting(
            final String name, final VectorSetter setter) {
        return Commands.literal(name)
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(-10000.0D, 10000.0D))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(-10000.0D, 10000.0D))
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(-10000.0D, 10000.0D))
                                        .executes(ctx -> setter.set(ctx,
                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                DoubleArgumentType.getDouble(ctx, "z"))))));
    }

    private static int giveWand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.getInventory().add(SableTestTools.wandStack())) {
            player.drop(SableTestTools.wandStack(), false);
        }
        if (!player.getInventory().add(SableTestTools.gravityGunStack())) {
            player.drop(SableTestTools.gravityGunStack(), false);
        }
        success(ctx, "已给予测试魔杖和重力枪。");
        return 1;
    }

    private static int gravityStatus(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final TestSession state = state(ctx);
        success(ctx, "重力枪：持有=" + (state.heldSubLevel == null ? "<无>" : state.heldSubLevel)
                + "，射程=" + state.gravityRange + "，持有距离=" + state.holdDistance
                + "，发射速度=" + state.launchSpeed + "，跟随强度=" + state.followStrength
                + "，最大跟随速度=" + state.maxFollowSpeed);
        return 1;
    }

    private static int gravityRelease(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (TestSessions.get(player).heldSubLevel == null) {
            return failure(ctx, "当前没有抓取物理体。");
        }
        GravityGunController.release(player, false);
        success(ctx, "已平稳放下物理体。");
        return 1;
    }

    private static int gravityReset(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        state(ctx).resetGravityConfig();
        success(ctx, "重力枪参数已恢复默认值。已抓取的物理体没有被释放。");
        return 1;
    }

    private static int setGravityValue(final CommandContext<CommandSourceStack> ctx, final GravityValue setting)
            throws CommandSyntaxException {
        final double value = DoubleArgumentType.getDouble(ctx, "value");
        final TestSession state = state(ctx);
        switch (setting) {
            case RANGE -> state.gravityRange = value;
            case HOLD_DISTANCE -> state.holdDistance = value;
            case LAUNCH_SPEED -> state.launchSpeed = value;
            case FOLLOW_STRENGTH -> state.followStrength = value;
            case MAX_FOLLOW_SPEED -> state.maxFollowSpeed = value;
        }
        success(ctx, "重力枪 " + setting.commandName + " = " + value);
        return 1;
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final TestSession state = state(ctx);
        success(ctx, "模式=" + state.mode.name().toLowerCase(Locale.ROOT)
                + "，点1=" + format(state.first) + "，点2=" + format(state.second)
                + "，最大方块=" + state.maxBlocks + "，名称=" + (state.name.isBlank() ? "<无>" : state.name));
        success(ctx, "线速度=" + vector(state.linearVelocity) + " m/s，角速度="
                + vector(state.angularVelocity) + " rad/s，偏移=" + vector(state.offset)
                + "，旋转(yaw,pitch,roll)=" + state.yaw + "," + state.pitch + "," + state.roll);
        if (state.lastSubLevel != null) {
            success(ctx, "最近创建 UUID=" + state.lastSubLevel);
        }
        return 1;
    }

    private static int clear(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        state(ctx).clearSelection();
        success(ctx, "选区已清除。");
        return 1;
    }

    private static int resetConfig(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        state(ctx).resetConfig();
        success(ctx, "参数已恢复默认值；选区没有清除。");
        return 1;
    }

    private static int setMode(final CommandContext<CommandSourceStack> ctx, final TestSession.Mode mode)
            throws CommandSyntaxException {
        state(ctx).mode = mode;
        success(ctx, "装配模式 = " + mode.name().toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int setMaxBlocks(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final int value = IntegerArgumentType.getInteger(ctx, "value");
        state(ctx).maxBlocks = value;
        success(ctx, "最大选区/连通方块数 = " + value);
        return value;
    }

    private static int setName(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        state(ctx).name = StringArgumentType.getString(ctx, "value").trim();
        success(ctx, "新物理体名称 = " + state(ctx).name);
        return 1;
    }

    private static int clearName(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        state(ctx).name = "";
        success(ctx, "新物理体将不设置名称。");
        return 1;
    }

    private static int setLinearVelocity(final CommandContext<CommandSourceStack> ctx,
                                         final double x, final double y, final double z) throws CommandSyntaxException {
        state(ctx).linearVelocity.set(x, y, z);
        success(ctx, "线速度 = " + vector(state(ctx).linearVelocity) + " m/s");
        return 1;
    }

    private static int setAngularVelocity(final CommandContext<CommandSourceStack> ctx,
                                          final double x, final double y, final double z) throws CommandSyntaxException {
        state(ctx).angularVelocity.set(x, y, z);
        success(ctx, "角速度 = " + vector(state(ctx).angularVelocity) + " rad/s");
        return 1;
    }

    private static int setOffset(final CommandContext<CommandSourceStack> ctx,
                                 final double x, final double y, final double z) throws CommandSyntaxException {
        state(ctx).offset.set(x, y, z);
        success(ctx, "创建后偏移 = " + vector(state(ctx).offset));
        return 1;
    }

    private static int setRotation(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final TestSession state = state(ctx);
        state.yaw = DoubleArgumentType.getDouble(ctx, "yaw");
        state.pitch = DoubleArgumentType.getDouble(ctx, "pitch");
        state.roll = DoubleArgumentType.getDouble(ctx, "roll");
        success(ctx, "附加旋转(yaw,pitch,roll) = " + state.yaw + ", " + state.pitch + ", " + state.roll);
        return 1;
    }

    private static int assemble(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final ServerLevel level = player.serverLevel();
        final TestSession state = TestSessions.get(player);
        if (state.first == null) {
            return failure(ctx, "请先用魔杖左键选择点 1。");
        }
        if (state.dimension != level.dimension()) {
            return failure(ctx, "选区不属于你当前所在维度，请重新选择。");
        }

        final AssemblyInput input;
        if (state.mode == TestSession.Mode.CONNECTED) {
            final SubLevelAssemblyHelper.GatherResult gathered = SubLevelAssemblyHelper.gatherConnectedBlocks(
                    state.first, level, state.maxBlocks, null);
            if (gathered.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
                return failure(ctx, gathered.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS
                        ? "连通结构超过 max_blocks=" + state.maxBlocks
                        : "点 1 没有可装配方块。");
            }
            input = new AssemblyInput(state.first, List.copyOf(gathered.blocks()),
                    new BoundingBox3i(gathered.boundingBox()));
        } else {
            if (state.second == null) {
                return failure(ctx, "cuboid 模式需要用魔杖右键选择点 2。");
            }
            final BoundingBox3i bounds = new BoundingBox3i(state.first, state.second);
            final long volume = (long) (bounds.maxX - bounds.minX + 1)
                    * (bounds.maxY - bounds.minY + 1) * (bounds.maxZ - bounds.minZ + 1);
            if (volume > state.maxBlocks) {
                return failure(ctx, "选区体积 " + volume + " 超过 max_blocks=" + state.maxBlocks);
            }
            final List<BlockPos> blocks = new ArrayList<>((int) volume);
            for (final BlockPos pos : BlockPos.betweenClosed(bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ)) {
                if (!level.isLoaded(pos)) {
                    return failure(ctx, "选区包含未加载区块：" + SelectionWandItem.format(pos));
                }
                if (!level.getBlockState(pos).isAir()) {
                    blocks.add(pos.immutable());
                }
            }
            if (blocks.isEmpty()) {
                return failure(ctx, "选区内没有非空气方块。");
            }
            final BlockPos anchor = level.getBlockState(state.first).isAir() ? blocks.get(0) : state.first;
            bounds.set(bounds.minX - 1, bounds.minY - 1, bounds.minZ - 1,
                    bounds.maxX + 1, bounds.maxY + 1, bounds.maxZ + 1);
            input = new AssemblyInput(anchor, blocks, bounds);
        }

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                level, input.anchor(), input.blocks(), input.bounds());
        if (subLevel.isRemoved() || subLevel.getMassTracker().isInvalid()) {
            return failure(ctx, "Sable 拒绝了无有效质量的装配结果。");
        }

        if (!state.name.isBlank()) {
            subLevel.setName(state.name);
        }
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return failure(ctx, "Sable 已创建子世界，但没有返回有效刚体句柄。");
        }
        final Vector3d destination = new Vector3d(subLevel.logicalPose().position()).add(state.offset);
        final Quaterniond orientation = new Quaterniond(subLevel.logicalPose().orientation()).rotateYXZ(
                -Math.toRadians(state.yaw), Math.toRadians(state.pitch), Math.toRadians(state.roll));
        handle.teleport(destination, orientation);
        handle.addLinearAndAngularVelocity(state.linearVelocity, state.angularVelocity);
        state.lastSubLevel = subLevel.getUniqueId();

        success(ctx, "已把 " + input.blocks().size() + " 个方块转换为 Sable 物理体；UUID="
                + state.lastSubLevel);
        return input.blocks().size();
    }

    private static TestSession state(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return TestSessions.get(ctx.getSource().getPlayerOrException());
    }

    private static void success(final CommandContext<CommandSourceStack> ctx, final String message) {
        ctx.getSource().sendSuccess(() -> Component.literal("[Sable Test] " + message)
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static int failure(final CommandContext<CommandSourceStack> ctx, final String message) {
        ctx.getSource().sendFailure(Component.literal("[Sable Test] " + message));
        return 0;
    }

    private static String format(final BlockPos pos) {
        return pos == null ? "<未选择>" : SelectionWandItem.format(pos);
    }

    private static String vector(final Vector3d value) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", value.x, value.y, value.z);
    }

    private record AssemblyInput(BlockPos anchor, List<BlockPos> blocks, BoundingBox3i bounds) {
    }

    @FunctionalInterface
    private interface VectorSetter {
        int set(CommandContext<CommandSourceStack> ctx, double x, double y, double z) throws CommandSyntaxException;
    }

    private enum GravityValue {
        RANGE("range"),
        HOLD_DISTANCE("hold_distance"),
        LAUNCH_SPEED("launch_speed"),
        FOLLOW_STRENGTH("follow_strength"),
        MAX_FOLLOW_SPEED("max_follow_speed");

        private final String commandName;

        GravityValue(final String commandName) {
            this.commandName = commandName;
        }
    }
}
