#version 460 core
//#extension  GL_ARB_shader_draw_parameters : require
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec4 vertexColor;
out vec3 worldPos;
out vec3 pNormal;

void main() {

//    worldPos.y -= Position.y < 0.01 ? 10 : 0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    worldPos = Position;
    vertexColor = Color;
    pNormal = Normal;
}
