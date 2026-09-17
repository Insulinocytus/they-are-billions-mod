package io.github.insulinocytus.theyarebillions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class DecayMobEffect extends MobEffect {
    DecayMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B6B6B);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        var maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        double maximum = maxHealth == null ? 0.0 : maxHealth.getBaseValue();
        float current = entity.getHealth();
        if (maximum <= 1.0 || current <= 1.0F) {
            entity.kill();
            return false;
        }
        entity.setHealth(current - 1.0F);
        maxHealth.setBaseValue(maximum - 1.0);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return tickCount % 20 == 0;
    }
}
