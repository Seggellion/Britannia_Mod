"""Discovery only: read assets and whitelisted settings; write evidence here.
No Minecraft process, application asset, world, Git ref or profile is modified.
"""
import csv, hashlib, io, itertools, json, math, re, subprocess, zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
EV = OUT / 'evidence'
EV.mkdir(exist_ok=True)
PROFILE = Path('C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft - Britannia')
ASSETS = ROOT / 'src/main/resources'
BASE = 'assets/britannia_mod/'
FAMILY = 'plaster_wall_and_support_blank'
MODEL = BASE + 'models/block/structure/plaster/'
BS = BASE + 'blockstates/'

def sha(data): return hashlib.sha256(data).hexdigest()
def save(name, obj): (EV / name).write_text(json.dumps(obj, indent=2) + '\n', encoding='utf-8')
def git(*args): return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()
def readmodel(name): return json.loads((ASSETS / (MODEL + name + '.json')).read_text())
def posts(doc):
    return [(i,e) for i,e in enumerate(doc.get('elements', []))
            if e['to'][1]-e['from'][1] >= 15 and e.get('faces')
            and all(f.get('texture') == '#2' for f in e['faces'].values())]
def rotpt(p, angle):
    x,y,z=p
    for _ in range(angle//90): x,z=16-z,x
    return [round(x,6),y,round(z,6)]
def bounds(e, angle):
    pts=[rotpt(p,angle) for p in itertools.product(*zip(e['from'],e['to']))]
    return [[min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]]
def has_xz(b, p): return all(b[0][i]-1e-6<=p[j]<=b[1][i]+1e-6 for j,i in enumerate([0,2]))

models={n:readmodel(n) for n in [FAMILY+'_corner', FAMILY+'_corner_mirrored', FAMILY+'_corner_branch_right', FAMILY+'_corner_branch_right_mirrored', FAMILY+'_half_corner', FAMILY+'_half_corner_branch_right']}
paths=[BS+FAMILY+'.json',BS+FAMILY+'_half.json']+[MODEL+n+'.json' for n in models]
paths += [BASE+'textures/block/structure/plaster/'+n+'.png' for n in ['dirty_plaster','wood_support']]
artifacts={}
for label,path in [('candidate',ROOT/'tmp/combined-0.1.8d/release/britannia_mod-0.1.8d-all.jar'),('prior_plaster',ROOT/'tmp/plaster-corner-0.1.8d/release/britannia_mod-0.1.8d-all.jar'),('active_profile',PROFILE/'mods/britannia_mod-0.1.8d.jar')]:
    if not path.exists(): artifacts[label]={'path':str(path),'available':False}; continue
    data=path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        identities={n:z.read(n).decode('utf-8',errors='replace') for n in z.namelist() if n=='britannia_mod_build.properties'}
        entries={p:{'sha256':sha(z.read(p)), 'source_byte_equal':z.read(p)==(ASSETS/p).read_bytes(), 'size':len(z.read(p))} for p in paths if p in z.namelist()}
        classes=['block/DoubleWallBlock','block/MirrorableWallBlock','block/WallConnection','block/PlasterWallHalfBlock','item/InteriorDecoratorToolItem']
        class_hashes={n:sha(z.read('com/seggellion/britannia_mod/'+n+'.class')) for n in classes if 'com/seggellion/britannia_mod/'+n+'.class' in z.namelist()}
        artifacts[label]={'path':str(path),'size_bytes':len(data),'sha256':sha(data),'embedded_identity':identities,'mtime':path.stat().st_mtime,'entries':entries,'class_hashes':class_hashes}
        save(label+'_models.json',{p:json.loads(z.read(p)) for p in paths if p.endswith('.json') and p in z.namelist()})
        if label=='active_profile': active_models={n:json.loads(z.read(MODEL+n+'.json')) for n in models}
save('ARTIFACTS.json',artifacts)

matrix=[]
for kind,blockstate in [('full',FAMILY),('half_wall',FAMILY+'_half')]:
    variants=json.loads((ASSETS/(BS+blockstate+'.json')).read_text())['variants']
    for key,selector in variants.items():
        props=dict(s.split('=') for s in key.split(','))
        if props['shape']!='corner': continue
        model=selector['model']; angle=selector.get('y',0)
        right=(props['branch_right']=='true') ^ (props.get('mirrored','false')=='true')
        joint=rotpt([16 if right else 0,0,0],angle)
        ps=[]
        if model!='minecraft:block/air':
            doc=models[model.rsplit('/',1)[-1]]
            for i,e in posts(doc):
                b=bounds(e,angle)
                ps.append({'element':i,'bounds':b,'covers_junction_xz':has_xz(b,[joint[0],joint[2]]),'faces':e['faces']})
        row={'kind':kind,'state':props,'selector':selector,'joint_xz':[joint[0],joint[2]],'posts':ps,'lower_owned_render':kind=='full','status':'upper_air_lower_owns_both_sections' if props.get('half')=='upper' else 'junction_post_present' if any(p['covers_junction_xz'] for p in ps) else 'NO_JUNCTION_POST'}
        matrix.append(row)
save('MATRIX.json',matrix)
with (EV/'MATRIX.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.writer(f);w.writerow(['kind','facing','branch_right','mirrored','half','model','y_rotation','joint_xz','post_elements','junction_post','status'])
    for r in matrix:
        p=r['state'];w.writerow([r['kind'],p['facing'],p['branch_right'],p.get('mirrored','N/A'),p.get('half','N/A'),r['selector']['model'],r['selector'].get('y',0),r['joint_xz'],[q['element'] for q in r['posts']],[q['element'] for q in r['posts'] if q['covers_junction_xz']],r['status']])

geometry={}
for name,doc in models.items():
    structural=[]
    for i,e in enumerate(doc['elements']):
        structural.append({'element':i,'from':e['from'],'to':e['to'],'rotation':e.get('rotation'), 'faces':e['faces'],'full_height_timber':i in [q[0] for q in posts(doc)]})
    overlaps=[]
    for pi,p in posts(doc):
        for i,e in enumerate(doc['elements']):
            if i==pi or all(f['texture']=='#2' for f in e['faces'].values()):continue
            span=[min(p['to'][k],e['to'][k])-max(p['from'][k],e['from'][k]) for k in range(3)]
            if all(s>1e-6 for s in span):overlaps.append({'post':pi,'plaster':i,'span':span})
    geometry[name]={'parent':doc.get('parent'),'textures':doc.get('textures'),'elements':structural,'post_plaster_positive_volume_overlaps':overlaps,'nonzero_element_rotations':[i for i,e in enumerate(doc['elements']) if e.get('rotation',{}).get('angle',0)!=0]}
save('GEOMETRY.json',geometry)
observations=[]
for label,x,br in [('A',5266,False),('B',5269,True)]:
    name=FAMILY+'_corner'+('_branch_right' if br else '')
    streams={}
    for tag,doc in [('current_candidate',models[name]),('installed_profile',active_models[name])]:
        joint=[0 if br else 16,16]
        p=[]
        for i,e in posts(doc):
            b=bounds(e,180)
            p.append({'element':i,'south_model_bounds':b,'world_bounds':[[round(x+c[0]/16,6),72+c[1]/16,round(4166+c[2]/16,6)] for c in b],'covers_junction':has_xz(b,joint)})
        streams[tag]={'model':name,'junction_south_model_xz':joint,'world_junction_xz':[x+joint[0]/16,4166+joint[1]/16],'posts':p}
    observations.append({'observation':label,'position':[x,72,4166],'state':{'facing':'south','shape':'corner','half':'lower','branch_right':str(br).lower(),'mirrored':'false'},'acceptable_label':'unresolved_owner_did_not_label','resource_geometry':streams})
save('OBSERVATIONS.json',observations)

# Effective resource contenders for selected family, parents and texture references.
# Empty options selections do not alone rule out server packs; inventory cached zips too.
pack_paths=paths+["assets/minecraft/models/block/block.json"]
contenders=[]
for folder in ['mods','resourcepacks','server-resource-packs','downloads']:
    base=PROFILE/folder
    if not base.exists():continue
    for path in base.rglob('*'):
        if not path.is_file():continue
        if zipfile.is_zipfile(path):
            try:
                with zipfile.ZipFile(path) as z:
                    owned=[p for p in pack_paths if p in z.namelist()]
                    if owned:contenders.append({'path':str(path),'folder':folder,'load_eligible':folder!='mods' or path.suffix=='.jar','entries':{p:sha(z.read(p)) for p in owned}})
            except (zipfile.BadZipFile,OSError):pass
        elif path.name==FAMILY+'.json' or path.name in ['dirty_plaster.png','wood_support.png','block.json']:
            contenders.append({'path':str(path),'folder':folder,'sha256':sha(path.read_bytes())})
options=(PROFILE/'options.txt').read_text(errors='replace')
selected=[s for s in options.splitlines() if s.startswith(('resourcePacks:','incompatibleResourcePacks:'))]
iris=PROFILE/'config/iris.properties'
shader=[s for s in iris.read_text(errors='replace').splitlines() if s.startswith(('enableShaders=','shaderPack='))] if iris.exists() else []
log=PROFILE/'logs/latest.log'
safe=[]
if log.exists():
    for no,line in enumerate(log.read_text(errors='replace').splitlines(),1):
        # Never copy launch arguments, account/chat records or full errors.
        if any(s in line for s in ['ModLauncher running: args','with arguments','CHAT','accessToken']):continue
        if (('Found mod file "britannia_mod-' in line) or ('Reloading ResourceManager:' in line) or ('Shaders are ' in line) or ('shaderpack' in line.lower() and 'iris' in line.lower()) or ('build identity' in line.lower()) or ('git.head=' in line) or ('Connecting to ' in line and 'ConnectScreen' in line)):
            safe.append({'line':no,'text':line})
save('RESOURCE_STACK.json',{'profile':str(PROFILE),'selected_options':selected,'shader_options':shader,'resource_contenders':contenders,'whitelisted_log_lines':safe,'log_mtime':log.stat().st_mtime if log.exists() else None,'effective_inference':'Only installed mod supplies selected Britannia files among available local archives. Server-selected/in-memory stack not independently captured.'})
save('SOURCE.json',{'captured_utc':datetime.now(timezone.utc).isoformat(),'root':str(ROOT),'branch':git('branch','--show-current'),'head':git('rev-parse','HEAD'),'status_at_analysis':git('status','--short'),'relevant_diff_986ed746':git('diff','986ed746','HEAD','--','src/main','src/test','tools','build.gradle','gradle.properties','.gitattributes'),'worktrees':git('worktree','list','--porcelain')})
save('CURRENT_TREE_PARITY.json',{'head':git('rev-parse','HEAD'),'entry_head':'71de2312a552ebf4dad3ecf147efd1de4bd815db','production_diff_excluding_gametest':git('diff','--name-only','986ed746','HEAD','--','src/main','src/test','tools','build.gradle','gradle.properties','.gitattributes',':(exclude)src/main/java/com/seggellion/britannia_mod/gametest/**'),'concurrent_test_harness_delta':git('diff','--name-only','71de2312','HEAD','--','src/main/java/com/seggellion/britannia_mod/gametest'),'packaging_excludes':'build.gradle lines 286 and 297 exclude com/seggellion/britannia_mod/gametest/**'})

try:
    from PIL import Image, ImageDraw
    im=Image.new('RGB',(1000,750),'white');d=ImageDraw.Draw(im)
    for r,tag in enumerate(['installed_profile','current_candidate']):
        for c,obs in enumerate(observations):
            x0=80+c*490;y0=100+r*335;scale=12
            name=FAMILY+'_corner'+('_branch_right' if c else '')
            doc=active_models[name] if r==0 else models[name]
            for i,e in enumerate(doc['elements']):
                if e['to'][1]-e['from'][1]<15:continue
                b=bounds(e,180);timber=i in [q[0] for q in posts(doc)]
                rect=[x0+b[0][0]*scale,y0+b[0][2]*scale,x0+b[1][0]*scale,y0+b[1][2]*scale]
                d.rectangle(rect,fill='#855222' if timber else '#ddd9ce',outline='#302a23')
                d.text((rect[0]+2,rect[1]+2),str(i),fill='black')
            joint=obs['resource_geometry'][tag]['junction_south_model_xz'];jx=x0+joint[0]*scale;jz=y0+joint[1]*scale
            d.ellipse([jx-6,jz-6,jx+6,jz+6],outline='red',width=3)
            d.rectangle([x0,y0,x0+16*scale,y0+16*scale],outline='#0088bb',width=2)
            d.text((x0-10,y0-50),f'{tag}: {obs["observation"]} branch_right={str(bool(c)).lower()}',fill='black')
            d.text((x0-10,y0+215),'Red circle = architectural junction; numbers = elements',fill='black')
    d.text((20,15),'SOUTH plan, full-height solids only. Brown timber, gray plaster; +X right, +Z down.',fill='black')
    d.text((20,35),'Analytical asset projection, NOT Minecraft rendered or owner visual acceptance.',fill='black')
    # Draw posts last to show their plan extents despite intersecting plaster.
    for r,tag in enumerate(['installed_profile','current_candidate']):
        for c,obs in enumerate(observations):
            x0=80+c*490;y0=100+r*335;scale=12
            name=FAMILY+'_corner'+('_branch_right' if c else '')
            doc=active_models[name] if r==0 else models[name]
            for i,e in posts(doc):
                b=bounds(e,180);rect=[x0+b[0][0]*scale,y0+b[0][2]*scale,x0+b[1][0]*scale,y0+b[1][2]*scale]
                d.rectangle(rect,fill='#855222',outline='#302a23');d.text((rect[0]+2,rect[1]+2),str(i),fill='black')
            joint=obs['resource_geometry'][tag]['junction_south_model_xz'];jx=x0+joint[0]*scale;jz=y0+joint[1]*scale
            d.ellipse([jx-6,jz-6,jx+6,jz+6],outline='red',width=3)
    im.save(EV/'PAIRED_GEOMETRY.png')
except ImportError: pass

print(json.dumps({'artifact_identities':{k:{a:v[a] for a in ['path','size_bytes','sha256','embedded_identity']} for k,v in artifacts.items() if v.get('size_bytes')},'corner_rows':len(matrix),'statuses':{s:sum(r['status']==s for r in matrix) for s in set(r['status'] for r in matrix)},'selected_options':selected,'shader_options':shader,'resource_contenders':len(contenders),'log_lines':safe},indent=2))
