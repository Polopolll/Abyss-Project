#version 150
// separable gaussian (9 taps via linear sampling) along Dir, in UV units
uniform sampler2D Sampler0;
uniform vec2 Dir;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec3 c = texture(Sampler0, uv).rgb * 0.2270270270;
    c += (texture(Sampler0, uv + Dir * 1.3846153846).rgb + texture(Sampler0, uv - Dir * 1.3846153846).rgb) * 0.3162162162;
    c += (texture(Sampler0, uv + Dir * 3.2307692308).rgb + texture(Sampler0, uv - Dir * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
