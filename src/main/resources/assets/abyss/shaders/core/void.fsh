#version 150

// The abyss: a galaxy fixed in world space (black, starlight, violet nebula along a galactic band, gray contour
// lines, a distant singularity with a lensing ring), seen through whatever is drawn with this shader.
// Texture channels: R = how much galaxy shows (else dark crystal), G = violet seam glow, B = facet sheen, A = coverage.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldDir;

out vec4 fragColor;

float hash3(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise3(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm3(vec3 p) { float v = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { v += a * noise3(p); p = p * 2.03 + 11.7; a *= 0.5; } return v; }

vec3 stars(vec3 d, float scale, float t, float density) {
    vec3 p = d * scale;
    vec3 id = floor(p), f = fract(p) - 0.5;
    float r = hash3(id + 7.0);
    vec3 o = vec3(hash3(id + 1.3), hash3(id + 2.9), hash3(id + 5.1)) - 0.5;
    float dist = length(f - o * 0.6);
    float tw = 0.65 + 0.35 * sin(t * (1.5 + r * 4.0) + r * 50.0);
    float s = (1.0 - smoothstep(0.0, 0.09 + 0.1 * r, dist)) * step(1.0 - density, r) * tw * 1.8;
    return vec3(s) * mix(vec3(0.72, 0.8, 1.0), vec3(1.0, 0.86, 1.0), hash3(id + 9.4));
}

vec3 galaxy(vec3 d, float t) {
    // a slow drift of the whole sky
    float a = t * 0.0015;
    d = vec3(d.x * cos(a) - d.z * sin(a), d.y, d.x * sin(a) + d.z * cos(a));
    vec3 band = normalize(vec3(0.25, 0.92, 0.3));
    float h = dot(d, band);
    float inBand = exp(-h * h / 0.06);
    float neb = fbm3(d * 2.6 + vec3(0.0, t * 0.004, 0.0));
    float neb2 = fbm3(d * 5.5 - 3.0);
    vec3 col = vec3(0.006, 0.004, 0.013);
    col += vec3(0.30, 0.13, 0.58) * pow(neb, 2.6) * (0.35 + 1.4 * inBand);
    col += vec3(0.55, 0.30, 0.85) * pow(neb2, 4.0) * inBand * 0.9;
    col += vec3(0.40, 0.40, 0.50) * pow(neb2, 5.0) * 0.35;
    float cl = abs(fract(neb * 6.0) - 0.5);
    col += vec3(0.26, 0.24, 0.32) * (1.0 - smoothstep(0.0, 0.03, cl)) * 0.3 * (0.3 + inBand);
    // a distant singularity: dark core, white-violet lensing ring
    vec3 sdir = normalize(vec3(0.45, 0.35, -0.82));
    float ang = acos(clamp(dot(d, sdir), -1.0, 1.0));
    col *= mix(0.05, 1.0, smoothstep(0.035, 0.06, ang));
    col += vec3(0.85, 0.75, 1.0) * (1.0 - smoothstep(0.0, 0.012, abs(ang - 0.062))) * 0.8;
    col += vec3(0.5, 0.3, 0.9) * exp(-pow((ang - 0.062) / 0.04, 2.0)) * 0.25;
    col += stars(d, 90.0, t, 0.14) + stars(d, 220.0, t, 0.2) * 0.8 + stars(d, 40.0, t, 0.05) * 1.3 * (0.4 + inBand);
    return col;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 vc = vertexColor * ColorModulator;
    float alpha = tex.a * vc.a;
    if (alpha < 0.01) discard;
    float t = GameTime * 1200.0;
    vec3 g = galaxy(normalize(worldDir), t);
    vec3 crystal = vec3(0.05, 0.04, 0.08) + vec3(0.14, 0.12, 0.2) * tex.b;
    vec3 col = mix(crystal, g, tex.r);
    col += vec3(0.55, 0.26, 0.95) * tex.g * (0.95 + 0.3 * sin(t * 2.0 + texCoord0.y * 30.0));
    col *= vc.rgb;
    fragColor = vec4(col, alpha);
}
