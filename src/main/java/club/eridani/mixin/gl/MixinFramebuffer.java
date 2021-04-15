package club.eridani.mixin.gl;

import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Framebuffer.class)
public interface MixinFramebuffer {

    @Accessor("colorAttachment")
    void eridaniSetColorAttachment(int v);

    @Invoker("bind")
    public void eridaniBind(boolean bl);
}
