"""Analytical first-face rays through authored quads. Not a Minecraft render oracle.
Accounts for omitted/back-facing quads; ignores textures/shaders/neighbors and camera clipping.
"""
import json,math
from pathlib import Path
DOC=Path(__file__).resolve().parent;EV=DOC/'evidence'
g=json.loads((EV/'GEOMETRY.json').read_text())
side={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}
def first(doc,cam,target):
    direction=[b-a for a,b in zip(cam,target)];hits=[]
    for e in doc['elements']:
        for name,face in e['faces'].items():
            axis,sign=side[name]
            if direction[axis]*sign>=0:continue # quad faces away from camera
            plane=e['to'][axis] if sign>0 else e['from'][axis]
            t=(plane-cam[axis])/direction[axis]
            if t<=0:continue
            p=[a+t*d for a,d in zip(cam,direction)]
            if all(e['from'][j]-1e-7<=p[j]<=e['to'][j]+1e-7 for j in range(3) if j!=axis):
                hits.append((t,{'element':e['element'],'face':name,'texture':face['texture'],'point':[round(v,5) for v in p]}))
    return min(hits,key=lambda r:r[0])[1] if hits else None
rows=[]
for name,doc in g.items():
    right=name.endswith('_corner_branch_right') or name.endswith('_corner_mirrored')
    # Mirrored true is the opposite of true; mirrored false is right.
    if name.endswith('_corner_branch_right_mirrored'):right=False
    joint=[16 if right else 0,0];maxy=16 if '_half_' in name else 32
    joint_posts=[e for e in doc['elements'] if e['full_height_timber'] and e['from'][0]<=joint[0]<=e['to'][0] and e['from'][2]<=joint[1]<=e['to'][2]]
    for y in [8]+([24] if maxy==32 else []):
        cam={'exterior':[48 if right else -32,y,-32],'interior':[-32 if right else 48,y,48]}
        for label,c in cam.items():
            for p in joint_posts:
                target=[(p['from'][0]+p['to'][0])/2,y,(p['from'][2]+p['to'][2])/2]
                h=first(doc,c,target)
                rows.append({'model':name,'view':label,'height':y,'camera_authored':c,'target':target,'junction_post':p['element'],'first_quad':h,'hits_expected_post':h is not None and h['element']==p['element']})
(EV/'FACE_RAYS.json').write_text(json.dumps({'method':'First intersection of outward-facing authored quads along a ray to junction-post center. Analytical directional sample, NOT rendered acceptance or complete visibility proof. Neighbor geometry omitted.','results':rows},indent=2)+'\n')
print(json.dumps([{'model':r['model'],'view':r['view'],'height':r['height'],'hits_post':r['hits_expected_post'],'first_quad':r['first_quad']} for r in rows if r['height']==8],indent=2))
