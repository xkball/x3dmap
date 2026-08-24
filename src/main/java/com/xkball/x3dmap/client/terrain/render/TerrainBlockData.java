package com.xkball.x3dmap.client.terrain.render;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TerrainBlockData(int color, int mask) {
    public static final StreamCodec<ByteBuf, TerrainBlockData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerrainBlockData decode(ByteBuf input) {
            var c = input.readInt();
            var mask = input.readByte();
            return new TerrainBlockData(c, mask);
        }
        
        @Override
        public void encode(ByteBuf output, TerrainBlockData value) {
            output.writeInt(value.color);
            output.writeByte(value.mask);
        }
    };
}
