package com.xkball.x3dmap.client.terrain.render;

import com.xkball.x3dmap.client.terrain.file.MapChunk;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

@NonNullByDefault
public class MapNodeModel {
    
    public static final StreamCodec<ByteBuf, MapNodeModel> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapNodeModel decode(ByteBuf input) {
            var depth = input.readInt();
            var x = input.readInt();
            var y = input.readInt();
            var z = input.readInt();
            var size = input.readInt();
            var map = new Int2ObjectOpenHashMap<TerrainBlockData>();
            for (int i = 0; i < size; i++) {
                map.put(input.readInt(), TerrainBlockData.STREAM_CODEC.decode(input));
            }
            return new MapNodeModel(depth, x, y, z, map);
        }
        
        @Override
        public void encode(ByteBuf output, MapNodeModel value) {
            output.writeInt(value.depth);
            output.writeInt(value.x);
            output.writeInt(value.y);
            output.writeInt(value.z);
            output.writeInt(value.data.size());
            for (var entry : value.data.int2ObjectEntrySet()) {
                output.writeInt(entry.getIntKey());
                TerrainBlockData.STREAM_CODEC.encode(output, entry.getValue());
            }
        }
    };
    
    private static final int SIDE_LENGTH = 32;
    private static final int COORDINATE_MASK = SIDE_LENGTH - 1;
    private static final int DOWN_MASK = 1 << Direction.DOWN.get3DDataValue();
    private static final int UP_MASK = 1 << Direction.UP.get3DDataValue();
    private static final int NORTH_MASK = 1 << Direction.NORTH.get3DDataValue();
    private static final int SOUTH_MASK = 1 << Direction.SOUTH.get3DDataValue();
    private static final int WEST_MASK = 1 << Direction.WEST.get3DDataValue();
    private static final int EAST_MASK = 1 << Direction.EAST.get3DDataValue();

    public final int depth;
    public final int x;
    public final int y;
    public final int z;
    public final Int2ObjectMap<TerrainBlockData> data;
    
    public MapNodeModel(int depth, int x, int y, int z, Int2ObjectMap<TerrainBlockData> data) {
        this.depth = depth;
        this.x = x;
        this.y = y;
        this.z = z;
        this.data = data;
    }
    
    public MapNodeModel(ChunkPos pos, int sectionY, MapChunk view00, MapChunk view10, MapChunk view01, MapChunk view11) {
        this.depth = 5;
        this.x = pos.x() >> 1;
        this.y = sectionY >> 1;
        this.z = pos.z() >> 1;
        this.data = new Int2ObjectOpenHashMap<>();
        this.addChunk(view00);
        this.addChunk(view10);
        this.addChunk(view01);
        this.addChunk(view11);
        this.clearInternalFaces();
    }
    
    public MapNodeModel(List<MapNodeModel> subNodes) {
        assert subNodes.size() == 8;
        var subNode000 = subNodes.getFirst();
        this.depth = subNode000.depth + 1;
        this.x = subNode000.x >> 1;
        this.y = subNode000.y >> 1;
        this.z = subNode000.z >> 1;
        this.data = new Int2ObjectOpenHashMap<>();
        var mergedData = new Int2ObjectOpenHashMap<ColorAccumulator>();
        for (var subNode : subNodes) {
            var offsetX = (subNode.x & 1) << 5;
            var offsetY = (subNode.y & 1) << 5;
            var offsetZ = (subNode.z & 1) << 5;
            for (var entry : subNode.data.int2ObjectEntrySet()) {
                var fineX = offsetX + getX(entry.getIntKey());
                var fineY = offsetY + getY(entry.getIntKey());
                var fineZ = offsetZ + getZ(entry.getIntKey());
                var index = index(fineX >> 1, fineY >> 1, fineZ >> 1);
                var accumulator = mergedData.get(index);
                if (accumulator == null) {
                    accumulator = new ColorAccumulator();
                    mergedData.put(index, accumulator);
                }
                accumulator.add(entry.getValue());
            }
        }
        for (var entry : mergedData.int2ObjectEntrySet()) {
            var index = entry.getIntKey();
            this.data.put(index, new TerrainBlockData(entry.getValue().averageColor(), calculateMask(mergedData, index)));
        }
    }

    private void addChunk(MapChunk view) {
        view.data.forEach((entry, blockData) -> {
            var px = entry.x() - (this.x << 5);
            var py = entry.y() - (this.y << 5);
            var pz = entry.z() - (this.z << 5);
            if (px < 0 || py < 0 || pz < 0 || px >= SIDE_LENGTH || py >= SIDE_LENGTH || pz >= SIDE_LENGTH) return;
            this.data.put(index(px, py, pz), blockData);
        });
    }

    private void clearInternalFaces() {
        for (var entry : this.data.int2ObjectEntrySet()) {
            var index = entry.getIntKey();
            var px = getX(index);
            var py = getY(index);
            var pz = getZ(index);
            var mask = entry.getValue().mask();
            if (px > 0 && this.data.containsKey(index(px - 1, py, pz))) mask &= ~WEST_MASK;
            if (px < COORDINATE_MASK && this.data.containsKey(index(px + 1, py, pz))) mask &= ~EAST_MASK;
            if (py > 0 && this.data.containsKey(index(px, py - 1, pz))) mask &= ~DOWN_MASK;
            if (py < COORDINATE_MASK && this.data.containsKey(index(px, py + 1, pz))) mask &= ~UP_MASK;
            if (pz > 0 && this.data.containsKey(index(px, py, pz - 1))) mask &= ~NORTH_MASK;
            if (pz < COORDINATE_MASK && this.data.containsKey(index(px, py, pz + 1))) mask &= ~SOUTH_MASK;
            entry.setValue(new TerrainBlockData(entry.getValue().color(), mask));
        }
    }

    private static int index(int x, int y, int z) {
        return x << 10 | y << 5 | z;
    }

    private static int getX(int index) {
        return index >> 10 & COORDINATE_MASK;
    }

    private static int getY(int index) {
        return index >> 5 & COORDINATE_MASK;
    }

    private static int getZ(int index) {
        return index & COORDINATE_MASK;
    }

    private static int calculateMask(Int2ObjectMap<?> data, int index) {
        var x = getX(index);
        var y = getY(index);
        var z = getZ(index);
        var mask = 0;
        if (x == 0 || !data.containsKey(index(x - 1, y, z))) mask |= WEST_MASK;
        if (x == COORDINATE_MASK || !data.containsKey(index(x + 1, y, z))) mask |= EAST_MASK;
        if (y == 0 || !data.containsKey(index(x, y - 1, z))) mask |= DOWN_MASK;
        if (y == COORDINATE_MASK || !data.containsKey(index(x, y + 1, z))) mask |= UP_MASK;
        if (z == 0 || !data.containsKey(index(x, y, z - 1))) mask |= NORTH_MASK;
        if (z == COORDINATE_MASK || !data.containsKey(index(x, y, z + 1))) mask |= SOUTH_MASK;
        return mask;
    }

    public boolean isEmpty(){
        return this.data.isEmpty();
    }
    
    private static class ColorAccumulator {
        private int alpha;
        private int red;
        private int green;
        private int blue;
        private int count;

        private void add(TerrainBlockData blockData) {
            this.alpha += blockData.color() >>> 24;
            this.red += blockData.color() >> 16 & 0xFF;
            this.green += blockData.color() >> 8 & 0xFF;
            this.blue += blockData.color() & 0xFF;
            this.count++;
        }

        private int averageColor() {
            return this.alpha / this.count << 24
                    | this.red / this.count << 16
                    | this.green / this.count << 8
                    | this.blue / this.count;
        }
    }
}
