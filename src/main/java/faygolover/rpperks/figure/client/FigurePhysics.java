package faygolover.rpperks.figure.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * Покачивание груди одного игрока (клиент, по тику). Пружина по образцу Female Gender Mod, упрощённая: вертикаль
 * (шаг, прыжок, падение, присед, взмах рукой) и поворот (разворот тела). В покое — ноль, поэтому без физики
 * грудь выглядит так же, как стоящая.
 */
public final class FigurePhysics {
    /** Упругость: доля шага к цели за тик и сила разгона — как у FGM при «мягкости» 0.95. */
    private static final float DAMP = 0.1725f, PULL = 2.0775f, FLOPPY = 0.95f;

    private float bounce, prevBounce, vel, target;
    private float rot, prevRot, rotVel, rotTarget;
    private Vec3 prevPos;
    private boolean wasCrouching;

    public void tick(LivingEntity e, float size, float resistance) {
        prevBounce = bounce;
        prevRot = rot;
        Vec3 pos = e.position();
        if (prevPos == null) {
            prevPos = pos;
            return;
        }
        Vec3 motion = pos.subtract(prevPos);
        prevPos = pos;

        float intensity = size * 3f * (1f - Mth.clamp(resistance, 0f, 1f));
        target = (float) motion.y * intensity;
        // Шаг: синус походки, при беге слабее (как у FGM).
        float speedSq = (float) e.getDeltaMovement().lengthSqr() / 0.2f;
        float damp = Math.max(1f, speedSq * speedSq * speedSq);
        target += Mth.cos(e.walkAnimation.position() * 0.6662f + Mth.PI) * 0.25f * e.walkAnimation.speed() * size / damp;
        rotTarget = -((e.yBodyRot - e.yBodyRotO) / 15f) * intensity;

        boolean crouching = e.getPose() == Pose.CROUCHING;
        if (crouching != wasCrouching) target += intensity;
        wasCrouching = crouching;
        if (e.swinging && e.tickCount % 5 == 0 && e.getPose() != Pose.SLEEPING) {
            target += (e.getRandom().nextBoolean() ? 0.25f : -0.25f) * intensity;
        }

        target = Mth.clamp(target, -1.5f, 2.5f);
        rotTarget = Mth.clamp(rotTarget, -25f, 25f);
        vel = Mth.lerp(DAMP, vel, (target - bounce) * PULL);
        bounce += vel * FLOPPY * 1.1625f;
        rotVel = Mth.lerp(DAMP, rotVel, (rotTarget - rot) * PULL);
        rot += rotVel * FLOPPY;
        if (!Float.isFinite(bounce) || !Float.isFinite(rot)) reset();
    }

    public void reset() {
        bounce = prevBounce = vel = target = 0f;
        rot = prevRot = rotVel = rotTarget = 0f;
        prevPos = null;
    }

    /** Смещение вниз, в «пикселях» FGM (делится на 32 блока при рендере). */
    public float bounce(float partialTick) {
        return Mth.lerp(partialTick, prevBounce, bounce);
    }

    /** Поворот вокруг вертикали, градусы. */
    public float rotation(float partialTick) {
        return Mth.lerp(partialTick, prevRot, rot);
    }
}
