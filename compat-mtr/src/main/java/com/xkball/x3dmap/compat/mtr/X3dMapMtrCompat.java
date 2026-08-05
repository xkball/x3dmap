package com.xkball.x3dmap.compat.mtr;

import com.xkball.x3dmap.X3dMap;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.common.Mod;

@Mod(X3dMapMtrCompat.MODID)
@NonNullByDefault
public final class X3dMapMtrCompat {
    
    public static final String MODID = X3dMap.MODID + "_compat_mtr";
    
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
