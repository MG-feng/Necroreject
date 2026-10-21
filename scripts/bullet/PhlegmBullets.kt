package com.manona.industry

import arc.audio.Sound
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.util.Time
import mindustry.content.Fx
import mindustry.entities.bullet.ArtilleryBulletType
import mindustry.entities.bullet.BasicBulletType
import mindustry.gen.Bullet
import mindustry.gen.Unit as GameUnit
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Trail
import kotlin.math.max
import kotlin.math.min

interface NecroBullet

interface FunctionBullet : NecroBullet {
    fun trigger(u: GameUnit, wx: Float, wy: Float, period: Float)
}

private val tmpColor = Color()

/**
 * Draws a phlegm blob. For arc shots [by] is lifted above ground and a ground
 * shadow is drawn at (b.x, b.y) whose size/alpha scales with the fraction of
 * arc height remaining, so the shell looks like it actually travels in an arc
 * instead of floating above a sliding ground trail.
 */
private fun drawPhlegm(b: Bullet, visualRadius: Float, by: Float, heightFrac: Float) {
    val fin = b.fin()
    val pulse = 1f + Mathf.sin((b.id * 37 + Time.time * 9f).toDouble()).toFloat() * 0.1f
    val grow = 1f + 0.25f * Mathf.sin((fin * 180f).toDouble()).toFloat()
    val r = (visualRadius * pulse * grow).coerceAtLeast(1f)

    // Ground shadow: smaller + fainter the higher the shell is.
    val shadowScale = 0.6f + 0.4f * (1f - heightFrac)
    val shadowAlpha = 0.18f + 0.22f * (1f - heightFrac)
    Draw.z(Layer.debris)
    Draw.color(Color.black, shadowAlpha)
    Fill.circle(b.x, b.y, r * 0.8f * shadowScale)

    // Main blob body, drawn at bullet layer at the lifted Y.
    Draw.z(Layer.bullet)
    Draw.color(NecroFx.BRIGHT, 0.22f)
    Fill.circle(b.x, by, r * 1.7f)

    for (i in 0 until 6) {
        val t = i / 5f
        if (t < 0.5f) tmpColor.set(NecroFx.BRIGHT).lerp(NecroFx.MID, t * 2f)
        else tmpColor.set(NecroFx.MID).lerp(NecroFx.DARK, (t - 0.5f) * 2f)
        Draw.color(tmpColor)
        Fill.circle(b.x, by, r * (1f - t * 0.85f))
    }

    Draw.color(NecroFx.BRIGHT)
    Lines.stroke(max(0.8f, r * 0.15f))
    Lines.circle(b.x, by, r * 0.95f)

    Draw.color(Color.white, 0.5f)
    Fill.circle(b.x - r * 0.3f, by + r * 0.3f, r * 0.18f)

    Draw.reset()
}

private fun arcY(b: Bullet, arcHeight: Float): Float {
    if (arcHeight <= 0f) return b.y
    return b.y + Mathf.sin((b.fin() * 180f).toDouble()).toFloat() * arcHeight
}

private fun heightFrac(b: Bullet, arcHeight: Float): Float {
    if (arcHeight <= 0f) return 0f
    return Mathf.sin((b.fin() * 180f).toDouble()).toFloat()
}

private fun phlegmImpact(
    b: Bullet, x: Float, y: Float, scale: Float,
    puddleRadius: Float, puddleCount: Int, corrosionSeconds: Float,
    spawnOnHit: Boolean, spawnType: String, spawnMin: Int, spawnMax: Int, spawnRadius: Float
) {
    val splash = max(b.type.splashDamageRadius, puddleRadius * 2f)

    NecroFx.burst.at(x, y, splash * scale)

    if (puddleCount > 0 && puddleRadius > 0f) {
        PuddleSystem.spawn(x, y, b.team, puddleRadius * scale, max(1, (puddleCount * scale).toInt()), splash * 0.35f * scale)
    }

    if (corrosionSeconds > 0f) {
        CorrosionSystem.areaApply(x, y, b.team, max(puddleRadius, 25f) * scale, 0f, corrosionSeconds * scale)
    }

    if (spawnOnHit && spawnType.isNotEmpty()) {
        NecroRegistry.spawnAt(spawnType, b.team, x, y, spawnRadius, spawnMin, spawnMax)
    }
}

/**
 * Base for straight-travel phlegm shots (Necroflux).
 * Draws the body at bullet Y (no lift) and a small soft puddle shadow beneath.
 */
open class PhlegmBullet : BasicBulletType(), NecroBullet {
    @JvmField var visualRadius = 6f
    @JvmField var puddleRadius = 10f
    @JvmField var puddleCount = 3
    @JvmField var corrosionSeconds = 3f
    @JvmField var spawnType = ""
    @JvmField var spawnMin = 0
    @JvmField var spawnMax = 0
    @JvmField var spawnRadius = 20f

