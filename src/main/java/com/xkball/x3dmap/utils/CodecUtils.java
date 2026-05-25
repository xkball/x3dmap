package com.xkball.x3dmap.utils;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

public class CodecUtils {
    
    public static final StreamCodec<ByteBuf, AABB> AABB_STREAM_CODEC = new StreamCodec<>() {
        
        @Override
        public void encode(ByteBuf output, AABB value) {
            output.writeDouble(value.minX);
            output.writeDouble(value.minY);
            output.writeDouble(value.minZ);
            output.writeDouble(value.maxX);
            output.writeDouble(value.maxY);
            output.writeDouble(value.maxZ);
        }
        
        @Override
        public AABB decode(ByteBuf input) {
            var minX = input.readDouble();
            var minY = input.readDouble();
            var minZ = input.readDouble();
            var maxX = input.readDouble();
            var maxY = input.readDouble();
            var maxZ = input.readDouble();
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    };
}
