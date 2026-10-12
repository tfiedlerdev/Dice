#version 310 es

in vec4 vPosition;
uniform mat4 uMVPMatrix;
out vec2 uv;
in vec2 vUV;
out vec3 normalWorldSpace;
in vec3 vNormal;
in float vFaceValue;
flat out float faceValue;
uniform mat4 uModelMatrix;
out vec4 positionWorldSpace;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    positionWorldSpace = uModelMatrix * vPosition;
    uv = vUV;
    faceValue = vFaceValue;

    // w=0: a normal is a direction, not a point, so (unlike positionWorldSpace above)
    // it must NOT pick up the model matrix's translation.
    normalWorldSpace = (uModelMatrix * vec4(vNormal, 0.)).xyz;
}
