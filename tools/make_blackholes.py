import numpy as np
from PIL import Image, ImageFilter
OUT='/home/claude/abyss-project/src/main/resources/assets/abyss/textures/fx/'
N=512; rng=np.random.default_rng(11)
y,x=np.mgrid[0:N,0:N]; x=(x-N/2+0.5)/(N/2); y=(y-N/2+0.5)/(N/2)
def pnoise(n,maxf):
    k=np.fft.rfft(rng.standard_normal(n)); f=np.arange(len(k)); k[(f>maxf)|(f<1)]=0; v=np.fft.irfft(k,n); return (v-v.min())/(v.max()-v.min()+1e-9)
def lookup(tab,a): return tab[((a%1)*(len(tab)-1)).astype(int)]
def palette(t,stops):
    stops=np.array(stops); t=np.clip(t,0,1)*(len(stops)-1); i=np.minimum(t.astype(int),len(stops)-2); f=(t-i)[...,None]
    return stops[i]*(1-f)+stops[i+1]*f
def tone(rgb):  # filmic-ish: bright cores go white instead of clipping flat
    return 1-np.exp(-rgb*1.6)
def blur(a,s):
    return np.array(Image.fromarray((np.clip(a,0,1)*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(s)))/255
BLUE=[[0.05,0.08,0.35],[0.15,0.35,0.95],[0.35,0.75,1.0],[0.85,0.97,1.0],[1,1,1]]
VIOLET=[[0.10,0.03,0.25],[0.40,0.15,0.85],[0.65,0.35,1.0],[0.92,0.8,1.0],[1,1,1]]

# ===== 1. the swirl (image 1): many thin luminous arcs spiralling in, cyan-white inside, violet outside
tilt=0.58
xs,ys=x*1.05,y/tilt
r=np.sqrt(xs**2+ys**2); th=np.arctan2(ys,xs)
core=0.27
E=np.zeros((N,N,3))
for i in range(70):
    r0=core+0.02+rng.random()**1.4*0.62
    w=0.004+0.012*rng.random()
    a0=rng.random(); span=0.25+0.6*rng.random()
    # spiral: the arc's radius drifts with angle
    rr=r0*(1+0.10*np.sin(th*1+i)+0.03*np.sin(2*th+i*0.7))
    arc=np.exp(-((r-rr)/w)**2)
    da=((th/(2*np.pi)-a0)%1)
    env=np.clip(np.sin(np.pi*np.clip(da/span,0,1)),0,1)**1.5
    b=(0.6+1.4*rng.random())*(1.1-(r0-core)/0.75)
    t=(r0-core)/0.65
    c=palette(np.array([1-t]),[[0.55,0.25,0.95],[0.35,0.4,1.0],[0.3,0.75,1.0],[0.85,0.97,1]])[0]
    E+=(arc*env*b)[...,None]*c
# soft body glow under the arcs
body=np.exp(-((r-core-0.18)/0.2)**2)*(r>core)
E+=body[...,None]*palette(1-np.clip((r-core)/0.5,0,1),[[0.2,0.08,0.45],[0.15,0.3,0.8],[0.3,0.6,1.0]])*0.45
# the photon ring: thin, white-cyan, brightest at the bottom (lensed front)
E+=(np.exp(-((r-core)/0.011)**2)*(1.4+0.6*np.sin(th)))[...,None]*np.array([0.85,0.97,1.0])
E*=(r>core*0.985)[...,None]
E=tone(E)
E=blur(E,0.7)*0.6+tone(blur(E,6))*0.55            # bloom
fade=np.clip((1-np.sqrt(x**2+y**2))/0.12,0,1)[...,None]
Image.fromarray((np.clip(E*fade,0,1)*255).astype(np.uint8)).save(OUT+'blackhole_swirl.png')

# ===== 2. lensed (image 2): hot disk across, its far side lensed into a ring around the shadow
r=np.sqrt(x**2+y**2); th=np.arctan2(y,x)
core=0.25
E=np.zeros((N,N,3))
ripple=0.6+0.4*np.cos(r*140)**2
# concentric lensed ring: bright at the shadow edge, many fine rings outward, brighter on top (far side of the disk)
ringI=0.55*np.exp(-((r-core-0.04)/0.06)**2)*ripple*(0.6+0.9*np.clip(-y/np.maximum(r,1e-3),0,1))
ringI+=np.exp(-((r-core-0.004)/0.007)**2)*1.6
ringI+=np.exp(-((r-0.45)/0.1)**2)*0.12*ripple
E+=(ringI*(r>core))[...,None]*palette(np.clip(1-(r-core)/0.35,0,1),VIOLET)
# the disk band: tilted slightly, thick and white-hot near the hole, clumpy dust lanes, fading at the tips
ty=y+0.08*x
thick=0.035+0.07*np.exp(-x**2/0.18)
dens=lookup(pnoise(4096,25),x*0.5+0.5)*0.55+lookup(pnoise(4096,8),x*0.37+0.2)*0.45
band=np.exp(-(ty/thick)**2)*np.exp(-(np.abs(x)/0.98)**6)*(0.35+0.9*dens)
band*=1+0.15*np.exp(-x**2/0.04)                       # hottest at the centre
band*=1.25-0.45*x                                    # approaching side brighter
# dark dust lanes inside the band
lane=1-0.5*np.exp(-((ty-0.01*np.sin(x*9))/(thick*0.22))**2)*lookup(pnoise(4096,40),x*0.5+0.5)
band*=lane
E+=band[...,None]*palette(np.clip(0.2+band*0.55,0,1),VIOLET)*1.25
E=tone(E)
E=blur(E,0.6)*0.65+tone(blur(E,7))*0.5
fade=np.clip((1-np.maximum(np.abs(x),np.abs(y)*1.0))/0.06,0,1)[...,None]
Image.fromarray((np.clip(E*fade,0,1)*255).astype(np.uint8)).save(OUT+'blackhole_lensed.png')

for n,cr,tl in [('blackhole_swirl',0.27,tilt),('blackhole_lensed',0.25,1)]:
    im=np.array(Image.open(OUT+n+'.png'))/255
    bg=np.zeros_like(im); bg[...]=[0.03,0.02,0.07]
    bg[rng.random(im.shape[:2])>0.997]=0.8
    rr=np.sqrt((x*(1.05 if tl!=1 else 1))**2+(y/tl)**2); bg[rr<cr]=0
    Image.fromarray((np.clip(bg+im,0,1)*255).astype(np.uint8)).save('/tmp/prev_'+n+'.png')
s=Image.new('RGB',(1024,512))
for i,n in enumerate(['blackhole_swirl','blackhole_lensed']): s.paste(Image.open(f'/tmp/prev_{n}.png'),(i*512,0))
s.save('/tmp/bh_prev.png'); print('ok')
