package com.xkball.x3dmap.api.mixin;

public interface IExtendedTlsfAllocation {
    
    void setX3dmap$requiedSize(long x3dmap$requiedSize);
    long getX3dmap$requiedSize();
    
    static IExtendedTlsfAllocation cast(Object o) {
        return (IExtendedTlsfAllocation)o;
    }
}
