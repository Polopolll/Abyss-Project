#version 150
// the sharp glow pass plus two blurred copies (half and quarter resolution): a real halo that bleeds past the shape
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float Intensity;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec3 s = texture(Sampler0, uv).rgb;
    vec3 b1 = texture(Sampler1, uv).rgb;
    vec3 b2 = texture(Sampler2, uv).rgb;
    vec3 halo = (b1 * 0.85 + b2 * 1.25) * Intensity;
    // soft-knee tone so stacked glows roll off instead of clipping to flat white
    vec3 c = s + halo;
    c = c / (1.0 + max(vec3(0.0), c - 0.85) * 0.6);
    fragColor = vec4(c, 1.0);
}
