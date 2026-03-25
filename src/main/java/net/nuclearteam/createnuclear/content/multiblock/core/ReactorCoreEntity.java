package net.nuclearteam.createnuclear.content.multiblock.core;

import lib.multiblock.SimpleMultiBlockAislePatternBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nuclearteam.createnuclear.CNEffects;
import net.nuclearteam.createnuclear.CNBlocks;
import net.nuclearteam.createnuclear.CNFluids;
import net.nuclearteam.createnuclear.CNItems;
import net.nuclearteam.createnuclear.CNDataComponents;
import net.nuclearteam.createnuclear.CreateNuclear;
import net.nuclearteam.createnuclear.content.multiblock.IHeat;
import net.nuclearteam.createnuclear.content.multiblock.casing.ReactorCasingEntity;
import net.nuclearteam.createnuclear.content.multiblock.controller.ReactorControllerBlockEntity;
import net.nuclearteam.createnuclear.infrastructure.config.CNConfigs;

import static net.nuclearteam.createnuclear.content.multiblock.CNMultiblock.*;

@SuppressWarnings({"unused"})
public class ReactorCoreEntity extends ReactorCasingEntity {
    private static final double MUSHROOM_CLOUD_HEIGHT = 40.0D;
    private static final double MUSHROOM_CLOUD_DIAMETER = 12.0D;
    private static final double SHOCKWAVE_RANGE = 100.0D;
    private int countdownTicks = 0;

