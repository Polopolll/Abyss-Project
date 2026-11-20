# Builds the Hands of the Abyss gauntlet (same steps as in Andy's Blender) and exports fist.mesh.
import bpy, bmesh, math, sys
bpy.ops.wm.read_factory_settings(use_empty=True)
def mat(name):
    m = bpy.data.materials.new(name); return m
GAL = mat("Fist_Galaxy"); DRK = mat("Fist_Dark")
parts = []
def block(name, size, loc, rot=(0,0,0), bevel=0.02, seg=3, material=GAL):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object; o.name = "Fist_" + name; o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel > 0:
        m = o.modifiers.new("bev", "BEVEL"); m.width = bevel; m.segments = seg; m.limit_method = 'NONE'
    o.data.materials.append(material); parts.append(o); return o
block("palm", (0.30, 0.25, 0.16), (0, 0.12, 0), bevel=0.045, seg=4)
block("backplate", (0.25, 0.19, 0.035), (0, 0.13, 0.085), bevel=0.012, seg=1, material=DRK)
for i in range(4):
    x = -0.112 + i * 0.075
    drop = 0.028 if i == 3 else (0.01 if i == 0 else 0)
    block(f"knuckle{i}", (0.068, 0.1, 0.15), (x, 0.29 - drop, 0.005), bevel=0.03, seg=4)
    block(f"curl{i}", (0.064, 0.14, 0.085), (x, 0.245 - drop, -0.115), rot=(0.25, 0, 0), bevel=0.025, seg=3)
    block(f"plate{i}", (0.06, 0.05, 0.03), (x, 0.315 - drop, 0.075), rot=(-0.5, 0, 0), bevel=0.008, seg=1, material=DRK)
block("thumb0", (0.075, 0.13, 0.075), (-0.15, 0.1, -0.05), rot=(0.3, 0, 0.5), bevel=0.028, seg=3)
block("thumb1", (0.17, 0.07, 0.07), (-0.05, 0.175, -0.165), rot=(0, 0, -0.3), bevel=0.028, seg=3)
bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.04, location=(0, 0.13, 0.11))
g = bpy.context.active_object; g.name = "Fist_gem"; g.scale = (1, 1.3, 0.6); g.data.materials.append(GAL)
def link(name, bm, m, smooth=False):
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    if smooth:
        for p in me.polygons: p.use_smooth = True
    o = bpy.data.objects.new(name, me); bpy.context.collection.objects.link(o); o.data.materials.append(m); return o
# crystal studs
for i in range(4):
    x = -0.112 + i * 0.075; drop = 0.028 if i == 3 else (0.01 if i == 0 else 0)
    bm = bmesh.new()
    base = [bm.verts.new((x + dx, 0.31 - drop + dy, 0.095)) for dx, dy in [(-0.022, -0.015), (0.022, -0.015), (0.022, 0.015), (-0.022, 0.015)]]
    tip = bm.verts.new((x, 0.345 - drop, 0.15 - (0.01 if i in (0, 3) else 0)))
    for k in range(4): bm.faces.new((base[k], base[(k + 1) % 4], tip))
    bm.faces.new(list(reversed(base))); bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    link(f"Fist_stud{i}", bm, GAL)
# back ridge (raised onto the bigger bracer)
bm = bmesh.new()
pts = [(-0.05, 0.03), (-0.2, 0.05), (-0.34, 0.07), (-0.43, 0.11)]
L = [bm.verts.new((-0.03, y, 0.152 + h * 0.2)) for y, h in pts]; R = [bm.verts.new((0.03, y, 0.152 + h * 0.2)) for y, h in pts]; T = [bm.verts.new((0, y, 0.152 + h)) for y, h in pts]
for i in range(3):
    bm.faces.new((L[i], L[i+1], T[i+1], T[i])); bm.faces.new((T[i], T[i+1], R[i+1], R[i]))
bm.faces.new((L[0], T[0], R[0])); bm.faces.new((R[3], T[3], L[3])); bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
link("Fist_ridge", bm, GAL)
# the heavy forearm
N = 16
prof = [(-0.0, 0.13), (-0.1, 0.14), (-0.2, 0.158), (-0.32, 0.182), (-0.44, 0.205), (-0.56, 0.214), (-0.68, 0.2), (-0.8, 0.178), (-0.86, 0.15)]
def bulge(a, y):
    k = max(0.0, min(1.0, (-y - 0.15) / 0.45))
    return max(0.0, math.cos(a - math.pi * 0.8)) ** 2 * 0.045 * k + max(0.0, math.cos(a - math.pi / 2)) ** 3 * 0.02 * k
