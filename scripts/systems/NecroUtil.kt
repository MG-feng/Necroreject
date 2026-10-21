package com.manona.industry

import arc.func.Cons
import arc.func.Floatc2
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Angles
import arc.math.Mathf
import arc.struct.Seq
import mindustry.entities.Effect
import mindustry.entities.Effect.EffectContainer
import mindustry.entities.Units
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.gen.Unit as GameUnit

object NecroUtil {
    /**
     * Collect units within radius of (x, y). Dead units are skipped.
     * Callers are responsible for freeing the returned Seq via [Seq.free] if they use a pooled list.
     */
    fun unitsIn(x: Float, y: Float, r: Float): Seq<GameUnit> {
        val out = Seq<GameUnit>()
        val rSq = r * r
        Groups.unit.intersect(x - r, y - r, r * 2f, r * 2f, Cons<GameUnit> { u ->
            if (!u.dead) {
                val dx = u.x - x
                val dy = u.y - y
                if (dx * dx + dy * dy <= rSq) out.add(u)
            }
        })
        return out
    }

    /** Iterate units in radius. Zero-allocation alternative to [unitsIn]. */
    inline fun eachUnitIn(x: Float, y: Float, r: Float, cons: (GameUnit) -> Unit) {
        val rSq = r * r
        Groups.unit.intersect(x - r, y - r, r * 2f, r * 2f, Cons<GameUnit> { u ->
            if (!u.dead) {
                val dx = u.x - x
                val dy = u.y - y
                if (dx * dx + dy * dy <= rSq) cons(u)
            }
        })
    }

    fun buildingsIn(x: Float, y: Float, r: Float): Seq<Building> {
        val out = Seq<Building>()
        Units.nearbyBuildings(x, y, r, Cons<Building> { b ->
            if (b.isValid && Mathf.within(b.x, b.y, x, y, r + b.hitSize() * 0.5f)) out.add(b)
        })
        return out
    }

    /** Zero-allocation iteration over nearby buildings. */
    inline fun eachBuildingIn(x: Float, y: Float, r: Float, cons: (Building) -> Unit) {
        Units.nearbyBuildings(x, y, r, Cons<Building> { b ->
            if (b.isValid && Mathf.within(b.x, b.y, x, y, r + b.hitSize() * 0.5f)) cons(b)
        })
    }
}

object NecroFx {
    val BRIGHT: Color = Color.valueOf("8cff59")
    val MID: Color = Color.valueOf("46a03a")
    val DARK: Color = Color.valueOf("173f1d")
    val PUDDLE_DARK: Color = Color.valueOf("2d5e1e")

    private fun ease(fin: Float): Float = 1f - (1f - fin) * (1f - fin)

    val ring = Effect(40f, 600f, Cons<EffectContainer> { e ->
        Draw.color(BRIGHT, e.fout() * 0.3f)
        Lines.stroke(1f + e.fout() * 2f)
        Lines.circle(e.x, e.y, e.rotation * ease(e.fin()))
        Draw.reset()
    })

    val burst = Effect(45f, 600f, Cons<EffectContainer> { e ->
        val r = e.rotation * ease(e.fin())
        Draw.color(BRIGHT, 0.3f * e.fout())
        Fill.circle(e.x, e.y, r)
        Draw.color(MID, e.fout())
        Lines.stroke(1f + 3f * e.fout())
        Lines.circle(e.x, e.y, r)
        Draw.color(BRIGHT, e.fout())
        Angles.randLenVectors(e.id.toLong(), 16, r, Floatc2 { dx, dy ->
            Fill.circle(e.x + dx, e.y + dy, 1f + 3f * e.fout())
        })
        Draw.reset()
    })

    val splat = Effect(25f, 200f, Cons<EffectContainer> { e ->
        Draw.color(BRIGHT, e.fout())
        Angles.randLenVectors(e.id.toLong(), 6, e.rotation * ease(e.fin()), Floatc2 { dx, dy ->
            Fill.circle(e.x + dx, e.y + dy, 0.5f + 2f * e.fout())
        })
        Draw.reset()
    })

    val charge = Effect(40f, 100f, Cons<EffectContainer> { e ->
        Draw.color(BRIGHT, 0.2f + e.fin() * 0.5f)
        Fill.circle(e.x, e.y, 2f + e.fin() * 7f)
        Draw.color(MID, e.fout())
        Lines.stroke(1.5f * e.fout())
        Lines.circle(e.x, e.y, 18f * e.fout())
        Draw.reset()
    })
}
