#version 310 es

in vec4 vPosition;
uniform mat4 uMVPMatrix;
out vec2 uv;
in vec2 vUV;
out vec3 normalWorldSpace;
in vec3 vNormal;
uniform mat4 uModelMatrix;
out vec4 positionWorldSpace;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    positionWorldSpace = uModelMatrix * vPosition;
    uv = vUV;

    vec4 normalTemp = (uModelMatrix *vec4(vNormal,1.));
    normalWorldSpace =normalTemp.xyz/normalTemp.w;

}