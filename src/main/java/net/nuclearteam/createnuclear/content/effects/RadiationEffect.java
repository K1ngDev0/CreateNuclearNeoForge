package net.nuclearteam.createnuclear.content.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.nuclearteam.createnuclear.CNEffects;
import net.nuclearteam.createnuclear.CNTags;
import net.nuclearteam.createnuclear.CreateNuclear;
import net.nuclearteam.createnuclear.content.equipment.armor.AntiRadiationArmorItem;

public class RadiationEffect extends MobEffect {

    /**
     * Constructs the RadiationEffect with harmful category and color.
     * Also applies attribute modifiers to reduce speed, attack damage, and attack speed.
     */
    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 15453236); // Custom color (hex value)

        // Reduces movement speed by 20%
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(CreateNuclear.MOD_ID, "radiation_movement_speed"), -0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // Reduces attack damage by 20%
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath(CreateNuclear.MOD_ID, "radiation_attack_damage"), -0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // Reduces attack speed by 20%
        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath(CreateNuclear.MOD_ID, "radiation_attack_speed"), -0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * Applies the radiation effect to the entity.
     * - Does nothing if the entity is immune via tag.
     * - Skips damage only when the full anti-radiation suit is worn.
     * - Otherwise, applies magic damage based on the amplifier.
     *
     * @param livingEntity The affected living entity.
     * @param amplifier    The strength (level) of the effect.
     */
    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        // If the entity is immune to radiation, remove the effect
        if (livingEntity.getType().is(CNTags.CNEntityTags.IRRADIATED_IMMUNE.tag)) {
            livingEntity.removeEffect(CNEffects.RADIATION);
            return true;
        }

        // Full suit is required to block radiation poison damage.
        if (isWearingFullAntiRadiationSuit(livingEntity)) {
            return false;
        }

        // Apply radiation damage (magic type), scaled by amplifier
        int damage = 1 << amplifier;
        livingEntity.hurt(livingEntity.damageSources().magic(), damage);
        return true;
    }

    private static boolean isWearingFullAntiRadiationSuit(LivingEntity livingEntity) {
        ItemStack head = livingEntity.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = livingEntity.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = livingEntity.getItemBySlot(EquipmentSlot.FEET);

        return AntiRadiationArmorItem.Armor.isArmored(head)
            && AntiRadiationArmorItem.Armor.isArmored(chest)
            && AntiRadiationArmorItem.Armor.isArmored(legs)
            && AntiRadiationArmorItem.Armor.isArmored(feet);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration >= 1;
    }
}