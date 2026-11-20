"""Offline preview of the void shader's sky function (assets/abyss/shaders/core/void.fsh).

Renders a 360 panorama plus two camera views, exactly as the game would show them (no tone mapping), and a
brightened copy underneath so the faint detail is visible. Needs moderngl + an X display (Xvfb :98 works).
usage: DISPLAY=:98 python3 tools/preview_void.py <void.fsh> <out.png> [sky=0|1] [time]
"""
import sys, re, moderngl, numpy as np
from PIL import Image

fsh, out = sys.argv[1], sys.argv[2]
sky = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
tm = float(sys.argv[4]) if len(sys.argv) > 4 else 100.0
src = open(fsh).read()
body = src[src.index('float hash3'):src.index('void main')]
call = 'abyssSky(d, T, %.1f)' % sky if 'abyssSky' in body else 'galaxy(d, T)'
ctx = moderngl.create_standalone_context()
prog = ctx.program(vertex_shader='#version 330\nin vec2 p; out vec2 uv; void main(){uv=p*0.5+0.5; gl_Position=vec4(p,0,1);}',
    fragment_shader='#version 330\nin vec2 uv; out vec4 o; uniform vec3 fwd; uniform int mode; uniform float T; uniform float gain;\n' + body + '''
void main(){ vec3 d;
 if(mode==0){ float lon=(uv.x-0.5)*6.2831853, lat=(uv.y-0.5)*3.14159265; d=vec3(cos(lat)*sin(lon), sin(lat), -cos(lat)*cos(lon)); }
 else { vec3 f=normalize(fwd), r=normalize(cross(f,vec3(0,1,0))), u=cross(r,f); vec2 s=(uv-0.5)*vec2(1.777,1.0)*1.2; d=normalize(f+r*s.x+u*s.y); }
 vec3 c=''' + call + '''; o=vec4(clamp(c*gain,0.0,1.0),1);} ''')
vbo = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], 'f4'))
vao = ctx.simple_vertex_array(prog, vbo, 'p')
def render(w, h, mode, gain, fwd=(0, 0, -1)):
    fbo = ctx.simple_framebuffer((w, h)); fbo.use(); prog['mode'].value = mode; prog['gain'].value = gain
    if 'T' in prog: prog['T'].value = tm
    if mode: prog['fwd'].value = fwd
    vao.render(moderngl.TRIANGLE_STRIP)
    return Image.frombytes('RGB', (w, h), fbo.read(components=3)).transpose(Image.FLIP_TOP_BOTTOM)
s = Image.new('RGB', (1600, 2 * (800 + 450)))
for k, gain in enumerate((1.0, 4.0)):
    y = k * 1250
    s.paste(render(1600, 800, 0, gain), (0, y))
    s.paste(render(960, 540, 1, gain, (0.45, 0.32, -0.83)).resize((800, 450)), (0, y + 800))
    s.paste(render(960, 540, 1, gain, (-0.6, 0.05, 0.5)).resize((800, 450)), (800, y + 800))
s.save(out); print('saved', out)
