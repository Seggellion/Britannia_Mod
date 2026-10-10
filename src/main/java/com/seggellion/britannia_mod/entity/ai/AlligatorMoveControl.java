package com.seggellion.britannia_mod.entity.ai;

import com.seggellion.britannia_mod.entity.AlligatorEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/** Signed water thrust, independent of look pitch; ordinary ground jumps remain vanilla. */
public final class AlligatorMoveControl extends MoveControl {
    public static final float WATER_ACCELERATION = .08F;

    public AlligatorMoveControl(AlligatorEntity mob) { super(mob); }

    public void clearWaterInput() {
        operation = Operation.WAIT;
        mob.setXxa(0);
        mob.setYya(0);
        mob.setZza(0);
    }

    @Override
    public void tick() {
        if (!((AlligatorEntity)mob).usesWaterMovement()) {
            mob.setXxa(0);
            mob.setYya(0);
            super.tick();
            return;
        }
        if (operation != Operation.MOVE_TO) {
            clearWaterInput();
            return;
        }
        operation = Operation.WAIT;
        Vec3 delta = new Vec3(wantedX, wantedY, wantedZ).subtract(mob.position());
        double distance = delta.length();
        double speed = Math.max(0, speedModifier) * mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (delta.horizontalDistanceSqr() > 1.0E-6) {
            float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 30));
        }
        // The controller owns input, never velocity. Limited counter-thrust and normal drag
        // preserve external impulses rather than setting the complete velocity to zero.
        Vec3 desired = distance < 1.0E-6 ? Vec3.ZERO : delta.scale(Math.min(speed * .4, distance * .2) / distance);
        Vec3 thrust = desired.subtract(mob.getDeltaMovement());
        double limit = speed * .1;
        if (thrust.length() > limit) thrust = thrust.normalize().scale(limit);
        Vec3 input = thrust.scale(1 / WATER_ACCELERATION);
        float yaw = mob.getYRot() * Mth.DEG_TO_RAD;
        mob.setSpeed((float)speed);
        mob.setXxa((float)(input.x * Math.cos(yaw) + input.z * Math.sin(yaw)));
        mob.setYya((float)input.y);
        mob.setZza((float)(input.z * Math.cos(yaw) - input.x * Math.sin(yaw)));
    }
}
