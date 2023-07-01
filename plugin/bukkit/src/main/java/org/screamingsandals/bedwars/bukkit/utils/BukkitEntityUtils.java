/*
 * Copyright (C) 2022 ScreamingSandals
 *
 * This file is part of Screaming BedWars.
 *
 * Screaming BedWars is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Screaming BedWars is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Screaming BedWars. If not, see <https://www.gnu.org/licenses/>.
 */

package org.screamingsandals.bedwars.bukkit.utils;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.screamingsandals.bedwars.bukkit.utils.nms.Attribute;
import org.screamingsandals.bedwars.bukkit.utils.nms.EntityLivingNMS;
import org.screamingsandals.bedwars.nms.accessors.MeleeAttackGoalAccessor;
import org.screamingsandals.bedwars.nms.accessors.MobAccessor;
import org.screamingsandals.bedwars.utils.EntityUtils;
import org.screamingsandals.lib.impl.bukkit.utils.nms.ClassStorage;
import org.screamingsandals.lib.utils.reflect.Reflect;

public class BukkitEntityUtils implements EntityUtils {
    @Nullable
    public EntitySelector makeMobAttackTarget(org.screamingsandals.lib.entity.LivingEntity mob, double speed, double follow, double attackDamage) {
        try {
            var handler = ClassStorage.getHandle(mob.as(LivingEntity.class));
            if (!MobAccessor.getType().isInstance(handler)) {
                // throw new IllegalArgumentException("Entity must be an instance of EntityInsentient!");  // pretty sure that this is not displayed - scorp
                new IllegalArgumentException("Entity must be an instance of EntityInsentient!").printStackTrace();
                return null;
            }

            var entityLiving = new EntityLivingNMS(handler);

            var selector = entityLiving.getGoalSelector();
            selector.clearSelector();
            selector.registerPathfinder(0, Reflect.construct(MeleeAttackGoalAccessor.getConstructor0(), handler, 1.0D, false));

            entityLiving.setAttribute(Attribute.MOVEMENT_SPEED, speed);
            entityLiving.setAttribute(Attribute.FOLLOW_RANGE, follow);
            entityLiving.setAttribute(Attribute.ATTACK_DAMAGE, attackDamage);

            entityLiving.getTargetSelector().clearSelector();

            return entityLiving.getTargetSelector();
        } catch (Throwable ignored) {
        }
        return null;
    }
}
