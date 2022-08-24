#version 310 es
precision mediump float;
uniform vec4 vColor;
in vec2 uv;
out vec4 fragColor;
in vec3 normal;

void main() {

    vec3 normColor = normal/2. + 0.5;
    fragColor = vec4(uv.x*normColor.x, uv.y*normColor.y, normColor.z, 1.);
}