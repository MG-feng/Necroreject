package com.manona.industry

import arc.Events
import arc.func.Cons
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Angles
import arc.math.Mathf
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Unit as GameUnit
import mindustry.graphics.Layer
import mindustry.type.UnitType
import mindustry.type.Weapon
import java.util.HashMap
import java.util.HashSet

object NecroRegistry {

    private class FuncEntry(val weapon: Weapon, val fb: FunctionBullet)

    private val fungal = HashSet<UnitType>()
    private val funcByType = HashMap<UnitType, Array<FuncEntry>>()
    private val deathByType = HashMap<UnitType, Array<NecroBullet>>()
    private val protectedUntil = HashMap<Int, Long>()
    private val timers = HashMap<Long, Float>()
    private val unitCache = HashMap<String, UnitType?>()

    private const val GRACE = 180L
    private const val SLOTS = 16L

    private var pruneTimer = 0f

    fun now(): Long = Vars.state.tick.toLong()

    fun init() {
        // Scan is deferred slightly so all mod content (including our own HJSON units) is loaded.
        Events.run(EventType.Trigger.update, object : Runnable {
            var once = false
            override fun run() {
            if (!once && Vars.content.units().size > 0) {
                once = true
                scan()
            }
                cleanProtection()
                tickFunctions()
            }
        })

        Events.run(EventType.Trigger.draw, Runnable {
            drawAuras()
        })

        Events.on(EventType.UnitDestroyEvent::class.java, Cons<EventType.UnitDestroyEvent> { e -> onDeath(e.unit) })

        Events.on(EventType.WorldLoadEvent::class.java, Cons<EventType.WorldLoadEvent> {
            timers.clear(); protectedUntil.clear()
        })
    }

    private fun scan() {
        fungal.clear(); funcByType.clear(); deathByType.clear(); unitCache.clear()

        Vars.content.units().each(Cons<UnitType> { u ->
            val funcList = ArrayList<FuncEntry>(2)
            val deathList = ArrayList<NecroBullet>(2)
            u.weapons.each(Cons<Weapon> { w ->
                val b = w.bullet ?: return@Cons

                if (b is NecroBullet) fungal.add(u)

                if (b is FunctionBullet) {
                    if (funcList.none { it.fb === b }) funcList.add(FuncEntry(w, b))
                }

                val nb = b as? NecroBullet
                if (b is DeathSpawnBullet || b is DeathBurstBullet) {
                    if (nb != null && deathList.none { it === nb }) deathList.add(nb)
                }

                if (b is ArcPhlegm && b.rootSeconds > 0f) {
                    w.shootStatus = CorrosionSystem.rooted
                    w.shootStatusDuration = (w.shoot?.firstShotDelay ?: 0f) + b.rootSeconds * 60f
                }
            })
            if (funcList.isNotEmpty()) funcByType[u] = funcList.toTypedArray()
            if (deathList.isNotEmpty()) deathByType[u] = deathList.toTypedArray()
        })

        Log.info("[Necroreject] Registry: fungal=${fungal.size}, funcUnits=${funcByType.size}, deathUnits=${deathByType.size}")
    }

    private fun drawAuras() {
        if (funcByType.isEmpty()) return

        val time = Time.time
        Draw.z(Layer.effect)

        Groups.unit.each(Cons<GameUnit> { u ->
            if (u.dead) return@Cons
            val list = funcByType[u.type] ?: return@Cons

            for (e in list) {
                val b = e.fb
                if (b !is AuraBullet) continue
                val r = b.auraRadius
                if (r <= 0f) continue

                val wx = u.x + Angles.trnsx(u.rotation - 90f, e.weapon.x, e.weapon.y)
                val wy = u.y + Angles.trnsy(u.rotation - 90f, e.weapon.x, e.weapon.y)

                Draw.color(NecroFx.BRIGHT, 0.30f)
                Lines.stroke(1.5f)
                Lines.circle(wx, wy, r)

                Draw.color(NecroFx.MID, 0.5f)
                Lines.stroke(1f)
                Lines.circle(wx, wy, r * 0.92f)

                for (i in 0 until 4) {
                    val ang = time * 0.6f + i * 90f
                    val px = wx + Angles.trnsx(ang, r)
                    val py = wy + Angles.trnsy(ang, r)
                    Draw.color(NecroFx.BRIGHT, 0.9f)
                    Fill.circle(px, py, 3f)
                }
            }
        })

        Draw.reset()
    }

