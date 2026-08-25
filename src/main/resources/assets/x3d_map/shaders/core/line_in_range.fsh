#version 330 core

layout(std140) uniform Fade{
    vec2 fadeStartEnd;
};

in vec4 vColor;
in vec4 vPos;
out vec4 FragColor;

void main() {
    vec3 pos = vPos.xyz / vPos.w;
    FragColor = vColor * (1 - smoothstep(fadeStartEnd.x,fadeStartEnd.y,dot(pos, pos)));
}
