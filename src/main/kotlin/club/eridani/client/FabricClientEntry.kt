package club.eridani.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW


// Author Simon Ogorodnik
class FabricClientEntry : ClientModInitializer {
    init {
        System.setProperty("SKIKO_RENDER_API", "OPENGL")
    }

    override fun onInitializeClient() {
        val keybind = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "eridani.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "eridani.render"
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client: MinecraftClient ->
            while (keybind.wasPressed()) {
                client.setScreen(TestGui())
            }
        })

    }
}