bm = bmesh.new(); rings = []
for y, r in prof:
    rings.append([bm.verts.new((math.cos(i*2*math.pi/N) * (r + bulge(i*2*math.pi/N, y)) * 0.9, y, math.sin(i*2*math.pi/N) * (r + bulge(i*2*math.pi/N, y)) * 0.86 * 0.9)) for i in range(N)])
for A, B in zip(rings, rings[1:]):
    for i in range(N): j = (i + 1) % N; bm.faces.new((A[i], A[j], B[j], B[i]))
bm.faces.new(list(reversed(rings[0]))); bm.faces.new(rings[-1]); bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
link("Fist_forearm", bm, GAL, smooth=True)
# bracer
def ring(bm, y, r, jag=None, n=8, rot=math.pi/8, squash=0.86):
    return [bm.verts.new((math.cos(rot + i*2*math.pi/n) * r * (jag[i] if jag else 1), y, math.sin(rot + i*2*math.pi/n) * r * (jag[i] if jag else 1) * squash)) for i in range(n)]
bm = bmesh.new()
RR = [ring(bm, -0.01, 0.165), ring(bm, -0.06, 0.158), ring(bm, -0.2, 0.178), ring(bm, -0.34, 0.205), ring(bm, -0.41, 0.245, jag=[1.22, 0.95, 1.18, 0.95, 1.28, 0.95, 1.18, 0.95])]
for A, B in zip(RR, RR[1:]):
    for i in range(8): j = (i + 1) % 8; bm.faces.new((A[i], A[j], B[j], B[i]))
bm.faces.new(list(reversed(RR[0]))); bm.faces.new(RR[-1]); bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
br = link("Fist_bracer", bm, DRK); m = br.modifiers.new("bev", "BEVEL"); m.width = 0.006; m.segments = 1
# export
dg = bpy.context.evaluated_depsgraph_get()
def group(n):
    for i in range(4):
        if n in (f"Fist_knuckle{i}", f"Fist_plate{i}", f"Fist_stud{i}"): return 1 + i
        if n == f"Fist_curl{i}": return 5 + i
    return 9 if n == "Fist_thumb0" else 10 if n == "Fist_thumb1" else 0
V = []; F = []; E = []
for o in sorted(bpy.data.objects, key=lambda o: o.name):
    if not o.name.startswith("Fist_") or o.type != 'MESH': continue
    me = o.evaluated_get(dg).to_mesh(); bm = bmesh.new(); bm.from_mesh(me); bm.transform(o.matrix_world)
    bmesh.ops.triangulate(bm, faces=bm.faces)
    gm = 0 if o.data.materials[0].name == "Fist_Dark" else 1; grp = group(o.name); base = len(V)
    for v in bm.verts: V.append(tuple(round(c, 4) for c in v.co))
    for f in bm.faces: F.append((base + f.verts[0].index, base + f.verts[1].index, base + f.verts[2].index, gm, grp))
    if o.name != "Fist_forearm":
        for e in bm.edges:
            if len(e.link_faces) == 2 and e.link_faces[0].normal.angle(e.link_faces[1].normal, 0) < math.radians(50): continue
            E.append((base + e.verts[0].index, base + e.verts[1].index, grp))
    bm.free(); o.evaluated_get(dg).to_mesh_clear()
lines = ["# Hands of the Abyss: a clenched gauntlet with its forearm (right hand). +Y wrist->knuckles, +Z back of hand, the forearm runs to the elbow at y=-0.86. Built with Blender (tools/blender/fist.py).",
         "# f a b c material(1 crystal, 0 armour) group; e a b group. Groups: 0 hand+forearm, 1-4 knuckle rows, 5-8 curled finger tips, 9 thumb base, 10 thumb tip"]
lines += ["v %g %g %g" % v for v in V]; lines += ["f %d %d %d %d %d" % f for f in F]; lines += ["e %d %d %d" % e for e in E]
open(sys.argv[-1], "w").write("\n".join(lines) + "\n")
print("exported", len(V), len(F), len(E))
