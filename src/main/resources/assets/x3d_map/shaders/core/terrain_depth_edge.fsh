#version 330 core

uniform sampler2D input0;
uniform sampler2D input1;

layout(std140) uniform ScreenSize {
    vec2 screenSize;
};

in vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform InvProjMat {
    mat4 invProjMat;
    mat4 ProjMat_;
    vec4 camDir;
    vec4 camPos;
};

const vec2 OFFSET[8] = vec2[8](
    vec2(0,1),
    vec2(1,1),
    vec2(0,1),
    vec2(-1,1),
    vec2(-1,0),
    vec2(-1,-1),
    vec2(0,-1),
    vec2(1,-1)
);

vec2 getDepthDiff(vec2 invSize, vec2 uv, float centerDepth) {
    float depthDiff = 0.0;
    float depthAvg = 0.0;
    for (int i = 0; i < 8; i++) {
        vec2 uvi = uv + OFFSET[i] * invSize;
        float depth = texture(input1, uvi).r;
        depthDiff = max(depthDiff, abs(depth - centerDepth));
        depthAvg += depth;
    }
    return vec2(depthDiff,depthAvg/8-centerDepth);
}

float grad(float v){
    return length(vec2(dFdx(v),dFdy(v)));
}

void main() {
    vec2 invSize = 1.0 / screenSize;
    float depth = texture(input1, texCoord).r;
    vec2 depthDiff = getDepthDiff(invSize, texCoord, depth);
    float edge = smoothstep(0.000015 * (depth * 2 - 1), 0.00025, depthDiff.x * clamp(grad(depthDiff.y * 2) * 100,0,1));
    float brightness = mix(1.0, 0.64, edge) ;
    vec4 color = texture(input0, texCoord);
    fragColor = vec4(color.rgb * brightness, color.a);
    //fragColor = vec4(vec3(grad(depthDiff) * 1000),color.a);
}