    init {
        collidesAir = false; collidesGround = true; hittable = true
        despawnHit = false
        trailColor = NecroFx.BRIGHT; lightColor = NecroFx.BRIGHT
        drawSize = 200f
    }

    override fun draw(b: Bullet) {
        // Trail stays flat with the ground blob; body is drawn directly on top.
        super.draw(b)
        drawPhlegm(b, visualRadius, b.y, 0f)
    }

    override fun hit(b: Bullet, x: Float, y: Float) {
        super.hit(b, x, y)
        phlegmImpact(b, x, y, 1f, puddleRadius, puddleCount, corrosionSeconds, true, spawnType, spawnMin, spawnMax, spawnRadius)
    }

    override fun despawned(b: Bullet) {
        if (b.absorbed) {
            super.despawned(b)
            return
        }
        super.despawned(b)
        phlegmImpact(b, b.x, b.y, 0.6f, puddleRadius, puddleCount, corrosionSeconds, false, spawnType, spawnMin, spawnMax, spawnRadius)
    }
}

/**
 * Arcing phlegm artillery (Ruptgout / Skysunder).
 *
 * The bullet moves in a straight line along the ground (its X/Y follow the
 * artillery trajectory that Arc already simulates via vertical velocity), but
 * because Arc's artillery bullet rendering doesn't draw an arc for a
 * homogenous blob, we manually:
 *  - keep an independent Trail in the bullet's data that records the *lifted*
 *    body position each frame, so the green trail follows the shell, not the
 *    ground;
 *  - draw a shrinking ground shadow so the eye reads the blob as climbing and
 *    falling instead of sliding.
 */
open class ArcPhlegm : ArtilleryBulletType(), NecroBullet {
    @JvmField var visualRadius = 9f
    @JvmField var arcHeight = 24f
    @JvmField var puddleRadius = 15f
    @JvmField var puddleCount = 5
    @JvmField var corrosionSeconds = 5f
    @JvmField var spawnType = ""
    @JvmField var spawnMin = 0
    @JvmField var spawnMax = 0
    @JvmField var spawnRadius = 25f
    @JvmField var rootSeconds = 0f

    init {
        collidesAir = false; collidesGround = true; hittable = true
        despawnHit = false
        chargeEffect = NecroFx.charge
        trailColor = NecroFx.BRIGHT; lightColor = NecroFx.BRIGHT
        // Don't let the parent draw a ground trail for us; we manage our own arc trail.
        trailLength = 0
        trailWidth = 0f
        drawSize = 400f
    }

    /** Lazy-init an arc-following Trail on the bullet. */
    private fun trail(b: Bullet): Trail {
        var t = b.data as? Trail
        if (t == null) {
            t = Trail(18)
            b.data = t
        }
        return t
    }

    override fun update(b: Bullet) {
        super.update(b)
        val by = arcY(b, arcHeight)
        trail(b).update(b.x, by)
    }

    override fun draw(b: Bullet) {
        val by = arcY(b, arcHeight)
        val hf = heightFrac(b, arcHeight)

        // Trail at lifted body position so the slime trail follows the arc.
        Draw.color(trailColor, b.fout())
        trail(b).drawCap(trailWidthCore(), b.fout())

        // Light centered on the flying blob.
        Drawf.light(b.x, by, lightRadius, lightColor, 0.7f * b.fout())

        drawPhlegm(b, visualRadius, by, hf)
        Draw.reset()
    }

    private fun trailWidthCore(): Float = max(2f, visualRadius * 0.55f)

    override fun hit(b: Bullet, x: Float, y: Float) {
        super.hit(b, x, y)
        phlegmImpact(b, x, y, 1f, puddleRadius, puddleCount, corrosionSeconds, true, spawnType, spawnMin, spawnMax, spawnRadius)
    }

    override fun despawned(b: Bullet) {
        if (b.absorbed) { super.despawned(b); return }
        val landed = b.time >= b.lifetime - 1.5f
        if (landed) {
            hit(b, b.x, b.y)
        } else {
            super.despawned(b)
            phlegmImpact(b, b.x, b.y, 0.6f, puddleRadius, puddleCount, corrosionSeconds, false, spawnType, spawnMin, spawnMax, spawnRadius)
        }
    }
}

private fun inertInit(b: BasicBulletType) {
    b.speed = 0f; b.lifetime = 1f; b.damage = 0f
    b.collidesAir = false; b.collidesGround = false; b.collides = false
    b.shootEffect = Fx.none; b.hitEffect = Fx.none; b.despawnEffect = Fx.none
    b.smokeEffect = Fx.none; b.hittable = false; b.absorbable = false
    b.drawSize = 0f
    b.shootSound = Sound()
}

class SecretionBullet : BasicBulletType(), FunctionBullet {
    @JvmField var puddleRadius = 10f
    @JvmField var puddleCount = 2

    init { inertInit(this) }

