package dev.motionengine.client.mixin;

import dev.motionengine.client.bridge.MotionHolder;
import dev.motionengine.client.bridge.MotionRuntime;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Attaches a lazily created animation runtime to every Avatar (players and mannequins). */
@Mixin(Avatar.class)
public abstract class AvatarMixin implements MotionHolder {
    @Unique
    private MotionRuntime motionengine$runtime;

    @Override
    public MotionRuntime motionengine$runtime() {
        if (motionengine$runtime == null) {
            Avatar self = (Avatar) (Object) this;
            motionengine$runtime = new MotionRuntime(self.getUUID().getLeastSignificantBits());
        }
        return motionengine$runtime;
    }
}
