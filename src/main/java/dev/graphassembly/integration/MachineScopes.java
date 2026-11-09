package dev.graphassembly.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owner-local, synchronous machine operations. No token survives a native call or is serialized. */
public final class MachineScopes<S> {
    private record Location(Level level,BlockPos pos) {}
    private final Map<Location,Token> active=new HashMap<>();
    private final Consumer<S> invalidate;

    public MachineScopes(Consumer<S> invalidate) {this.invalidate=invalidate;}

    public final class Token implements AutoCloseable {
        private final Location location;
        private S value;
        private boolean invalid;
        private Token(Location location){this.location=location;}
        private void invalidate(){invalid=true;if(value!=null)invalidate.accept(value);}
        @Override public void close(){active.remove(location,this);}
    }

    /** Reentry never acquires ownership: the existing operation is invalidated instead. */
    public S enter(Level level,BlockPos pos,Function<Token,S> factory) {
        var location=new Location(level,pos.immutable());
        var previous=active.get(location);
        if(previous!=null){previous.invalidate();return null;}
        var token=new Token(location);
        active.put(location,token);
        try {
            token.value=factory.apply(token);
            if(token.invalid)token.invalidate();
            return token.value;
        } catch(RuntimeException|Error failure) {
            token.close();
            throw failure;
        }
    }

    public S get(Level level,BlockPos pos) {
        var token=active.get(new Location(level,pos));
        return token==null?null:token.value;
    }
}
