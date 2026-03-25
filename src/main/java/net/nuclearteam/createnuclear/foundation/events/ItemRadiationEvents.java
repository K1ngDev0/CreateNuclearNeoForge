package net.nuclearteam.createnuclear.foundation.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.nuclearteam.createnuclear.CNEffects;
import net.nuclearteam.createnuclear.CNItems;
import net.nuclearteam.createnuclear.CNTags;

import java.lang.reflect.Method;

public class ItemRadiationEvents {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int RADIATION_DURATION_TICKS = 100;
    private static final int RADIATION_AMPLIFIER = 0;
    private static final double DIRECT_RADIATION_RANGE = 50.0D;
    private static final double SINGLE_WALL_MAX_RANGE = 5.0D;

    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (Player player : serverLevel.players()) {
            if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isAlive() || serverPlayer.isSpectator()) {
                continue;
            }

            if (isExposedToRadioactiveItems(serverLevel, serverPlayer)) {
                serverPlayer.addEffect(new MobEffectInstance(CNEffects.RADIATION.getDelegate(), RADIATION_DURATION_TICKS, RADIATION_AMPLIFIER));
            }
        }
    }

    private static boolean isExposedToRadioactiveItems(ServerLevel level, ServerPlayer target) {
        Vec3 targetPos = target.getEyePosition();
        AABB searchBox = target.getBoundingBox().inflate(DIRECT_RADIATION_RANGE);

        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
            Vec3 sourcePos = itemEntity.getBoundingBox().getCenter();
            if (isRadioactive(itemEntity.getItem()) && canRadiate(level, sourcePos, targetPos)) {
                return true;
            }
        }

        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            if (player.position().distanceTo(target.position()) > DIRECT_RADIATION_RANGE) {
                continue;
            }

            if (isRadioactive(player.getMainHandItem()) && canRadiate(level, player.getEyePosition(), targetPos)) {
                return true;
            }

            if (isRadioactive(player.getOffhandItem()) && canRadiate(level, player.getEyePosition(), targetPos)) {
                return true;
            }
        }

        BlockPos center = target.blockPosition();
        int radius = (int) Math.ceil(DIRECT_RADIATION_RANGE);

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !isCreateCarrier(blockEntity) || !hasRadioactiveStack(level, pos, blockEntity)) {
                continue;
            }

            Vec3 sourcePos = Vec3.atCenterOf(pos);
            if (canRadiate(level, sourcePos, targetPos)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isRadioactive(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.is(CNTags.CNItemTags.RADIOACTIVE_ITEMS.tag)) {
            return true;
        }

        return stack.is(CNItems.RAW_URANIUM.get())
            || stack.is(CNItems.URANIUM_POWDER.get())
            || stack.is(CNItems.URANIUM_ROD.get());
    }

    private static boolean isCreateCarrier(BlockEntity blockEntity) {
        String className = blockEntity.getClass().getName();
        return className.contains("Belt") || className.contains("Depot");
    }

    private static boolean hasRadioactiveStack(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        ItemStack heldStack = invokeItemStackNoArg(blockEntity);
        if (isRadioactive(heldStack)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (containsRadioactiveStack(handler)) {
                return true;
            }
        }

        IItemHandler internalHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        return containsRadioactiveStack(internalHandler);
    }

    private static boolean containsRadioactiveStack(IItemHandler handler) {
        if (handler == null) {
            return false;
        }

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (isRadioactive(handler.getStackInSlot(slot))) {
                return true;
            }
        }

        return false;
    }

    private static ItemStack invokeItemStackNoArg(Object target) {
        try {
            Method method = target.getClass().getMethod("getHeldItemStack");
            Object result = method.invoke(target);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean canRadiate(Level level, Vec3 source, Vec3 target) {
        double distance = source.distanceTo(target);
        if (distance > DIRECT_RADIATION_RANGE) {
            return false;
        }

        if (distance < 0.001D) {
            return true;
        }

        BlockHitResult firstHit = level.clip(new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (firstHit.getType() == HitResult.Type.MISS) {
            return true;
        }

        if (distance > SINGLE_WALL_MAX_RANGE) {
            return false;
        }

        Vec3 direction = target.subtract(source);
        double length = direction.length();
        if (length < 0.001D) {
            return true;
        }

        Vec3 offset = direction.scale(0.05D / length);
        Vec3 secondStart = firstHit.getLocation().add(offset);
        BlockHitResult secondHit = level.clip(new ClipContext(secondStart, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

        return secondHit.getType() == HitResult.Type.MISS;
    }
}
