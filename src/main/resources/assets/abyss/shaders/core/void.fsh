#version 150

// The abyss: not a galaxy but a depth. Mostly black, with cold blue-black mists layered at different distances,
// darker dust, an abyssal current streaking past, very sparse cold stars, and a dark singularity rimmed in white-blue.
// Fixed in world space and seen through whatever is drawn with this shader.
// Texture channels: R = how much abyss shows (else obsidian crystal), G = seam glow, B = facet sheen, A = coverage.
// A texture with R = 1 and B = 1 (void_sky.png) marks the Realm's sky: the deep below, a horizon haze and far monoliths.

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
    float tw = 0.6 + 0.4 * sin(t * (0.6 + r * 2.0) + r * 50.0);
    float s = (1.0 - smoothstep(0.0, 0.05 + 0.06 * r, dist)) * step(1.0 - density, r) * tw * 1.6;
    // cold white-blue, rarely a dim violet one
    vec3 c = hash3(id + 9.4) > 0.93 ? vec3(0.62, 0.5, 0.95) : mix(vec3(0.62, 0.72, 1.0), vec3(0.92, 0.94, 1.0), hash3(id + 3.3));
    return vec3(s) * c;
}

vec3 rotY(vec3 d, float a) { return vec3(d.x * cos(a) - d.z * sin(a), d.y, d.x * sin(a) + d.z * cos(a)); }

// the abyssal current: noise stretched along a slow flow around a tilted axis, so it reads as long faint streaks
float current(vec3 d, float t) {
    vec3 ax = normalize(vec3(-0.35, 1.0, 0.2));
    vec3 u = normalize(cross(ax, vec3(1.0, 0.0, 0.0))), v = cross(ax, u);
    float h = dot(d, ax);
    float phi = atan(dot(d, v), dot(d, u)) + t * 0.006;
    float n = fbm3(vec3(cos(phi) * 1.4, sin(phi) * 1.4, h * 26.0));
    float lane = exp(-h * h / 0.10);
    float breakup = smoothstep(0.35, 0.75, noise3(vec3(cos(phi) * 3.0, sin(phi) * 3.0, h * 4.0) + 3.0));
    return pow(smoothstep(0.52, 0.78, n), 2.0) * lane * breakup;
}

