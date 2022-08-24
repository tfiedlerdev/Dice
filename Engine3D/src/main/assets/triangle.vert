#version 310 es

in vec4 vPosition;
uniform mat4 uMVPMatrix;
out vec2 uv;
in vec2 vUV;
out vec3 normal;

void main() {
    gl_Position = uMVPMatrix * vPosition;

    //uv = vUV;


    if (vPosition.z == -.5 || vPosition.z == .5) {
        // front or back
        uv = vPosition.xy + vec2(0.5, 0.);
        if(vPosition.z< 0.){
            // back
            normal = vec3(0.,0.,-1.);
        }
        else{
            //front
            normal = vec3(0.,0.,1.);
        }
    }
    else if (vPosition.y == 0. || vPosition.y == 1.) {
        // top or bottom
        uv = vPosition.xz + 0.5;
        if(vPosition.y< 0.5){
            // bottom
            normal = vec3(0.,-1.,0.);
        }
        else{
            //top
            normal = vec3(0.,1.,0.);
        }
    }
    else {
        // left or right
        uv = vPosition.zy + vec2(0.5, 0.);
        if(vPosition.x< 0.){
            // left
            normal = vec3(-1.,0.,0.);
        }
        else{
            //right
            normal = vec3(1.,0.,0.);
        }
    }
}