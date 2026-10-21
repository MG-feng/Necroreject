package com.manona.industry

import arc.Events
import arc.func.Cons
import arc.graphics.Color
import arc.util.Time
import mindustry.Vars
import mindustry.content.Fx
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Unit as GameUnit
import mindustry.type.StatusEffect
import java.util.HashMap

object CorrosionSystem {

    private class Entry(var ticks: Float, var acc: Float = 0f)

    private val map = HashMap<Int, HashMap<Team, Entry>>()
    private var installed = false

    lateinit var status: StatusEffect
    lateinit var rooted: StatusEffect

    fun register() {
        if (::status.isInitialized) return

        status = StatusEffect("necro-corrosion").apply {
            speedMultiplier = 0.7f
            color = Color.valueOf("8cff59")
            effect = Fx.smoke
            effectChance = 0.12f
        }

        rooted = StatusEffect("necro-rooted").apply {
            speedMultiplier = 0f
            show = false
        }
    }

    fun install() {
        register()
        if (installed) return
        installed = true

        Events.run(EventType.Trigger.update, Runnable { tick() })
        Events.on(EventType.UnitDestroyEvent::class.java, Cons<EventType.UnitDestroyEvent> { e -> onDeath(e.unit) })
        Events.on(EventType.WorldLoadEvent::class.java, Cons<EventType.WorldLoadEvent> { clear() })
    }

    fun clear() {
        map.clear()
    }

    private fun tick() {
        if (Vars.net.client()) return
        if (!installed) return
        if (map.isEmpty()) return

        val dt = Time.delta
        val unitIt = map.entries.iterator()

        while (unitIt.hasNext()) {
            val unitEntry = unitIt.next()
            val u = Groups.unit.getByID(unitEntry.key)
            if (u == null || u.dead) { unitIt.remove(); continue }

            val prot = NecroRegistry.isProtected(u.id)
            var active = false
            var dotDmg = 0f

            val teamIt = unitEntry.value.entries.iterator()
            while (teamIt.hasNext()) {
                val en = teamIt.next().value
                en.ticks -= dt
                if (en.ticks <= 0f) { teamIt.remove(); continue }
                active = true
                en.acc += dt

                if (en.acc >= 30f) {
                    en.acc -= 30f
                    if (!prot) dotDmg += u.maxHealth * 0.0075f
                }
            }

            if (active) {
                u.apply(status, 2f)
                if (dotDmg > 0f && !u.dead) u.damagePierce(dotDmg)
            } else {
                unitIt.remove()
            }
        }
    }

    fun apply(u: GameUnit, team: Team, seconds: Float) {
        if (Vars.net.client()) return
        if (seconds <= 0f) return
        val ticks = seconds * 60f
        val teams = map.getOrPut(u.id) { HashMap() }
        val en = teams.getOrPut(team) { Entry(0f) }
        en.ticks += ticks
    }

    fun refresh(u: GameUnit, team: Team, seconds: Float) {
        if (Vars.net.client()) return
        if (seconds <= 0f) return
        val teams = map.getOrPut(u.id) { HashMap() }
        val en = teams.getOrPut(team) { Entry(0f) }
        val target = seconds * 60f
        if (en.ticks < target) en.ticks = target
    }

    fun areaApply(x: Float, y: Float, team: Team, radius: Float, damage: Float, seconds: Float) {
        if (Vars.net.client()) return
        if (radius <= 0f || (damage <= 0f && seconds <= 0f)) return

        NecroUtil.eachUnitIn(x, y, radius) { u ->
            if (u.team != team && !NecroRegistry.isProtected(u.id)) {
                if (seconds > 0f) apply(u, team, seconds)
                if (damage > 0f) u.damage(damage)
            }
        }

        if (damage > 0f) {
            NecroUtil.eachBuildingIn(x, y, radius) { b ->
                if (b.team != team) b.damage(damage)
            }
        }
    }

    private fun onDeath(u: GameUnit) {
        if (Vars.net.client()) return

        val teams = map.remove(u.id) ?: return

        for (entry in teams.entries) {
            if (entry.value.ticks <= 0f) continue
            val count = (u.maxHealth / 10f).toInt().coerceIn(1, 50)
            PuddleSystem.spawn(u.x, u.y, entry.key, 15f, count, maxOf(8f, u.hitSize * 0.8f))
        }
    }
}
