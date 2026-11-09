package dev.graphassembly.mixin.create.bridge;
import dev.graphassembly.integration.bridge.CandidateBinding;
import com.simibubi.create.content.processing.recipe.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(value=ProcessingRecipe.class,remap=false)
public abstract class CandidateRecipeMixin implements CandidateBinding.Attached {
    @Unique private CandidateBinding graphAssembly$binding;
    @Shadow protected abstract int getMaxOutputCount();
    public int graphAssembly$outputLimit(){return getMaxOutputCount();}
    public CandidateBinding graphAssembly$binding(){return graphAssembly$binding;}
    public void graphAssembly$binding(CandidateBinding b){graphAssembly$binding=b;}
    @Inject(method="getRollableResults",at=@At("HEAD"),cancellable=true) private void graphAssembly$outputs(CallbackInfoReturnable<List<ProcessingOutput>> cir){if(graphAssembly$binding!=null)cir.setReturnValue(graphAssembly$binding.outputs());}
}
