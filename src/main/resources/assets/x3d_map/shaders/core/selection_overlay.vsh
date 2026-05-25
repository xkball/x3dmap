#version 460 core
#extension GL_ARB_sparse_texture2 : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform LevelData {
    float minHeight;
    float maxHieight;
    float seaLevel;
    float unusedPadding;
};

struct cmddata{
    int indexCount;
    int instanceCount;
    int firstIndex;
    int baseVertex;
    int baseInstance;
    int px;
    int pz;
};

layout(std430, binding = 0) buffer cmd {
    cmddata cmd_data[];
};

uniform sampler2D heightTexture;

const float textureSize_ = 16384;
const float textureUnit_ = 1 / textureSize_;
in vec3 inPos;

out vec4 vertexColor;

float getHeight(vec2 uv){
    vec4 c;
    int residency = sparseTextureARB(heightTexture, uv, c);
    if (!sparseTexelsResidentARB(residency)){
        return seaLevel;
    }
    return float(int(packUnorm4x8(c)));
}

void main() {
    cmddata data = cmd_data[gl_DrawID];
    vec3 worldPos = inPos + vec3(data.px, 0, data.pz);
    vec2 uv = worldPos.xz / textureSize_;
    float h00 = getHeight(uv);
    worldPos.y = h00 + 2;
    vertexColor = vec4(1.0, 1.0, 0.0, 0.35);
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
}
