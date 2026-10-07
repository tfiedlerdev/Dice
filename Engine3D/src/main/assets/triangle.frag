#version 310 es
precision mediump float;
uniform vec4 uColor;
in vec2 uv;
out vec4 fragColor;
in vec3 normalWorldSpace;

in vec4 positionWorldSpace;
uniform vec4 uLightColor;
uniform vec3 uLightDirection;
uniform vec3 uLightPosition;
float uLightPower = 3.;
void main() {
    vec4 MaterialDiffuseColor =uColor;//vec4(normalize(normalWorldSpace)*0.5+0.5,1.);

    float cosTheta = clamp(dot( normalWorldSpace, uLightDirection ), 0.,1. );

    float distance = abs(length(vec4(uLightPosition,1.) -  positionWorldSpace));
    vec4 MaterialAmbientColor = vec4(0.35,0.35,0.35,1.) * MaterialDiffuseColor;

    vec3 normColor = normalWorldSpace/2. + 0.5;
    fragColor = MaterialAmbientColor+MaterialDiffuseColor*vec4(vec3(uLightColor*uLightPower*cosTheta/*/(distance*distance)*/), 1.);
}