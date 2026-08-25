#version 460 core
#extension GL_ARB_gpu_shader_int64 : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

struct PosColor{
    uint64_t packed_pos;
    int color_ssbo;
    uint mask;
};

layout(std430, binding = 0) buffer ABlock {
    PosColor posColor[];
};

struct cmddata{
    int indexCount;
    int instanceCount;
    int firstIndex;
    int baseVertex;
    int baseInstance; // idx
    int dir;
    int offset;
};

layout(std430, binding = 1) buffer CMD {
    cmddata cmd_data[];
};

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec4 vertexColor;
out vec3 worldPos;
out vec3 pNormal;

out gl_PerVertex {
    vec4 gl_Position;
    float gl_CullDistance[1];
};

void main() {
    cmddata cmd = cmd_data[gl_BaseInstance];
    PosColor pc = posColor[cmd.offset + gl_InstanceID];
    int x = int(int64_t(pc.packed_pos) >> 38);
    int y = int(int64_t(pc.packed_pos << 52) >> 52);
    int z = int(int64_t(pc.packed_pos << 26) >> 38);
    vec3 blockPosition = vec3(x, y, z);
    gl_Position = ProjMat * ModelViewMat * vec4(Position * float(BLOCK_SIZE) + blockPosition, 1.0);
    gl_CullDistance[0] = (uint(pc.mask) & (1u << uint(cmd.dir))) != 0u ? 1.0 : -1.0;
    uint color = uint(pc.color_ssbo);
    float alpha = float(color >> 24u & 255u) / 255.0;
    float red = float(color >> 16u & 255u) / 255.0;
    float green = float(color >> 8u & 255u) / 255.0;
    float blue = float(color & 255u) / 255.0;
    vertexColor = Color * vec4(red, green, blue, alpha);
    worldPos = blockPosition;
    pNormal = Normal;
}
