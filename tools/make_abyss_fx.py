"""Generates the abyss effect textures (darker, varied motifs instead of galaxies).

  void_sky.png  R=1,B=1 marker: the void shader draws the Realm sky (deep below, horizon haze, monoliths)
  corona.png    eclipse corona: a thin white ring with faint ragged streamers outward (additive, white mask)
  seam.png      a strip with a black core and a thin pale edge (alpha, baked colour): fractures, tendril segments
  tendril.png   the same strip tapering to a point at u=1, the edge light flickering along it (alpha, baked colour)
  wave.png      a pressure ring: a hard outer front with a soft trailing inner shadow (alpha mask, tint it black)
  smudge.png    4 soft ragged smudges of ink in a strip (alpha mask, tint it black)
  blackhole_*   with --recolour-blackholes only (one-shot, it edits the files in place; already applied):
                colder, less violet (only the few true singularities use them)
usage: python3 tools/make_abyss_fx.py [--recolour-blackholes]   (run from the repo root)
"""
import sys
import numpy as np
from PIL import Image, ImageFilter
OUT = 'src/main/resources/assets/abyss/textures/fx/'
rng = np.random.default_rng(7)

def save(a, name):
    Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(OUT + name)

def periodic_noise(n, maxf):
    k = np.fft.rfft(rng.standard_normal(n)); f = np.arange(len(k)); k[(f > maxf) | (f < 1)] = 0
    v = np.fft.irfft(k, n); return (v - v.min()) / (v.max() - v.min() + 1e-9)

def blur(a, s):
    return np.array(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(s))) / 255.0

# --- sky marker
a = np.zeros((4, 4, 4)); a[..., 0] = 1; a[..., 2] = 1; a[..., 3] = 1; save(a, 'void_sky.png')

# --- corona
N = 256; y, x = np.mgrid[0:N, 0:N]; x = (x - N / 2 + 0.5) / (N / 2); y = (y - N / 2 + 0.5) / (N / 2)
r = np.sqrt(x * x + y * y); th = np.arctan2(y, x)
R0 = 0.62
idx = ((th / (2 * np.pi)) % 1 * 1023).astype(int)
streams = periodic_noise(1024, 40)[idx] ** 3 * 0.8 + periodic_noise(1024, 9)[idx] * 0.4
edge = np.exp(-((r - R0) / 0.012) ** 2) * (r > R0 - 0.02)
inner = np.exp(-((r - R0) / 0.05) ** 2) * 0.35 * (r > R0)
outer = np.where(r > R0, np.exp(-(r - R0) / (0.05 + 0.18 * streams)), 0) * 0.45
fade = np.clip((1 - r) / 0.12, 0, 1)
al = np.clip(edge + inner + outer, 0, 1) * fade
c = np.ones((N, N, 4)); c[..., 3] = al; save(c, 'corona.png')

# --- seam (fracture / tendril body): v across, u along
W, H = 64, 32
v = (np.arange(H) + 0.5) / H * 2 - 1
core = np.clip(1 - np.abs(v) / 0.55, 0, 1) ** 0.6
rim = np.exp(-((np.abs(v) - 0.62) / 0.12) ** 2)
alpha = np.clip(core + rim * 0.85, 0, 1)
pale = np.array([0.72, 0.74, 0.9])
col = (rim / (core + rim + 1e-6))[:, None] * pale[None, :]
s = np.zeros((H, W, 4)); s[..., :3] = col[:, None, :]; s[..., 3] = alpha[:, None]
save(s, 'seam.png')

# --- tendril: tapering to the tip, edge light flickering
W, H = 256, 32
u = (np.arange(W) + 0.5) / W
width = (1 - u) ** 0.7 * 0.9 + 0.1
flick = 0.5 + 0.5 * periodic_noise(W, 24)
V = v[:, None] / width[None, :]
core = np.clip(1 - np.abs(V) / 0.55, 0, 1) ** 0.6
rim = np.exp(-((np.abs(V) - 0.62) / 0.13) ** 2) * flick[None, :]
alpha = np.clip(core + rim * 0.8, 0, 1) * np.clip(u / 0.06, 0, 1)
t = np.zeros((H, W, 4)); t[..., :3] = (rim / (core + rim + 1e-6))[..., None] * pale; t[..., 3] = alpha
save(t, 'tendril.png')

# --- pressure wave
N = 256; y, x = np.mgrid[0:N, 0:N]; x = (x - N / 2 + 0.5) / (N / 2); y = (y - N / 2 + 0.5) / (N / 2)
r = np.sqrt(x * x + y * y); th = np.arctan2(y, x)
idx = ((th / (2 * np.pi)) % 1 * 1023).astype(int)
ragged = 0.85 + 0.15 * periodic_noise(1024, 30)[idx]
front = 0.88
al = np.where(r < front, np.exp(-(front - r) / 0.06) * ragged, np.exp(-((r - front) / 0.012) ** 2))
w = np.ones((N, N, 4)); w[..., 3] = np.clip(al, 0, 1) * 0.95; save(w, 'wave.png')

# --- smudges: 4 ragged ink blobs
S = 64; strip = np.zeros((S, S * 4, 4)); strip[..., :3] = 1
y, x = np.mgrid[0:S, 0:S]; x = (x - S / 2 + 0.5) / (S / 2); y = (y - S / 2 + 0.5) / (S / 2)
r = np.sqrt(x * x + y * y); th = np.arctan2(y, x)
for i in range(4):
    idx = ((th / (2 * np.pi)) % 1 * 511).astype(int)
    rad = 0.72 + 0.22 * periodic_noise(512, 5)[idx]
    blob = np.clip((rad - r) / 0.35, 0, 1) ** 1.3 * np.clip((1 - r) / 0.1, 0, 1)
    grain = blur(rng.random((S, S)), 2.0)
    strip[:, i * S:(i + 1) * S, 3] = np.clip(blob * (0.6 + 0.8 * (grain - 0.5) * 2), 0, 1) * 0.9
save(strip, 'smudge.png')

# --- recolour the singularities: luminance into a cold white-blue ramp, a trace of the old colour kept
for name in (('blackhole_lensed.png', 'blackhole_swirl.png') if '--recolour-blackholes' in sys.argv else ()):
    im = np.array(Image.open(OUT + name).convert('RGB')) / 255.0
    L = im @ np.array([0.3, 0.5, 0.2])
    ramp = np.stack([L ** 1.35 * 0.92, L ** 1.2 * 0.97, L ** 0.95], -1)
    outp = ramp * 0.8 + im * 0.2
    outp *= 0.85
    Image.fromarray((np.clip(outp, 0, 1) * 255).astype(np.uint8)).save(OUT + name)
print('ok')
