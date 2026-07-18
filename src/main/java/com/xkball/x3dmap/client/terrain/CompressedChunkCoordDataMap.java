package com.xkball.x3dmap.client.terrain;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

//todo 随机存取
public class CompressedChunkCoordDataMap<T> {
    
    private final int x0;
    private final int z0;
    private final int[] index;
    private final IntList yList;
    private final ArrayList<T> data;
    
    public CompressedChunkCoordDataMap(Builder<T> builder) {
        this.x0 = builder.x0;
        this.z0 = builder.z0;
        this.index = new int[256];
        this.yList = new IntArrayList();
        this.data = new ArrayList<>();
        var idx = 0;
        for (int i = 0; i < 256; i++) {
            index[i] = idx;
            if (builder.map.containsKey(i)) {
                var list = builder.map.get(i);
                for(var pair : list){
                    yList.add(pair.keyInt());
                    data.add(pair.value());
                }
                idx += list.size();
            }
        }
    }
    
    public CompressedChunkCoordDataMap(ChunkPos chunkPos){
        this.x0 = chunkPos.getMinBlockX();
        this.z0 = chunkPos.getMinBlockZ();
        this.index = new int[256];
        this.yList = new IntArrayList();
        this.data = new ArrayList<>();
    }
    
    public CompressedChunkCoordDataMap(int x0, int z0, int[] index, IntList yList, ArrayList<T> data) {
        this.x0 = x0;
        this.z0 = z0;
        this.index = index;
        this.yList = yList;
        this.data = data;
    }
    
    public void forEach(BiConsumer<MutableEntry, T> consumer) {
        var entry = new MutableEntry();
        for (int i = 0; i < 256; i++) {
            var idx = this.index[i];
            var x = this.x0 + i / 16;
            var z = this.z0 + i % 16;
            var maxIdx = i == 255 ? this.size() : this.index[i + 1];
            for (int j = idx; j < maxIdx; j++) {
                entry.set(x, yList.getInt(j), z,j);
                consumer.accept(entry, data.get(j));
            }
        }
    }
    
    public int size(){
        return yList.size();
    }
    
    public boolean isEmpty(){
        return yList.isEmpty();
    }
    
    public static class Builder<T>{
        
        private final int x0;
        private final int z0;
        private final Multimap<Integer,IntObjectPair<T>> map = MultimapBuilder.hashKeys().arrayListValues().build();
        
        public Builder(ChunkPos chunkPos){
            this.x0 = chunkPos.getMinBlockX();
            this.z0 = chunkPos.getMinBlockZ();
        }
        
        public Builder(int x0, int z0) {
            this.x0 = x0;
            this.z0 = z0;
        }
        
        public void append(int px, int y, int pz, T data){
            this.map.put((px << 4) + pz, IntObjectPair.of(y,data));
        }
        
        public void append(BlockPos pos, T data){
            var x = pos.getX() - ((pos.getX() >> 4) << 4);
            var z = pos.getZ() - ((pos.getZ() >> 4) << 4);
            this.map.put((x << 4) + z, IntObjectPair.of(pos.getY(),data));
        }
        
        public CompressedChunkCoordDataMap<T> build(){
            return new CompressedChunkCoordDataMap<T>(this);
        }
    }
    
    public static final class MutableEntry {
        public int x;
        public int y;
        public int z;
        public int index;
        
        public void set(int x, int y, int z, int index) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.index = index;
        }
        
        public int x() {
            return x;
        }
        
        public int y() {
            return y;
        }
        
        public int z() {
            return z;
        }
        
        public int index() {
            return index;
        }
    }
    
    public static <T> StreamCodec<ByteBuf, CompressedChunkCoordDataMap<T>> streamCodecWithList(StreamCodec<ByteBuf, List<T>> tStreamCodec) {
        return new StreamCodec<>() {
            @Override
            public CompressedChunkCoordDataMap<T> decode(ByteBuf input) {
                var x0 = input.readInt();
                var z0 = input.readInt();
                var index = new int[256];
                for (int i = 0; i < 256; i++) {
                    index[i] = VarInt.read(input);
                }
                var size = VarInt.read(input);
                var yList = new IntArrayList();
                for (int i = 0; i < size; i++) {
                    yList.add(input.readShort());
                }
                var data = new ArrayList<T>(tStreamCodec.decode(input));
                return new CompressedChunkCoordDataMap<>(x0, z0, index, yList, data);
            }
            
            @Override
            public void encode(ByteBuf output, CompressedChunkCoordDataMap<T> value) {
                output.writeInt(value.x0);
                output.writeInt(value.z0);
                for (int i = 0; i < 256; i++) {
                    VarInt.write(output,value.index[i]);
                }
                VarInt.write(output,value.data.size());
                for (var y : value.yList){
                    output.writeShort(y);
                }
                tStreamCodec.encode(output, value.data);
            }
        };
    }
    
    public static <T> StreamCodec<ByteBuf, CompressedChunkCoordDataMap<T>> streamCodec(StreamCodec<ByteBuf, T> tStreamCodec) {
        return new StreamCodec<>() {
            @Override
            public CompressedChunkCoordDataMap<T> decode(ByteBuf input) {
                var x0 = input.readInt();
                var z0 = input.readInt();
                var index = new int[256];
                for (int i = 0; i < 256; i++) {
                    index[i] = VarInt.read(input);
                }
                var size = VarInt.read(input);
                var yList = new IntArrayList();
                for (int i = 0; i < size; i++) {
                    yList.add(input.readShort());
                }
                var data = new ArrayList<T>();
                for (int i = 0; i < size; i++) {
                    data.add(tStreamCodec.decode(input));
                }
                return new CompressedChunkCoordDataMap<>(x0, z0, index, yList, data);
            }
            
            @Override
            public void encode(ByteBuf output, CompressedChunkCoordDataMap<T> value) {
                output.writeInt(value.x0);
                output.writeInt(value.z0);
                for (int i = 0; i < 256; i++) {
                    VarInt.write(output,value.index[i]);
                }
                VarInt.write(output,value.data.size());
                for (var y : value.yList){
                    output.writeShort(y);
                }
                for (var t : value.data){
                    tStreamCodec.encode(output, t);
                }
            }
        };
    }
    
}

