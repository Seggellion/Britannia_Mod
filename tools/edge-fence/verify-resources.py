"""Independent vertex/UV and mapping oracle for the edge-fence resource migration.

Run with Python 3 from any directory. Uses Minecraft FaceInfo vertex ordering,
element rotation (including rescale), then blockstate rotation about (8,8,8).
This is geometric verification, not a substitute for observing baked client quads.
"""
import hashlib
import itertools
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/britannia_mod'
ORACLE=ROOT/'docs/projects/patch18-edge-fence-hotfix/oracle'
DIRS=('north','east','south','west')
FACE_VERTICES={
 'down':((0,0,1),(0,0,0),(1,0,0),(1,0,1)),
 'up':((0,1,0),(0,1,1),(1,1,1),(1,1,0)),
 'north':((1,1,0),(1,0,0),(0,0,0),(0,1,0)),
 'south':((0,1,1),(0,0,1),(1,0,1),(1,1,1)),
 'west':((0,1,0),(0,0,0),(0,0,1),(0,1,1)),
 'east':((1,1,1),(1,0,1),(1,0,0),(1,1,0))}

def rotate(point,axis,angle,origin):
    p=[point[i]-origin[i] for i in range(3)]
    a,b={'x':(1,2),'y':(2,0),'z':(0,1)}[axis]
    c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
    p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
    return tuple(p[i]+origin[i] for i in range(3))

def vertices(entry):
    model=json.loads((ASSETS/'models'/ (entry['model'].split(':')[1]+'.json')).read_text())
    result=[]
    for index,element in enumerate(model['elements']):
        lo,hi=element['from'],element['to']
        for direction,face in element['faces'].items():
            u0,v0,u1,v1=face['uv']; uv=((u0,v0),(u0,v1),(u1,v1),(u1,v0))
            for i,corner in enumerate(FACE_VERTICES[direction]):
                point=tuple((lo,hi)[corner[j]][j] for j in range(3))
                r=element.get('rotation')
                if r:
                    point=rotate(point,r['axis'],r['angle'],r['origin'])
                    if r.get('rescale'):
                        factor=1/math.cos(math.radians(abs(r['angle'])))
                        axis='xyz'.index(r['axis'])
                        point=tuple(r['origin'][j]+(point[j]-r['origin'][j])*(1 if j==axis else factor) for j in range(3))
                point=rotate(point,'y',-entry.get('y',0),(8,8,8))
                texture=uv[(i+face.get('rotation',0)//90)%4]
                result.append((index,*point,*texture))
    return result

def canonical(points): return sorted(tuple(round(x,5) if isinstance(x,float) else x for x in p) for p in points)
def transform(points,turn=0,mirror=None):
    result=[]
    for index,x,y,z,u,v in points:
        if mirror=='z': z=16-z
        if mirror=='x': x=16-x
        x,y,z=rotate((x,y,z),'y',-turn*90,(8,8,8))
        result.append((index,x,y,z,u,v))
    return result
def state_transform(facing,code,turn=0,mirror=None):
    def d(i):
        if mirror=='z': i={0:2,2:0,1:1,3:3}[i]
        if mirror=='x': i={0:0,2:2,1:3,3:1}[i]
        return (i+turn)%4
    mask=sum(1<<d(i) for i in range(4) if code & 1<<i)
    parity=(code&16)^(16 if mirror else 0)
    return DIRS[d(DIRS.index(facing))],mask|parity

def main():
    variants=json.loads((ASSETS/'blockstates/wooden_fence.json').read_text())['variants']
    old=json.loads((ORACLE/'legacy-blockstate.json').read_text())['variants']
    assert len(variants)==192
    for line in (ORACLE/'AUTHORED_MODEL_SHA256.txt').read_text().splitlines():
        expected,name=line.split()
        assert hashlib.sha256((ASSETS/'models/block/structure/wooden_fence'/name).read_text().encode()).hexdigest()==expected,name
    for key,entry in old.items():
        facing,bits=key.split(',',1)
        assert variants[facing+',layout_code=32,'+bits]==entry,key
        mask=sum(1<<i for i,d in enumerate(DIRS) if d+'=true' in bits)
        assert variants[facing+',layout_code='+str(mask)]==entry,key
    checked=0
    for facing in DIRS:
        for code in range(32):
            source=vertices(variants[f'facing={facing},layout_code={code}'])
            for turn,mirror in itertools.product(range(4),(None,'x','z')):
                f,c=state_transform(facing,code,turn,mirror)
                actual=vertices(variants[f'facing={f},layout_code={c}'])
                assert canonical(actual)==canonical(transform(source,turn,mirror)),(facing,code,turn,mirror)
                checked+=1
    for facing,code,mask in itertools.product(DIRS,range(33),range(16)):
        state=dict(facing=facing,layout_code=str(code),**{d:str(bool(mask&1<<i)).lower() for i,d in enumerate(DIRS)})
        matches=[entry for key,entry in variants.items() if all(state[k]==v for k,v in (p.split('=') for p in key.split(',')))]
        assert len(matches)==1,(facing,code,mask)
    print(f'PASS: 64 legacy mappings; 2112 unique state matches; {checked} vertex+UV transforms; 7 authored hashes')

if __name__=='__main__': main()
