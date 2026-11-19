import numpy as np, subprocess, os
from scipy.signal import butter, sosfilt, fftconvolve
SR=44100
OUT='/home/claude/abyss-project/src/main/resources/assets/abyss/sounds/'
rng=np.random.default_rng(7)
def t(d): return np.arange(int(SR*d))/SR
def noise(d): return rng.standard_normal(int(SR*d))
def bp(x,lo,hi,o=2): return sosfilt(butter(o,[lo,hi],'band',fs=SR,output='sos'),x)
def lp(x,f,o=2): return sosfilt(butter(o,f,'low',fs=SR,output='sos'),x)
def hp(x,f,o=2): return sosfilt(butter(o,f,'high',fs=SR,output='sos'),x)
def env(n,a,dcy,shape=3.0):
    e=np.ones(n); na=max(1,int(SR*a)); e[:na]=np.linspace(0,1,na)**1.5
    k=np.arange(n-na)/SR; e[na:]=np.exp(-k/dcy*shape/3)
    return e
def sweep_noise(d,f0,f1,q=0.6):
    """noise through a band-pass whose centre glides f0->f1 (block-wise)"""
    x=noise(d); out=np.zeros_like(x); B=512
    for i in range(0,len(x),B):
        k=i/len(x); fc=f0*(f1/f0)**k
        lo=max(40,fc*(1-q)); hi=min(SR/2-100,fc*(1+q))
        out[i:i+B]=bp(x[i:i+B+0],lo,hi)[:len(x[i:i+B])]
    return out
def reverb(x,length=1.2,mix=0.25,damp=4000):
    # no hard edge where the dry sound stops: fade its last 20%
    f=max(1,int(len(x)*0.2)); x=x.copy(); x[-f:]*=np.linspace(1,0,f)**2
    n=int(SR*length); ir=noise(length)*np.exp(-np.arange(n)/SR*8/length); ir=lp(ir,min(damp,2600),4)
    ir[:int(SR*0.012)]=0
    wet=fftconvolve(x,ir); wet/=np.max(np.abs(wet))+1e-9
    y=np.concatenate([x,np.zeros(n)]); m=min(len(y),len(wet)); y=y[:m]; wet=wet[:m]; return y*(1-mix*0.5)+wet*mix*0.6*np.max(np.abs(x))
def tone(freqs,d,dec,glide=0.0):
    tt=t(d); y=np.zeros_like(tt)
    for i,f in enumerate(freqs):
        ph=2*np.pi*np.cumsum(f*(1+glide*tt/d))/SR
        y+=np.sin(ph)*np.exp(-tt/(dec*(1-0.12*i)))/(1+i*0.4)
    return y
def save(name,y,gain=0.9):
    y=y/ (np.max(np.abs(y))+1e-9)*gain
    fade=int(SR*0.01); y[-fade:]*=np.linspace(1,0,fade)
    wav=f'/tmp/{name}.wav'
    import wave
    with wave.open(wav,'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((y*32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',wav,'-c:a','libvorbis','-q:a','5',OUT+name+'.ogg'],check=True)

# --- swings: a quick airy cut through the air, three takes so repeats don't sound copy-pasted
for i,(f0,f1,d) in enumerate([(2600,700,0.22),(3000,800,0.2),(2200,600,0.24)]):
    w=sweep_noise(d,f0,f1,0.5)*env(int(SR*d),0.06,d*0.5)
    save(f'arsenal_swing_{i+1}',reverb(w,0.4,0.12))
# heavy swing: lower, longer, a body of air moving
d=0.48; w=sweep_noise(d,900,140,0.6)*env(int(SR*d),0.18,0.22)+0.6*lp(noise(d),200)*env(int(SR*d),0.2,0.2)
save('arsenal_swing_heavy',reverb(w,0.6,0.15))
# the tear: the abyss "shing" — inharmonic metal ring gliding down, with a hiss onset
for i,base in enumerate([1180,1320]):
    d=0.9; r=tone([base,base*1.47,base*2.31,base*3.14,base*4.6],d,0.28,-0.06)
    hiss=hp(noise(d),5000)*env(int(SR*d),0.004,0.05)
    sub=np.sin(2*np.pi*55*t(d))*np.exp(-t(d)/0.25)*0.5
    save(f'arsenal_tear_{i+1}',reverb(r*0.7+hiss*0.6+sub,1.0,0.3))
# light hits: crack + thump
for i,(f,c) in enumerate([(140,5200),(120,4600),(160,6000)]):
    d=0.3; tt=t(d)
    thump=np.sin(2*np.pi*np.cumsum(f*np.exp(-tt*9)+45)/SR)*np.exp(-tt/0.07)
    crack=hp(noise(d),c)*np.exp(-tt/0.018)
    body=bp(noise(d),300,1500)*np.exp(-tt/0.05)*0.5
    save(f'arsenal_hit_{i+1}',reverb(thump*1.0+crack*0.9+body,0.5,0.15))