    private fun cleanProtection() {
        val t = now()
        val it = protectedUntil.entries.iterator()
        while (it.hasNext()) if (it.next().value <= t) it.remove()
    }

    private fun tickFunctions() {
        if (Vars.net.client()) return
        if (funcByType.isEmpty()) return

        val dt = Time.delta

        Groups.unit.each(Cons<GameUnit> { u ->
            if (u.dead) return@Cons
            val list = funcByType[u.type] ?: return@Cons

            for (idx in list.indices) {
                val e = list[idx]
                val period = e.weapon.reload.coerceAtLeast(1f)
                val key = u.id.toLong() * SLOTS + idx
                val tm = (timers[key] ?: Mathf.random(period)) + dt

                if (tm >= period) {
                    timers[key] = tm - period
                    val wx = u.x + Angles.trnsx(u.rotation - 90f, e.weapon.x, e.weapon.y)
                    val wy = u.y + Angles.trnsy(u.rotation - 90f, e.weapon.x, e.weapon.y)
                    e.fb.trigger(u, wx, wy, period)
                } else {
                    timers[key] = tm
                }
            }
        })

        pruneTimer += dt
        if (pruneTimer > 1200f) {
            pruneTimer = 0f
            val it = timers.keys.iterator()
            while (it.hasNext()) {
                val id = (it.next() / SLOTS).toInt()
                if (Groups.unit.getByID(id) == null) it.remove()
            }
        }
    }

    private fun onDeath(u: GameUnit) {
        if (Vars.net.client()) return

        protectedUntil.remove(u.id)
        for (i in 0 until SLOTS.toInt()) timers.remove(u.id.toLong() * SLOTS + i)

        val list = deathByType[u.type] ?: return

        for (b in list) {
            if (b is DeathBurstBullet) {
                NecroFx.burst.at(u.x, u.y, b.burstRadius)
                if (b.puddleCount > 0 && b.puddleRadius > 0f) {
                    PuddleSystem.spawn(u.x, u.y, u.team, b.puddleRadius, b.puddleCount, b.burstRadius * 0.6f)
                }
                if (b.burstDamage > 0f || b.burstCorrosionSeconds > 0f) {
                    CorrosionSystem.areaApply(u.x, u.y, u.team, b.burstRadius, b.burstDamage, b.burstCorrosionSeconds)
                }
            } else if (b is DeathSpawnBullet) {
                spawnAt(b.spawnType, u.team, u.x, u.y, b.spawnRadius, b.spawnMin, b.spawnMax)
            }
        }
    }

    fun isFungal(type: UnitType): Boolean = fungal.contains(type)

    fun isProtected(id: Int): Boolean = (protectedUntil[id] ?: 0L) > now()

    private fun findUnit(name: String): UnitType? {
        unitCache[name]?.let { return it }
        var found = Vars.content.unit(name)
        if (found == null) {
            val prefix = Vars.mods.getMod(Necroreject::class.java)?.name
            if (prefix != null) found = Vars.content.unit("$prefix-$name")
        }
        unitCache[name] = found
        if (found == null) Log.warn("[Necroreject] Unit type not found: $name")
        return found
    }

    fun spawnAt(typeName: String, team: Team, x: Float, y: Float, radius: Float, minCount: Int, maxCount: Int) {
        if (Vars.net.client()) return
        if (typeName.isEmpty()) return

        val type = findUnit(typeName) ?: return
        val count = if (maxCount <= minCount) minCount.coerceAtLeast(0) else Mathf.random(minCount, maxCount)

        for (i in 0 until count) {
            val pos = safePos(x, y, radius)
            try {
                val u = type.create(team) ?: continue
                u.set(pos[0], pos[1])
                u.rotation = Mathf.random(0f, 360f)
                u.add()
                protectedUntil[u.id] = now() + GRACE
                NecroFx.splat.at(pos[0], pos[1], 14f)
            } catch (e: Exception) {
                Log.err("[Necroreject] Spawn failed for $typeName: ${e.message}")
            }
        }
    }

    private fun safePos(bx: Float, by: Float, r: Float): FloatArray {
        for (a in 0 until 8) {
            val tx = bx + Mathf.range(r)
            val ty = by + Mathf.range(r)
            val tile = Vars.world.tileWorld(tx, ty)
            if (tile != null && !tile.solid()) return floatArrayOf(tx, ty)
        }
        return floatArrayOf(bx, by)
    }
}
