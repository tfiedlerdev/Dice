#version 310 es
precision mediump float;
uniform vec4 uColor;
in vec2 uv;
out vec4 fragColor;
in vec3 normalWorldSpace;
flat in float faceValue;

in vec4 positionWorldSpace;
uniform vec4 uLightColor;
uniform vec3 uLightPosition;
uniform vec3 uSecondaryLightDirection;
uniform float uPointLightEnabled;
float uLightPower = 0.85;
// Directional (not positional) lights don't attenuate with distance the way the point
// light above does, so this is tuned down on its own to compensate.
float uSecondaryLightPower = 0.55;

// A single pip (dot), antialiased over one pixel-ish of UV space rather than
// hard-edged.
float pip(vec2 uv, vec2 center, float radius) {
    return 1. - smoothstep(radius - 0.015, radius, distance(uv, center));
}

// The conventional 1-6 face layout on a 3x3 grid of pip positions. `value` is
// rounded to the nearest int by the caller - faceValue is 0 (not a die face,
// e.g. the floor/walls) for anything this should leave untouched.
float pipMask(vec2 uv, int value) {
    float r = 0.09;
    vec2 topLeft = vec2(0.23, 0.23);
    vec2 topRight = vec2(0.77, 0.23);
    vec2 midLeft = vec2(0.23, 0.5);
    vec2 center = vec2(0.5, 0.5);
    vec2 midRight = vec2(0.77, 0.5);
    vec2 bottomLeft = vec2(0.23, 0.77);
    vec2 bottomRight = vec2(0.77, 0.77);

    float m = 0.;
    if (value == 1) {
        m = pip(uv, center, r);
    } else if (value == 2) {
        m = max(pip(uv, topLeft, r), pip(uv, bottomRight, r));
    } else if (value == 3) {
        m = max(max(pip(uv, topLeft, r), pip(uv, center, r)), pip(uv, bottomRight, r));
    } else if (value == 4) {
        m = max(max(pip(uv, topLeft, r), pip(uv, topRight, r)), max(pip(uv, bottomLeft, r), pip(uv, bottomRight, r)));
    } else if (value == 5) {
        float corners = max(max(pip(uv, topLeft, r), pip(uv, topRight, r)), max(pip(uv, bottomLeft, r), pip(uv, bottomRight, r)));
        m = max(corners, pip(uv, center, r));
    } else if (value == 6) {
        float left = max(pip(uv, topLeft, r), max(pip(uv, midLeft, r), pip(uv, bottomLeft, r)));
        float right = max(pip(uv, topRight, r), max(pip(uv, midRight, r), pip(uv, bottomRight, r)));
        m = max(left, right);
    }
    return m;
}

void main() {
    vec3 normal = normalize(normalWorldSpace);

    vec4 bodyColor = uColor;
    vec4 pipColor = vec4(0.08, 0.08, 0.1, 1.);
    float pipAmount = pipMask(uv, int(faceValue + 0.5));
    vec4 MaterialDiffuseColor = mix(bodyColor, pipColor, pipAmount);

    vec3 toLight = uLightPosition - positionWorldSpace.xyz;
    float distance = length(toLight);
    vec3 lightDir = distance > 0.0001 ? toLight / distance : vec3(0., 1., 0.);
    float cosTheta = clamp(dot(normal, lightDir), 0., 1.);
    // Gentle falloff - the room is small, so this mostly keeps corners from
    // reading as dramatically darker than directly under the light.
    float attenuation = 1. / (1. + 0.1 * distance + 0.02 * distance * distance);
    float pointLightAmount = uPointLightEnabled * uLightPower * cosTheta * attenuation;

    // The overhead point light is nearly edge-on to a vertical wall, so on its own
    // walls would only ever show their flat ambient color - this angled, un-attenuated
    // light is what actually reveals them.
    float secondaryCosTheta = clamp(dot(normal, normalize(uSecondaryLightDirection)), 0., 1.);
    float secondaryLightAmount = uSecondaryLightPower * secondaryCosTheta;

    vec4 MaterialAmbientColor = vec4(0.5, 0.5, 0.5, 1.) * MaterialDiffuseColor;
    fragColor = MaterialAmbientColor + MaterialDiffuseColor * vec4(vec3(uLightColor) * (pointLightAmount + secondaryLightAmount), 1.);
    fragColor.a = 1.;
}
