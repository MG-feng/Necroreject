package com.manona.industry

import arc.Events
import arc.func.Cons
import arc.func.Floatc2
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.math.Angles
import arc.math.Mathf
import arc.struct.Seq
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.graphics.Layer

class Puddle(val x: Float, val y: Float, val team: Team, val maxRadius: Float, val life: Float) {
    var age = 0f
    private var acc = 0f
    private val seed = Mathf.random(0f, 360f)
    private val shrinkStart get() = life * 0.66f

    val radius: Float
        get() {
            val grow = Mathf.clamp(age / 8f, 0f, 1f)
            if (age < shrinkStart) return maxRadius * grow
            val t = Mathf.clamp((age - shrinkStart) / (life - shrinkStart), 0f, 1f)
            return maxRadius * (1f - 0.4f * t)
        }

    private val alpha: Float
        get() {
            if (age < shrinkStart) return 1f
            val t = Mathf.clamp((age - shrinkStart) / (life - shrinkStart), 0f, 1f)
            val k = 1f - t
            return k * k
        }

    fun update(dt: Float) {
        age += dt
        if (age >= life) return
        acc += dt
        if (acc < STEP) return
        val step = acc
        acc = 0f
        val r = radius
        if (r <= 0.5f) return
        val rSq = r * r

        NecroUtil.eachUnitIn(x, y, r) { u ->
            val dx = u.x - x
            val dy = u.y - y
            val overlap = (1f - (dx * dx + dy * dy) / rSq * 0.7f).coerceIn(0.2f, 1f)
            val amount = 0.25f * overlap * step
            if (u.team == team) {
                if (NecroRegistry.isFungal(u.type) && u.health < u.maxHealth) {
                    u.health = minOf(u.maxHealth, u.health + amount)
                }
            } else if (!NecroRegistry.isProtected(u.id)) {
                u.damagePierce(amount)
                CorrosionSystem.refresh(u, team, 1.5f)
            }
        }

        NecroUtil.eachBuildingIn(x, y, r) { b ->
            if (b.team != team) {
                val dx = b.x - x
                val dy = b.y - y
                val overlap = (1f - (dx * dx + dy * dy) / rSq * 0.7f).coerceIn(0.2f, 1f)
                b.damage(0.25f * overlap * step)
            }
        }
    }

    fun draw() {
        val r = radius
        if (r <= 0.5f) return
        val a = alpha
        val t = Time.time * 0.04f + seed
        val sid = (seed * 1000f).toLong()

        Draw.z(Layer.debris)

        Draw.color(NecroFx.MID, 0.40f * a)
        Angles.randLenVectors(sid, 10, r * 0.75f, Floatc2 { dx, dy ->
            val wobble = 1f + Mathf.sin(t + dx * 0.5f) * 0.15f
            Fill.circle(x + dx, y + dy, r * 0.28f * wobble)
        })
        Fill.circle(x, y, r * 0.72f)

        Draw.color(NecroFx.BRIGHT, 0.50f * a)
        Angles.randLenVectors(sid + 7L, 6, r * 0.40f, Floatc2 { dx, dy ->
            val wobble = 1f + Mathf.sin(t * 1.3f + dy * 0.4f) * 0.12f
            Fill.circle(x + dx, y + dy, r * 0.20f * wobble)
        })
        Fill.circle(x, y, r * 0.50f)

        Draw.color(NecroFx.PUDDLE_DARK, 0.65f * a)
        Fill.circle(
            x + Mathf.sin(t * 0.7f) * r * 0.12f,
            y + Mathf.cos(t * 0.9f) * r * 0.12f,
            r * 0.35f
        )

        Draw.color(NecroFx.BRIGHT, 0.85f * a)
        Fill.circle(
            x + Mathf.sin(t * 1.8f) * r * 0.35f,
            y + Mathf.cos(t * 1.4f) * r * 0.35f,
            r * 0.09f
        )
        Fill.circle(
            x + Mathf.cos(t * 2.1f) * r * 0.25f,
            y + Mathf.sin(t * 1.6f) * r * 0.25f,
            r * 0.06f
        )

        Draw.reset()
    }

    companion object {
        const val STEP = 6f
    }
}

object PuddleSystem {
    val puddles = Seq<Puddle>(false, 64)
    const val MAX_PUDDLES = 250

    fun init() {
        Events.run(EventType.Trigger.update, Runnable {
            if (!Vars.net.client()) {
                val dt = Time.delta
                // Snapshot the size before iterating so that puddles spawned this frame
                // (from onDeath splash, etc.) don't get ticked this frame — prevents
                // double-aging and any re-entrant iteration weirdness.
                val n = puddles.size
                var i = 0
                var processed = 0
                while (processed < n && i < puddles.size) {
                    val p = puddles.get(i)
                    p.update(dt)
                    if (p.age >= p.life) {
                        puddles.remove(i)
                    } else {
                        i++
                    }
                    processed++
                }
                while (puddles.size > MAX_PUDDLES) puddles.remove(0)
            }
        })
        Events.run(EventType.Trigger.draw, Runnable {
            val n = puddles.size
            for (i in 0 until n) puddles.get(i).draw()
        })
        Events.on(EventType.WorldLoadEvent::class.java, Cons<EventType.WorldLoadEvent> { clear() })
    }

    fun clear() {
        puddles.clear()
    }

    fun spawn(x: Float, y: Float, team: Team, radius: Float, count: Int, spread: Float = radius * 0.5f) {
        if (Vars.net.client()) return
        if (radius <= 0f || count <= 0) return
        for (i in 0 until count) {
            puddles.add(Puddle(
                x + Mathf.range(spread),
                y + Mathf.range(spread),
                team, radius, Mathf.random(150f, 210f)))
        }
    }
}