    public ReactorCoreEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide()) return;

        BlockPos controllerPos = getBlockPosForReactor();
        if (level.getBlockEntity(controllerPos) instanceof ReactorControllerBlockEntity reactorController) {
            int heat = reactorController.heat;
            if (IHeat.HeatLevel.of(heat) == IHeat.HeatLevel.DANGER) {
                if (countdownTicks >= CNConfigs.common().explode.time.get()) { // 300 ticks = 15 secondes
                    explodeReactorCore(level, getBlockPos());
                } else {
                    countdownTicks++;
                }
            } else {
                countdownTicks = 0; // Reset the countdown if the heat level is not in danger
            }
        }
    }

    private void explodeReactorCore(Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }

        int type = CNConfigs.common().explode.type.get();
        float configuredSize = Math.max(8F, CNConfigs.common().explode.size.get());

        if (type == 0) {
            serverLevel.removeBlock(pos, false);
            return;
        }

        Vec3 center = Vec3.atCenterOf(pos);

        if (type == 1) {
            serverLevel.explode(null, center.x, center.y, center.z, configuredSize, Level.ExplosionInteraction.BLOCK);
            spawnFireballBurst(serverLevel, center);
            spawnMushroomCloud(serverLevel, center, configuredSize);
            spawnVisualShockwave(serverLevel, center);
            applyShockwaveKnockback(serverLevel, center);

            if (CNConfigs.common().explode.enableRadiationClouds.get()) {
                spawnRadioactiveClouds(serverLevel, center, configuredSize);
            }
            if (CNConfigs.common().explode.enableUraniumLeak.get()) {
                leakUranium(serverLevel, pos, configuredSize);
            }
            return;
        }

        triggerSpectacularExplosion(serverLevel, pos, configuredSize);
    }

    private void triggerSpectacularExplosion(ServerLevel level, BlockPos pos, float configuredSize) {
        Vec3 center = Vec3.atCenterOf(pos);
        float primaryBlast = configuredSize * 2.0F;
        float secondaryBlast = configuredSize * 1.2F;

        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F, 0.65F);
        level.playSound(null, pos, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.BLOCKS, 7.0F, 1.25F);

        level.explode(null, center.x, center.y, center.z, primaryBlast, Level.ExplosionInteraction.BLOCK);
        level.explode(null, center.x, center.y + 12.0D, center.z, secondaryBlast, Level.ExplosionInteraction.NONE);

        double ringRadius = configuredSize * 1.3D;
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2D * i) / 8D;
            double x = center.x + Math.cos(angle) * ringRadius;
            double z = center.z + Math.sin(angle) * ringRadius;
            level.explode(null, x, center.y + 1.0D, z, configuredSize * 0.7F, Level.ExplosionInteraction.NONE);
        }

        spawnFireballBurst(level, center);
        spawnMushroomCloud(level, center, configuredSize);
        spawnVisualShockwave(level, center);
        applyShockwaveKnockback(level, center);

        if (CNConfigs.common().explode.enableRadiationClouds.get()) {
            spawnRadioactiveClouds(level, center, configuredSize);
        }

        if (CNConfigs.common().explode.enableUraniumLeak.get()) {
            leakUranium(level, pos, configuredSize);
        }
    }

    private void spawnFireballBurst(ServerLevel level, Vec3 center) {
        int rings = 10;
        double maxRadius = MUSHROOM_CLOUD_DIAMETER / 2.0D;

        for (int i = 0; i < rings; i++) {
            double progress = i / (double) (rings - 1);
            double radius = maxRadius * progress;
            int particleCount = Mth.clamp((int) (80 * (1.0D - progress * 0.6D)), 20, 80);
            double y = center.y + 1.0D + progress * 6.0D;

            level.sendParticles(ParticleTypes.FLAME, center.x, y, center.z, particleCount, radius, 0.8D, radius, 0.02D);
            level.sendParticles(ParticleTypes.LAVA, center.x, y, center.z, particleCount / 3, radius * 0.7D, 0.4D, radius * 0.7D, 0.0D);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, y, center.z, 2, radius * 0.2D, 0.15D, radius * 0.2D, 0.0D);
        }
    }

    private void spawnMushroomCloud(ServerLevel level, Vec3 center, float configuredSize) {
        double stemHeight = MUSHROOM_CLOUD_HEIGHT;
        for (int i = 0; i < 40; i++) {
            double y = center.y + (stemHeight * i / 40D);
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, y, center.z, 2, 0.15D, 0.1D, 0.15D, 0.01D);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, y, center.z, 5, 0.5D, 0.2D, 0.5D, 0.02D);
        }

        int layers = 22;
        double capRadius = MUSHROOM_CLOUD_DIAMETER / 2.0D;
        for (int layer = 0; layer < layers; layer++) {
            double progress = layer / (double) (layers - 1);
            double y = center.y + stemHeight + progress * 8.0D;
            double radius = capRadius * (0.55D + Math.sin(progress * Math.PI) * 0.45D);
            int smokeCount = Mth.clamp((int) (configuredSize * 6), 30, 180);

            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, y, center.z, smokeCount, radius, 0.35D, radius, 0.01D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, y, center.z, smokeCount / 2, radius * 0.9D, 0.3D, radius * 0.9D, 0.02D);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, y, center.z, 2, radius * 0.25D, 0.15D, radius * 0.25D, 0.0D);
        }
    }

    private void spawnVisualShockwave(ServerLevel level, Vec3 center) {
        for (int radius = 6; radius <= (int) SHOCKWAVE_RANGE; radius += 3) {
            int points = Mth.clamp(radius * 6, 48, 720);
            double ringY = center.y + 1.25D;

            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2D * i) / points;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;

                level.sendParticles(ParticleTypes.CLOUD, x, ringY, z, 1, 0.15D, 0.05D, 0.15D, 0.01D);
                if (radius % 9 == 0) {
                    level.sendParticles(ParticleTypes.EXPLOSION, x, ringY + 0.2D, z, 1, 0.08D, 0.04D, 0.08D, 0.0D);
                }
            }
        }
    }

    private void applyShockwaveKnockback(ServerLevel level, Vec3 center) {
        AABB range = new AABB(
            center.x - SHOCKWAVE_RANGE, center.y - 32.0D, center.z - SHOCKWAVE_RANGE,
            center.x + SHOCKWAVE_RANGE, center.y + 64.0D, center.z + SHOCKWAVE_RANGE
        );

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, range)) {
            if (!entity.isAlive()) {
                continue;
            }

            Vec3 delta = entity.position().subtract(center);
            double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (horizontalDistance < 0.001D || horizontalDistance > SHOCKWAVE_RANGE) {
                continue;
            }

            double falloff = 1.0D - (horizontalDistance / SHOCKWAVE_RANGE);
            double horizontalForce = 3.5D + (falloff * 4.0D);
            double verticalForce = 0.7D + (falloff * 1.2D);

            Vec3 push = new Vec3(delta.x / horizontalDistance, 0.0D, delta.z / horizontalDistance)
                .scale(horizontalForce)
                .add(0.0D, verticalForce, 0.0D);

            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hurtMarked = true;
            entity.hasImpulse = true;
        }
    }

    private void spawnRadioactiveClouds(ServerLevel level, Vec3 center, float configuredSize) {
        RandomSource random = level.getRandom();
        int cloudCount = CNConfigs.common().explode.cloudCount.get();
        int cloudLifetime = CNConfigs.common().explode.cloudLifetimeTicks.get();
        int smokeLifetime = CNConfigs.common().explode.enableLingeringSmoke.get()
            ? CNConfigs.common().explode.smokeLifetimeTicks.get()
            : 0;
        float driftSpeed = CNConfigs.common().explode.cloudDriftSpeed.get() / 100.0F;
        float baseRadius = CNConfigs.common().explode.cloudRadius.get();

        for (int i = 0; i < cloudCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double spread = configuredSize * (0.6D + random.nextDouble() * 1.8D);
            double x = center.x + Math.cos(angle) * spread;
            double z = center.z + Math.sin(angle) * spread;
            double y = center.y + 4.0D + random.nextDouble() * (configuredSize * 0.5D);

            AreaEffectCloud radiationCloud = new AreaEffectCloud(level, x, y, z);
            radiationCloud.setDuration(cloudLifetime);
            radiationCloud.setRadius(baseRadius + random.nextFloat() * (configuredSize * 0.4F));
            radiationCloud.setRadiusPerTick(-radiationCloud.getRadius() / Math.max(1, cloudLifetime));
            radiationCloud.setParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);
            radiationCloud.setNoGravity(true);
            radiationCloud.addEffect(new MobEffectInstance(CNEffects.RADIATION.getDelegate(), 220, 1));
            radiationCloud.setDeltaMovement(
                (random.nextDouble() - 0.5D) * driftSpeed,
                (random.nextDouble() * driftSpeed) * 0.35D,
                (random.nextDouble() - 0.5D) * driftSpeed
            );
            level.addFreshEntity(radiationCloud);

            if (smokeLifetime > 0) {
                AreaEffectCloud smokeCloud = new AreaEffectCloud(level, x, y + 2.0D, z);
                smokeCloud.setDuration(smokeLifetime);
                smokeCloud.setRadius((baseRadius + configuredSize * 0.5F) * 1.2F);
                smokeCloud.setRadiusPerTick(0.0F);
                smokeCloud.setParticle(ParticleTypes.LARGE_SMOKE);
                smokeCloud.setNoGravity(true);
                smokeCloud.setDeltaMovement(
                    (random.nextDouble() - 0.5D) * driftSpeed * 0.6D,
                    random.nextDouble() * driftSpeed * 0.25D,
                    (random.nextDouble() - 0.5D) * driftSpeed * 0.6D
                );
                level.addFreshEntity(smokeCloud);
            }
        }
    }

    private void leakUranium(ServerLevel level, BlockPos centerPos, float configuredSize) {
        RandomSource random = level.getRandom();
        int puddles = CNConfigs.common().explode.uraniumLeakPuddles.get();
        int drops = CNConfigs.common().explode.uraniumLeakDrops.get();

        FluidState uraniumState = CNFluids.URANIUM.get().defaultFluidState();
        BlockState uraniumBlock = uraniumState.createLegacyBlock();

        for (int i = 0; i < puddles; i++) {
            int dx = Mth.nextInt(random, -(int) configuredSize, (int) configuredSize);
            int dz = Mth.nextInt(random, -(int) configuredSize, (int) configuredSize);
            BlockPos basePos = centerPos.offset(dx, 0, dz);
            BlockPos leakPos = findLeakSurface(level, basePos);

            if (leakPos != null) {
                BlockState stateAtLeak = level.getBlockState(leakPos);
                if (stateAtLeak.isAir() || stateAtLeak.getFluidState().is(Fluids.EMPTY)) {
                    level.setBlock(leakPos, uraniumBlock, 3);
                }
            }
        }

        for (int i = 0; i < drops; i++) {
            double rx = centerPos.getX() + 0.5D + (random.nextDouble() - 0.5D) * configuredSize * 1.5D;
            double ry = centerPos.getY() + 2.0D + random.nextDouble() * configuredSize * 0.45D;
            double rz = centerPos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * configuredSize * 1.5D;

            ItemEntity leakedPowder = new ItemEntity(level, rx, ry, rz, new ItemStack(CNItems.URANIUM_POWDER.asItem()));
            leakedPowder.setDeltaMovement(
                (random.nextDouble() - 0.5D) * 0.55D,
                random.nextDouble() * 0.35D,
                (random.nextDouble() - 0.5D) * 0.55D
            );
            level.addFreshEntity(leakedPowder);
        }
    }

    private BlockPos findLeakSurface(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int minY = level.getMinBuildHeight() + 2;
        int maxY = Math.min(level.getMaxBuildHeight() - 2, origin.getY() + 12);
        cursor.setY(maxY);

        while (cursor.getY() > minY && level.isEmptyBlock(cursor)) {
            cursor.move(0, -1, 0);
        }

        if (cursor.getY() <= minY) {
            return null;
        }

        BlockPos candidate = cursor.above();
        return level.isEmptyBlock(candidate) ? candidate : null;
    }

    private static BlockPos FindController(char character) {
        return SimpleMultiBlockAislePatternBuilder.start()
                .aisle(AAAAA, AAAAA, AAAAA, AAAAA, AAAAA)
                .aisle(AABAA, ADADA, BACAB, ADADA, AABAA)
                .aisle(AABAA, ADADA, BACAB, ADADA, AABAA)
                .aisle(AAIAA, ADADA, BACAB, ADADA, AAAA)
                .aisle(AABAA, ADADA, BACAB, ADADA, AABAA)
                .aisle(AABAA, ADADA, BACAB, ADADA, AABAA)
                .aisle(AAAAA, AAAAA, AAAAA, AAAAA, AAOAA)
                .where('A', a -> a.getState().is(CNBlocks.REACTOR_CASING.get()))
                .where('B', a -> a.getState().is(CNBlocks.REACTOR_FRAME.get()))
                .where('C', a -> a.getState().is(CNBlocks.REACTOR_CORE.get()))
                .where('D', a -> a.getState().is(CNBlocks.REACTOR_COOLER.get()))
                .where('*', a -> a.getState().is(CNBlocks.REACTOR_CONTROLLER.get()))
                .where('O', a -> a.getState().is(CNBlocks.REACTOR_OUTPUT.get()))
                .where('I', a -> a.getState().is(CNBlocks.REACTOR_INPUT.get()))
                .getDistanceController(character);
    }

    private BlockPos getBlockPosForReactor() {
        BlockPos posController = getBlockPos();
        BlockPos posInput = new BlockPos(posController.getX(), posController.getY(), posController.getZ());

        int[][][] directions = {
                {{0, 2, 2}, {0, 1, 2}, {0, 0, 2}, {0, -1, 2}, {0, -2, 2}}, // NORTH
                {{0, 2, -2}, {0, 1, -2}, {0, 0, -2}, {0, -1, -2}, {0, -2, -2}}, // SOUTH

                {{2, 2, 0}, {2, 1, 0}, {2, 0, 0}, {2, -1, 0}, {2, -2, 0}}, // EAST
                {{-2, 2, 0}, {-2, 1, 0}, {-2, 0, 0}, {-2, -1, 0}, {-2, -2, 0}} // WEST
        };


        for (int[][] direction : directions) {
            for (int[] dir : direction) {
                BlockPos newPos = posController.offset(dir[0], dir[1], dir[2]);
                if (level.getBlockState(newPos).is(CNBlocks.REACTOR_CONTROLLER.get())) {
                    posInput = newPos;
                    break;
                }
            }
        }

        return posInput;
    }
}