vec3 galaxy(vec3 d, float t) {
    // slow breathing: the whole dark swells and settles, as if something vast is there
    float breath = 0.88 + 0.12 * sin(t * 0.35);
    vec3 col = vec3(0.0035, 0.004, 0.008);
    // far layer: enormous blue-black banks, barely there, turning slowest
    vec3 d0 = rotY(d, t * 0.0004);
    float far = fbm3(d0 * 1.3 + vec3(4.0, 1.0, 7.0));
    col += vec3(0.010, 0.014, 0.034) * smoothstep(0.38, 0.8, far) * 1.4;
    // middle layer: cold indigo mists, domain-warped so they curl
    vec3 d1 = rotY(d, t * 0.0011);
    vec3 q = vec3(fbm3(d1 * 1.8 + vec3(0.0, 0.0, t * 0.0015)), fbm3(d1 * 1.8 + vec3(5.2, 1.3, 2.8)), 0.0);
    float mid = fbm3(d1 * 2.6 + 2.2 * vec3(q.xy, q.x * q.y));
    float mist = pow(smoothstep(0.45, 0.85, mid), 1.6);
    col += mix(vec3(0.022, 0.026, 0.07), vec3(0.03, 0.022, 0.07), smoothstep(0.4, 0.7, q.y)) * mist * 1.9 * breath;
    // the old colour survives only in thin filaments along the edges of the mist
    float ridge = 1.0 - abs(mid - 0.6) * 9.0;
    col += vec3(0.20, 0.12, 0.38) * pow(clamp(ridge, 0.0, 1.0), 6.0) * smoothstep(0.55, 0.8, q.x) * 0.22;
    // near layer: fine grey-blue wisps drifting faster (parallax against the far banks)
    vec3 d2 = rotY(d, t * 0.0032);
    float wn = fbm3(d2 * 5.5 + 9.0);
    col += vec3(0.016, 0.019, 0.03) * pow(clamp(1.0 - abs(wn - 0.5) * 3.5, 0.0, 1.0), 5.0) * smoothstep(0.4, 0.7, far) * breath;
    // dark dust: swallows the mist in long lanes
    float dust = fbm3(d1 * 3.4 + 21.0);
    float lanes = smoothstep(0.48, 0.66, dust);
    col *= 1.0 - 0.85 * lanes;
    // the current: faint pale streaks flowing past
    col += vec3(0.035, 0.045, 0.075) * current(d, t) * (1.0 - 0.7 * lanes);
    // stars: sparse and cold, hidden behind the dust
    col += (stars(d, 120.0, t, 0.035) * 0.7 + stars(d, 300.0, t, 0.05) * 0.3 + stars(d, 46.0, t, 0.012) * 1.1) * (1.0 - 0.8 * lanes);

    // the singularity: a black shadow, a thin white-blue photon ring, a cold dim disk; around it the dark deepens
    vec3 sdir = normalize(vec3(0.45, 0.32, -0.83));
    vec3 su = normalize(cross(sdir, vec3(0.0, 1.0, 0.0))), sv = cross(su, sdir);
    float sd = dot(d, sdir);
    vec2 p = vec2(dot(d, su), dot(d, sv)) / max(sd, 0.001);
    if (sd > 0.0) {
        float R = 0.11, r = length(p);
        col *= mix(0.35, 1.0, smoothstep(R, R * 5.0, r));            // the well: light thins out around it
        float ty = p.y + 0.08 * p.x;
        float thick = 0.007 + 0.016 * exp(-p.x * p.x / 0.008);
        float disk = exp(-ty * ty / (thick * thick)) * exp(-pow(abs(p.x) / 0.5, 4.0));
        disk *= 0.55 + 0.6 * noise3(vec3(p.x * 60.0, 1.0, t * 0.05));
        disk *= 1.2 - 1.3 * p.x;
        float ripple = 0.6 + 0.4 * pow(cos(r * 300.0), 2.0);
        float ring = 0.7 * exp(-pow((r - R - 0.016) / 0.02, 2.0)) * ripple * (0.5 + 0.7 * clamp(-p.y / max(r, 1e-3), 0.0, 1.0));
        ring += exp(-pow((r - R - 0.0015) / 0.0022, 2.0)) * 1.4;
        vec3 hot = vec3(0.86, 0.92, 1.0), cold = vec3(0.28, 0.34, 0.6);
        col *= smoothstep(R * 0.97, R * 1.03, r);                      // the shadow swallows everything behind it
        col += mix(cold, hot, clamp(ring * 0.7, 0.0, 1.0)) * ring * step(R, r);
        col += mix(cold, hot, clamp(disk * 0.8, 0.0, 1.0)) * disk * 0.75;
        col += vec3(0.05, 0.06, 0.11) * exp(-pow((r - R) / 0.1, 2.0)) * 0.5;   // a faint cold halo
    }
    return col;
}

