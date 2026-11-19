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
float fbm3(vec3 p) { float v = 0.0, a = 0.5; for (int i = 0; i < 6; i++) { v += a * noise3(p); p = p * 2.02 + 11.7; a *= 0.5; } return v; }

vec3 stars(vec3 d, float scale, float t, float density) {
    vec3 p = d * scale;
    vec3 id = floor(p), f = fract(p) - 0.5;
    float r = hash3(id + 7.0);
    vec3 o = vec3(hash3(id + 1.3), hash3(id + 2.9), hash3(id + 5.1)) - 0.5;
    float dist = length(f - o * 0.6);
    float tw = 0.7 + 0.3 * sin(t * (1.5 + r * 4.0) + r * 50.0);
    float s = (1.0 - smoothstep(0.0, 0.07 + 0.08 * r, dist)) * step(1.0 - density, r) * tw * 2.0;
    return vec3(s) * mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.85, 0.95), hash3(id + 9.4));
}

// the Abyss nebula: billowing violet/magenta clouds with blue and crimson pockets, dark dust lanes, star clusters,
// and a lensed black hole (Gargantua) hanging in it
vec3 galaxy(vec3 d, float t) {
    float a = t * 0.0012;
    d = vec3(d.x * cos(a) - d.z * sin(a), d.y, d.x * sin(a) + d.z * cos(a));
    // domain warp: clouds that billow and curl instead of plain noise
    vec3 q = vec3(fbm3(d * 1.6 + vec3(0.0, 0.0, t * 0.002)), fbm3(d * 1.6 + vec3(5.2, 1.3, 2.8)), fbm3(d * 1.6 + vec3(2.1, 7.7, 4.4)));
    vec3 w = d * 2.2 + 2.6 * q;
    float n = fbm3(w);
    float n2 = fbm3(w * 2.3 + 4.0);
    float band = exp(-pow(dot(d, normalize(vec3(0.3, 0.9, 0.25))), 2.0) / 0.12);
    float cloud = smoothstep(0.35, 0.85, n) * (0.35 + 0.9 * band);
    vec3 violet = vec3(0.42, 0.16, 0.75), magenta = vec3(0.78, 0.25, 0.70), blue = vec3(0.22, 0.30, 0.85), crimson = vec3(0.75, 0.12, 0.25), white = vec3(1.0, 0.85, 1.0);
    vec3 cc = mix(violet, magenta, smoothstep(0.4, 0.8, q.x));
    cc = mix(cc, blue, smoothstep(0.48, 0.78, q.y));
    cc = mix(cc, crimson, smoothstep(0.55, 0.82, q.z) * 0.85);
    vec3 col = vec3(0.008, 0.005, 0.02) + vec3(0.03, 0.015, 0.07) * band;
    col += cc * cloud * 1.15;
    col += cc * smoothstep(0.55, 0.9, n) * smoothstep(0.5, 0.8, n2) * 0.6;   // lit cloud edges
    col += white * pow(smoothstep(0.55, 0.9, n * n2 * 1.9), 2.0) * (0.4 + band) * 0.7;     // bright cores in the clouds
    col *= 1.0 - 0.75 * smoothstep(0.5, 0.75, n2) * smoothstep(0.45, 0.7, n);         // dark dust lanes
    float cluster = smoothstep(0.55, 0.9, fbm3(d * 3.0 + 9.0));
    col += stars(d, 110.0, t, 0.10 + 0.25 * cluster) + stars(d, 260.0, t, 0.15 + 0.3 * cluster) * 0.7 + stars(d, 45.0, t, 0.03) * 1.4;

    // the singularity: black shadow, a thin photon ring, lensed rings, and the hot disk across it (Gargantua)
    vec3 sdir = normalize(vec3(0.45, 0.32, -0.83));
    vec3 su = normalize(cross(sdir, vec3(0.0, 1.0, 0.0))), sv = cross(su, sdir);
    vec2 p = vec2(dot(d, su), dot(d, sv)) / max(dot(d, sdir), 0.001);
    if (dot(d, sdir) > 0.0) {
        float R = 0.11, r = length(p);
        float ty = p.y + 0.08 * p.x;
        float thick = 0.009 + 0.022 * exp(-p.x * p.x / 0.008);
        float disk = exp(-ty * ty / (thick * thick)) * exp(-pow(abs(p.x) / 0.6, 4.0));
        disk *= 0.6 + 0.6 * noise3(vec3(p.x * 60.0, 1.0, t * 0.05)) ;
        disk *= 1.25 - 1.4 * p.x;
        float ripple = 0.6 + 0.4 * pow(cos(r * 300.0), 2.0);
        float ring = 1.3 * exp(-pow((r - R - 0.018) / 0.024, 2.0)) * ripple * (0.6 + 0.8 * clamp(-p.y / max(r, 1e-3), 0.0, 1.0));
        ring += exp(-pow((r - R - 0.0015) / 0.0025, 2.0)) * 2.0;
        vec3 hot = vec3(0.9, 0.78, 1.0), warm = vec3(0.55, 0.3, 1.0);
        col *= smoothstep(R * 0.97, R * 1.03, r);                      // the shadow swallows everything behind it
        col += mix(warm, hot, clamp(ring * 0.6, 0.0, 1.0)) * ring * step(R, r);
        col += mix(warm, hot, clamp(disk, 0.0, 1.0)) * disk * 1.3;
        col += warm * exp(-pow((r - R) / 0.14, 2.0)) * 0.2;          // soft halo
    }
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
