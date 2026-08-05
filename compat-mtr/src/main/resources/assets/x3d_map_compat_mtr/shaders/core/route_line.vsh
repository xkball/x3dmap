#version 430

layout(std140) uniform ScreenSize{
    vec2 screenSize;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 P0;
layout(location = 3) in vec3 P1;
layout(location = 4) in vec2 Corner;

out vec4 vColor;

void main() {
    vec4 clip0 = ProjMat * vec4(P0, 1.0);
    vec4 clip1 = ProjMat * vec4(P1, 1.0);

    bool aBehind = clip0.z < -clip0.w;
    bool bBehind = clip1.z < -clip1.w;

    if (aBehind && bBehind) {
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        vColor = Color;
        return;
    }

    if (aBehind != bBehind) {
        float t = (-clip0.w - clip0.z) / ((clip1.z + clip1.w) - (clip0.z + clip0.w));
        vec4 clipP = mix(clip0, clip1, t);
        if (aBehind)
            clip0 = clipP;
        else
            clip1 = clipP;
    }

    vec4 clip = mix(clip0, clip1, Corner.y);

    vec2 ndc0 = clip0.xy / clip0.w;
    vec2 ndc1 = clip1.xy / clip1.w;

    vec2 dir = normalize(ndc1 - ndc0);
    vec2 n = vec2(-dir.y, dir.x);

    vec2 offset = n * Corner.x * 5.0 / screenSize;
    clip.xy += offset * clip.w;

    gl_Position = clip;
    vColor = Color;
}
