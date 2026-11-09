package dev.graphassembly.mixin.create.bridge;
import dev.graphassembly.integration.bridge.CandidateBinding;
import com.simibubi.create.foundation.recipe.trie.IntArrayTrie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(value=IntArrayTrie.class,remap=false)
public class CandidateTrieMixin {
    @Inject(method="lookup",at=@At("RETURN"),cancellable=true) private void graphAssembly$candidates(CallbackInfoReturnable<List<Object>> cir){cir.setReturnValue(CandidateBinding.candidates(cir.getReturnValue()));}
}
