package com.pp.shaderlod.dev.mixin;

import java.nio.ByteBuffer;
import com.pp.shaderlod.dev.timing.DhTaskTimings;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLBuffer",remap=false)
public abstract class DhUploadTimingMixin {
    @WrapMethod(method="uploadBuffer")
    private void shaderlod$upload(ByteBuffer data,@Coerce Object method,int maxExpansion,int hint,Operation<Void> original){
        var t=DhTaskTimings.begin(DhTaskTimings.owner(),"upload/"+method,data.remaining());boolean failed=true;
        try{original.call(data,method,maxExpansion,hint);failed=false;}finally{DhTaskTimings.end(t,failed);}
    }
}