# heavy hit: a deep boom with grit
d=1.0; tt=t(d)
boom=np.sin(2*np.pi*np.cumsum(90*np.exp(-tt*5)+38)/SR)*np.exp(-tt/0.25)
grit=lp(noise(d),1800)*np.exp(-tt/0.12)*0.8; crack=hp(noise(d),4000)*np.exp(-tt/0.02)
save('arsenal_hit_heavy',reverb(boom*1.2+grit+crack*0.7,1.2,0.25))
# echo summon: glassy rising shimmer (a construct forming)
d=0.7; tt=t(d); y=np.zeros_like(tt)
for f in [880,1320,1760,2640]:
    y+=np.sin(2*np.pi*np.cumsum(f*(0.6+0.6*tt/d))/SR)*(tt/d)**1.5*0.25
y+=hp(noise(d),3000)*(tt/d)**3*0.2
save('arsenal_summon',reverb(y,0.9,0.3))
# shatter: crystal breaking — many tiny pings + crack
d=1.1; tt=t(d); y=hp(noise(d),2500)*np.exp(-tt/0.06)*0.8
for k in range(40):
    s=int(rng.uniform(0,0.25)*SR); f=rng.uniform(2500,7500); n=int(SR*rng.uniform(0.08,0.3))
    seg=np.sin(2*np.pi*f*np.arange(n)/SR)*np.exp(-np.arange(n)/SR/0.05)*rng.uniform(0.1,0.35)
    y[s:s+n]+=seg[:len(y)-s]
y+=np.sin(2*np.pi*np.cumsum(70*np.exp(-tt*6)+40)/SR)*np.exp(-tt/0.15)*0.8
save('arsenal_shatter',reverb(y,1.0,0.3))
# liberation boom: reverse swell into a huge sub drop and crackle
d1=0.55; sw=hp(noise(d1),300)*(t(d1)/d1)**3; sw=lp(sw,6000)
d2=2.2; tt=t(d2)
drop=np.sin(2*np.pi*np.cumsum(120*np.exp(-tt*3)+32)/SR)*np.exp(-tt/0.6)
crack=lp(noise(d2),3500)*np.exp(-tt/0.25)*0.9
y=np.concatenate([sw*0.8,drop*1.3+crack])
save('arsenal_liberation',reverb(y,2.0,0.3,3000))
# dodge and perfect dodge
d=0.2; save('arsenal_dodge',reverb(sweep_noise(d,4000,1500,0.4)*env(int(SR*d),0.03,0.08),0.3,0.1))
d=1.4; tt=t(d)
chime=tone([1567,2349,3136],d,0.5)*0.6
wub=np.sin(2*np.pi*np.cumsum(200*np.exp(-tt*2)+60)/SR)*np.exp(-tt/0.5)*0.5
save('arsenal_perfect',reverb(chime+wub+sweep_noise(d,6000,400,0.4)*np.exp(-tt/0.3)*0.4,1.5,0.35))
# charge: a rising hum
d=0.9; tt=t(d); f=80*(3**(tt/d))
saw=2*((np.cumsum(f)/SR)%1)-1; y=lp(saw,900)*(tt/d)**1.2 + hp(noise(d),2000)*(tt/d)**4*0.2
save('arsenal_charge',reverb(y,0.6,0.2))
# --- domain
d1=1.6; sw=lp(hp(noise(d1),80),3000)*(t(d1)/d1)**3.5
d2=3.4; tt=t(d2)
boom=np.sin(2*np.pi*np.cumsum(100*np.exp(-tt*3)+30)/SR)*np.exp(-tt/0.8)*1.2
pad=np.zeros_like(tt)
for f in [55,82.4,110,164.8,220.5]:
    for det in (-0.6,0.6):
        pad+=(2*((np.cumsum(np.full_like(tt,f+det))/SR)%1)-1)*0.12
pad=lp(pad,700)*np.minimum(1,tt/0.6)*np.exp(-tt/2.0)
save('domain_open',reverb(np.concatenate([sw*0.9,boom+pad]),2.5,0.35,2500))
d=0.9; tt=t(d)
save('domain_spears',reverb(tone([1400,2050,3300],d,0.2,-0.08)*0.6+np.sin(2*np.pi*np.cumsum(110*np.exp(-tt*8)+50)/SR)*np.exp(-tt/0.12),1.0,0.3))
d=1.2; tt=t(d)
pulse=np.sin(2*np.pi*48*tt)*np.exp(-tt/0.18)+np.sin(2*np.pi*48*np.maximum(0,tt-0.22))*np.exp(-np.maximum(0,tt-0.22)/0.2)*(tt>0.22)*0.8
save('domain_pulse',reverb(pulse+sweep_noise(d,300,2000,0.5)*np.exp(-tt/0.35)*0.4,1.4,0.3))
d1=1.5; tt1=t(d1)
inward=sweep_noise(d1,200,5000,0.5)*(tt1/d1)**2.5 + np.sin(2*np.pi*np.cumsum(30+200*(tt1/d1)**3)/SR)*(tt1/d1)**2*0.6
d2=2.4; tt=t(d2)
bang=np.sin(2*np.pi*np.cumsum(140*np.exp(-tt*4)+28)/SR)*np.exp(-tt/0.7)*1.3+lp(noise(d2),2500)*np.exp(-tt/0.3)
save('domain_collapse',reverb(np.concatenate([inward,bang]),2.2,0.3,2800))
d=0.7; tt=t(d)
save('form_open',reverb(tone([523,784,1046],d,0.25)*np.minimum(1,tt/0.08)*0.5+sweep_noise(d,600,3000,0.5)*np.exp(-tt/0.2)*0.4,1.0,0.3))
print('done')
