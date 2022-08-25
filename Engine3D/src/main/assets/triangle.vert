#version 310 es

in vec4 vPosition;
uniform mat4 uMVPMatrix;
out vec2 uv;
in vec2 vUV;
out vec3 normal;
in vec3 vNormal;
void main() {
    gl_Position = uMVPMatrix * vPosition;

    uv = vUV;
    normal = vNormal;

}