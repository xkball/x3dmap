#version 460 core
#moj_import <minecraft:dynamictransforms.glsl>

layout(std140) uniform PhongLight{
    vec3 lightDir;
    vec3 viewPos;
};

layout(early_fragment_tests) in;
in vec4 vertexColor;
in vec3 worldPos;
in vec3 pNormal;
out vec4 fragColor;

const float ambient = 0.3;
const vec3 lightColor = vec3(1.0, 1.0, 1.0);
const float specularStrength = 0.2;

void main() {
    if(vertexColor.a < 1e-3){
        discard;
    }
    float diff = max(dot(pNormal, lightDir), 0.0);
    vec3 viewDir = normalize(viewPos - worldPos);
    vec3 reflectDir = reflect(-lightDir, pNormal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
    float specular = specularStrength * spec;
    fragColor = vertexColor * ColorModulator * vec4((ambient + diff + specular) * lightColor,1.0);
    //fragColor = vertexColor * ColorModulator;
}
