//layout(location = 0) in vec2 vertexUV;
attribute vec4 vPosition;
uniform mat4 uMVPMatrix;
//out vec2 uv;
void main() {
    gl_Position = uMVPMatrix * vPosition;

    //uv = vertexUV;
}