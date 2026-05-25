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

vec4 projection_from_position(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
    projection.zw = position.zw;
    return projection;
}

uniform sampler2D colorTexture;
uniform sampler2D heightTexture;

const float textureSize_ = 16384;
const float textureUnit_ = 1 / textureSize_;
in vec3 inPos;

out vec4 vertexColor;
out vec3 worldPos;
out vec3 pNormal;

float getHeight(vec2 uv){
    vec4 c;
    int residency = sparseTextureARB(heightTexture, uv, c);
    if (!sparseTexelsResidentARB(residency)){
        return minHeight - 1;
    }
    return float(int(packUnorm4x8(c)));
}

void main() {
    cmddata data = cmd_data[gl_DrawID];
    worldPos = inPos + vec3(data.px, 0, data.pz);
    vec2 uv = worldPos.xz / textureSize_;
    float h00 = getHeight(uv);
    float h10 = getHeight(uv + vec2(textureUnit_, 0));
    float h01 = getHeight(uv + vec2(0, textureUnit_));
    pNormal = -normalize(cross(vec3(1,h10-h00,0),vec3(0,h01-h00,1)));
    worldPos.y = h00;
    vertexColor = texture(colorTexture, uv);
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
}
