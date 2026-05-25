package com.xkball.x3dmap.client.terrain;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.xkball.xklibmc.api.client.b3d.ISTD140Writer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

public record ABlock(int x, int y, int z, int color, int mask) implements ISTD140Writer {
    
    private static final int PACKED_Y_MASK = (1 << BlockPos.PACKED_Y_LENGTH) - 1;
    
    public ABlock(){
        this(0,0,0,0,0b111111);
    }
    
    @Override
    public void calculateSize(Std140SizeCalculator calculator) {
        calculator.putVec3().putInt();
    }
    
    @Override
    public void writeToBuffer(Std140Builder builder) {
        builder.putVec3(x,y,z);
        builder.putInt(color);
    }
    
    public record ABlockData(int x, int y, int z, int color, int mask){
        
        public static final StreamCodec<ByteBuf, ABlockData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ABlockData decode(ByteBuf input) {
                var p = input.readByte();
                var x = p >> 4 & 0xF;
                var z  = p & 0xF;
                var y = input.readShort() & PACKED_Y_MASK;
                var c = input.readInt();
                var mask = input.readByte();
                return new ABlockData(x, y,z,c, mask);
            }
            
            @Override
            public void encode(ByteBuf output, ABlockData value) {
                var p = (value.x & 0xF) << 4;
                p |= value.z & 0xF;
                output.writeByte(p);
                output.writeShort(value.y & PACKED_Y_MASK);
                output.writeInt(value.color);
                output.writeByte(value.mask);
            }
        };
        
        public ABlock toABlock(int px, int pz){
            return new ABlock((this.x & 0xF) + px,this.y,(this.z & 0xF) + pz,this.color, mask & 0b111111);
        }
    }
}
