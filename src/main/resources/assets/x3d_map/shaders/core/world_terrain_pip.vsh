#version 460 core
//#extension  GL_ARB_shader_draw_parameters : require
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

struct PosColor{
    vec3 pos_ssbo;
    int color_ssbo;
};

layout(std140, binding = 0) buffer ABlock {
    PosColor posColor[];
};

layout(std430, binding = 1) buffer FaceIndex {
    uint faceIndex[];
};

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec4 vertexColor;
out vec3 worldPos;
out vec3 pNormal;

//float nextFloat(float f,int s){
//    int i = floatBitsToInt(f);
//    i += i > 0 ? -s : s;
//    return intBitsToFloat(i);
//}

void main() {
    int blockIndex = int(faceIndex[gl_BaseInstance + gl_InstanceID]);
    PosColor pc = posColor[blockIndex];
    vec3 worldPos = Position + pc.pos_ssbo;
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    uint c = uint(pc.color_ssbo);
    float a = float((c >> 24u) & 255u) / 255.0;
    float r = float((c >> 16u) & 255u) / 255.0;
    float g = float((c >> 8u) & 255u) / 255.0;
    float b = float(c & 255u) / 255.0;
    vertexColor = Color * vec4(r, g, b, a);
    worldPos = pc.pos_ssbo;
    pNormal = Normal;
}
