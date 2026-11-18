#version 150

// The abyss: a screen-space galaxy (black, starlight, purple nebula, gray contour lines) seen through whatever is drawn.
// Texture channels: R = how much galaxy shows (else dark crystal), G = purple seam glow, B = facet highlight, A = coverage.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { float v = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; } return v; }

vec3 stars(vec2 uv, float scale, float t) {
    vec2 g = uv * scale;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float r = hash(id);
    vec2 o = vec2(hash(id + 3.1), hash(id + 7.7)) - 0.5;
    float d = length(f - o * 0.7);
    float tw = 0.6 + 0.4 * sin(t * (2.0 + r * 5.0) + r * 40.0);
    float s = smoothstep(0.07 * r, 0.0, d) * step(0.8, r) * tw;
    return vec3(s) * mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.85, 1.0), hash(id + 1.3));
}

vec3 galaxy(vec2 uv, float t) {
    vec2 c = uv - 0.5 * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float a = atan(c.y, c.x);
    float r = length(c);
    vec2 sw = vec2(cos(a + r * 3.5 - t * 0.04), sin(a + r * 3.5 - t * 0.04)) * r;
    float neb = fbm(sw * 3.0 + t * 0.01);
    float neb2 = fbm(uv * 2.2 - t * 0.006);
    vec3 col = vec3(0.008, 0.006, 0.016);
    col += vec3(0.34, 0.16, 0.62) * pow(neb, 3.0) * 1.3;
    col += vec3(0.42, 0.42, 0.52) * pow(neb2, 4.0) * 0.5;
    float cl = abs(fract(neb2 * 7.0) - 0.5);
    col += vec3(0.28, 0.25, 0.34) * smoothstep(0.035, 0.0, cl) * 0.35;
    // singularity: a dark core with a lensing ring
    float core = smoothstep(0.08, 0.0, r);
    col *= 1.0 - core * 0.9;
    col += vec3(0.7, 0.55, 1.0) * smoothstep(0.012, 0.0, abs(r - 0.09)) * 0.25;
    col += stars(uv + t * 0.002, 90.0, t) + stars(uv * 1.7 - t * 0.001, 170.0, t) * 0.7 + stars(uv * 0.6, 40.0, t) * 1.4;
    return col;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 vc = vertexColor * ColorModulator;
    float alpha = tex.a * vc.a;
    if (alpha < 0.01) discard;
    float t = GameTime * 1200.0;
    vec2 uv = gl_FragCoord.xy / ScreenSize.y;
    vec3 g = galaxy(uv, t);
    vec3 crystal = vec3(0.05, 0.04, 0.08) + vec3(0.14, 0.12, 0.2) * tex.b;
    vec3 col = mix(crystal, g, tex.r);
    col += vec3(0.55, 0.26, 0.95) * tex.g * (0.95 + 0.3 * sin(t * 2.0 + texCoord0.y * 30.0));
    col *= vc.rgb;
    fragColor = vec4(col, alpha);
}
