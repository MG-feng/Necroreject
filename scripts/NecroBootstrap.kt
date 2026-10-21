package com.manona.industry

object NecroBootstrap {

    private var installed = false

    fun install() {
        if (installed) return
        installed = true

        CorrosionSystem.install()
        PuddleSystem.init()
        NecroRegistry.init()
    }
}
