#version 460 core

layout(early_fragment_tests) in;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}