    override fun trigger(u: GameUnit, wx: Float, wy: Float, period: Float) {
        if (puddleCount > 0 && puddleRadius > 0f) {
            PuddleSystem.spawn(wx, wy, u.team, puddleRadius, puddleCount, max(4f, u.hitSize * 0.3f))
            NecroFx.splat.at(wx, wy, puddleRadius)
        }
    }

    override fun draw(b: Bullet) {}
}

class AuraBullet : BasicBulletType(), FunctionBullet {
    @JvmField var auraRadius = 60f
    @JvmField var auraDamage = 5f
    @JvmField var auraHeal = 5f
    @JvmField var auraHealPercentPerSecond = 0f
    @JvmField var auraCorrosionSeconds = 2f

    init { inertInit(this) }

    override fun trigger(u: GameUnit, wx: Float, wy: Float, period: Float) {
        val r = auraRadius
        if (r <= 0f) return

        val seconds = period / 60f
        val damage = auraDamage * seconds
        val healFlat = auraHeal * seconds

        NecroUtil.eachUnitIn(wx, wy, r) { t ->
            if (t.team == u.team) {
                if (NecroRegistry.isFungal(t.type) && t.health < t.maxHealth) {
                    var heal = healFlat
                    if (auraHealPercentPerSecond > 0f) {
                        heal += t.maxHealth * auraHealPercentPerSecond * seconds
                    }
                    if (heal > 0f) {
                        t.health = min(t.maxHealth, t.health + heal)
                    }
                }
            } else if (!NecroRegistry.isProtected(t.id)) {
                if (auraCorrosionSeconds > 0f) CorrosionSystem.apply(t, u.team, auraCorrosionSeconds)
                if (damage > 0f) t.damagePierce(damage)
            }
        }

        if (damage > 0f) {
            NecroUtil.eachBuildingIn(wx, wy, r) { bd ->
                if (bd.team != u.team) bd.damage(damage)
            }
        }
    }

    override fun draw(b: Bullet) {}
}

class PustuleBullet : BasicBulletType(), FunctionBullet {
    @JvmField var burstRadius = 80f
    @JvmField var burstDamage = 40f
    @JvmField var burstCorrosionSeconds = 4f
    @JvmField var spawnType = ""
    @JvmField var spawnMin = 0
    @JvmField var spawnMax = 0
    @JvmField var spawnRadius = 35f
    @JvmField var puddleRadius = 12f
    @JvmField var puddleCount = 3

    init { inertInit(this) }

    override fun trigger(u: GameUnit, wx: Float, wy: Float, period: Float) {
        NecroFx.burst.at(wx, wy, burstRadius)
        if (puddleCount > 0 && puddleRadius > 0f) PuddleSystem.spawn(wx, wy, u.team, puddleRadius, puddleCount, burstRadius * 0.4f)
        if (burstDamage > 0f || burstCorrosionSeconds > 0f) {
            CorrosionSystem.areaApply(wx, wy, u.team, burstRadius, burstDamage, burstCorrosionSeconds)
        }
        if (spawnType.isNotEmpty()) {
            NecroRegistry.spawnAt(spawnType, u.team, wx, wy, spawnRadius, spawnMin, spawnMax)
        }
    }

    override fun draw(b: Bullet) {}
}

class SprayBullet : BasicBulletType(), FunctionBullet {
    @JvmField var sprayMinRadius = 20f
    @JvmField var sprayMaxRadius = 110f
    @JvmField var sprayCount = 5
    @JvmField var puddleRadius = 14f
    @JvmField var puddleCount = 2
    @JvmField var corrosionSeconds = 2f

    init { inertInit(this) }

    override fun trigger(u: GameUnit, wx: Float, wy: Float, period: Float) {
        for (i in 0 until sprayCount) {
            val ang = Mathf.random(360f)
            val dst = Mathf.random(sprayMinRadius, sprayMaxRadius)
            val px = wx + Mathf.cosDeg(ang) * dst
            val py = wy + Mathf.sinDeg(ang) * dst
            NecroFx.splat.at(px, py, puddleRadius)
            PuddleSystem.spawn(px, py, u.team, puddleRadius, puddleCount, puddleRadius * 0.5f)
            if (corrosionSeconds > 0f) CorrosionSystem.areaApply(px, py, u.team, puddleRadius, 0f, corrosionSeconds)
        }
    }

    override fun draw(b: Bullet) {}
}

class DeathSpawnBullet : BasicBulletType(), NecroBullet {
    @JvmField var spawnType = ""
    @JvmField var spawnMin = 0
    @JvmField var spawnMax = 0
    @JvmField var spawnRadius = 30f

    init { inertInit(this) }

    override fun draw(b: Bullet) {}
}

class DeathBurstBullet : BasicBulletType(), NecroBullet {
    @JvmField var burstRadius = 80f
    @JvmField var burstDamage = 100f
    @JvmField var burstCorrosionSeconds = 3f
    @JvmField var puddleRadius = 15f
    @JvmField var puddleCount = 5

    init { inertInit(this) }

    override fun draw(b: Bullet) {}
}
