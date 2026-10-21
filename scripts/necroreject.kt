package com.manona.industry

import mindustry.mod.Mod

class Necroreject : Mod() {

    /**
     * 内容创建必须在这里。
     * Mods.loadContent() 会先 content.setCurrentMod(mod) 再调本方法，
     * MappableContent 构造函数走 Vars.content.transformName() 自动加 "necroreject-" 前缀，
     * 所以 CorrosionSystem.register() 里要写裸名 "necro-corrosion"，
     * 最终 name = necroreject-necro-corrosion，正好对上 bundle 的 status.necroreject-necro-corrosion.name
     */
    override fun loadContent() {
        CorrosionSystem.register()
    }

    /**
     * 事件挂载。此时所有内容（含本 mod 的单位和上面注册的状态）都已创建完毕，
     * NecroRegistry.scan() 才能遍历到单位、才能读到 CorrosionSystem.rooted。
     */
    override fun init() {
        NecroBootstrap.install()
    }
}