// The Realm's sky: the abyss above, a deeper dark below, a cold haze far off at eye level and the silhouettes of
// monoliths standing in it, a few with a dim light; one far pillar of light rising out of the deep.
vec3 abyssSky(vec3 d, float t, float sky) {
    vec3 col = galaxy(d, t);
    if (sky < 0.5) return col;
    float y = d.y;
    col *= mix(0.25, 1.0, smoothstep(-0.45, 0.08, y));                 // below: it only gets deeper
    float az = atan(d.z, d.x);
    float hz = fbm3(vec3(cos(az) * 3.0, sin(az) * 3.0, y * 8.0) + vec3(0.0, 0.0, t * 0.002));
    vec3 haze = vec3(0.016, 0.020, 0.038) * exp(-y * y / 0.012) * (0.6 + 0.8 * hz);
    col += haze;
    // monoliths: two rings of them, the far ring closer to the haze colour (atmospheric depth)
    for (int k = 0; k < 2; k++) {
        float N = k == 0 ? 17.0 : 37.0;
        float cellA = (az + 3.14159265) / 6.2831853 * N;
        float id = floor(cellA), fx = fract(cellA) - 0.5;
        float h = hash3(vec3(id, float(k) * 7.0, 3.0));
        if (h < (k == 0 ? 0.62 : 0.6)) continue;
        float w = (k == 0 ? 0.04 : 0.03) + 0.12 * pow(hash3(vec3(id, 1.0, float(k))), 2.0);
        float off = (hash3(vec3(id, 2.0, float(k))) - 0.5) * 0.5;
        float hr = hash3(vec3(id, 4.0, float(k)));
        float top = (k == 0 ? 0.025 : 0.012) + (k == 0 ? 0.22 : 0.05) * hr * hr * hr;
        float lean = (hash3(vec3(id, 6.0, float(k))) - 0.5) * 0.6;
        float x = fx - off - lean * (y + 0.1);
        float taper = clamp((top - y) / top, 0.0, 1.0);
        float wy = w * (0.35 + 0.65 * sqrt(taper));
        float jag = 0.004 * sin(id * 13.0 + floor(x * 40.0));
        float inside = step(abs(x), wy) * step(y, top + jag) * step(-0.25, y);
        vec3 tint = k == 0 ? vec3(0.002, 0.002, 0.004) : haze * 0.55 + vec3(0.003, 0.003, 0.006);
        col = mix(col, tint, inside);
        // a dim cold light near the top of some of them, slowly breathing
        if (hash3(vec3(id, 9.0, float(k))) > 0.6) {
            vec2 lp = vec2(x / N * 6.2831853, y - top * 0.85);
            float l = exp(-dot(lp, lp) / (k == 0 ? 0.00003 : 0.00001));
            col += vec3(0.55, 0.62, 0.9) * l * (0.35 + 0.25 * sin(t * 0.4 + id)) * (k == 0 ? 1.0 : 0.6);
        }
    }
    // one far pillar of pale light rising out of the deep
    float pa = az - 2.2;
    pa = mod(pa + 3.14159265, 6.2831853) - 3.14159265;
    col += vec3(0.05, 0.06, 0.1) * exp(-pa * pa / 0.00012) * smoothstep(0.35, -0.05, y) * smoothstep(-0.5, -0.05, y) * (0.8 + 0.2 * sin(t * 0.3));
    col += vec3(0.02, 0.025, 0.045) * exp(-pa * pa / 0.004) * exp(-y * y / 0.01);
    return col;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 vc = vertexColor * ColorModulator;
    float alpha = tex.a * vc.a;
    if (alpha < 0.01) discard;
    float t = GameTime * 1200.0;
    float sky = step(0.99, tex.r) * step(0.99, tex.b) * step(tex.g, 0.01);
    vec3 g = abyssSky(normalize(worldDir), t, sky);
    // obsidian: near-black with a cold sheen on the facets
    vec3 crystal = vec3(0.010, 0.010, 0.016) + vec3(0.055, 0.06, 0.085) * tex.b;
    vec3 col = mix(crystal, g, tex.r * (1.0 - sky) + sky);
    // seams: thin, cold pale violet, a slow pulse rather than neon
    float seam = smoothstep(0.3, 1.0, tex.g) * (1.0 - sky);
    col += vec3(0.40, 0.36, 0.62) * seam * (0.8 + 0.2 * sin(t * 1.2 + texCoord0.y * 30.0)) + vec3(0.03, 0.03, 0.07) * tex.g * (1.0 - sky);
    col *= vc.rgb;
    fragColor = vec4(col, alpha);
}
