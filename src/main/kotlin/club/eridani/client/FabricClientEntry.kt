package club.eridani.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.LiteralText
import net.minecraft.text.TranslatableText
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

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, scaledWidth, scaledHeight ->
            if (screen is TitleScreen) {
                val buttons = Screens.getButtons(screen)
                val btn = buttons.find { ((it as? ButtonWidget)?.message as? TranslatableText)?.string?.contains("Realms", ignoreCase = true) == true }!!
                buttons.remove(btn)
                buttons.add(ButtonWidget(btn.x, btn.y, btn.width, btn.height, LiteralText("TEST"), ButtonWidget.PressAction {
                    Screens.getClient(screen).openScreen(TestGui())
                }))
            }
        })
    }
}