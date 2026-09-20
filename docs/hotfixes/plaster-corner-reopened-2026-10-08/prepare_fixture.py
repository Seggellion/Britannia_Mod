"""Create a new disposable command fixture; never touch an existing/owner world.
This prepares model-display probes and connected command builds, NOT player placement evidence.
"""
import hashlib, itertools, json, math, shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
DOC=Path(__file__).resolve().parent
RUN=ROOT/'tmp/plaster-corner-0.1.8d/reopened-discovery-20261008'
assert RUN.resolve().is_relative_to((ROOT/'tmp/plaster-corner-0.1.8d').resolve())
assert not RUN.exists(), 'Preserve all previous fixtures; choose a new dated directory instead.'
JAR=ROOT/'tmp/combined-0.1.8d/release/britannia_mod-0.1.8d-all.jar'
assert hashlib.sha256(JAR.read_bytes()).hexdigest()=='f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd'
(RUN/'mods').mkdir(parents=True)
shutil.copy2(JAR,RUN/'mods'/JAR.name)
(RUN/'eula.txt').write_text('eula=true\n')
(RUN/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25579\nonline-mode=false\nlevel-name=world\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-players=2\nenable-rcon=false\nallow-flight=true\nmotd=Disposable plaster discovery only\n')
PACK=RUN/'world/datapacks/reopened_plaster'
F=PACK/'data/reopened_plaster/function';F.mkdir(parents=True)
(PACK/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Reopened plaster discovery only: forced display probes vs notified connected builds'}})+'\n')
FULL='britannia_mod:plaster_wall_and_support_blank';BLANK='britannia_mod:plaster_wall_blank';HALF=FULL+'_half'
FACES=['north','east','south','west']
def state(block,face,branch=False,mirror=False,half=None,shape='corner'):
    p={'facing':face,'shape':shape,'branch_right':str(branch).lower()}
    if block==FULL:p['mirrored']=str(mirror).lower()
    if half:p['half']=half
    return block+'['+','.join(k+'='+v for k,v in p.items())+']'
def put(name,commands):(F/(name+'.mcfunction')).write_text('\n'.join(commands)+'\n',encoding='utf-8')
def pair(block,x,z,face,branch=False,mirror=False,shape='straight'):
    return [f'setblock {x} 81 {z} '+state(block,face,branch,mirror,'upper',shape),f'setblock {x} 80 {z} '+state(block,face,branch,mirror,'lower',shape)]
cases=[('south',False,False,FULL),('south',True,False,FULL)]+[(f,b,m,FULL) for f,b,m in itertools.product(FACES,[False,True],[False,True]) if (f,b,m) not in [('south',False,False),('south',True,False)]]+[(f,b,False,HALF) for f,b in itertools.product(FACES,[False,True])]
setup=['gamerule doMobSpawning false','gamerule doDaylightCycle false','time set noon','weather clear','forceload add 0 0 63 95','fill -4 79 -4 59 79 91 minecraft:smooth_stone','kill @e[tag=reopened_asset_probe]']
manifest=[];support=[];capture=[];notify=[]
for i,(face,b,m,block) in enumerate(cases):
    x,z=8+(i%4)*12,8+(i//4)*12
    main=FACES.index(face);secondary=(main+(1 if b^m else -1))%4
    edges=[main,secondary];ew=next(e for e in edges if e%2==0);ns=next(e for e in edges if e%2==1)
    # Continue away from the two occupied edges. Both mirror variants use their PHYSICAL branch.
    arms=[(-1 if ns==1 else 1,0,FACES[ew]),(0,1 if ew==0 else -1,FACES[ns])]
    neighbors=[]
    for dx,dz,nface in arms:
        for dist in [1,2]:
            nx,nz=x+dx*dist,z+dz*dist
            setup+=pair(BLANK,nx,nz,nface)
            support+=pair(FULL,nx,nz,nface)
            neighbors.append({'position':[nx,80,nz],'facing':nface,'distance':dist})
    s=state(block,face,b,m,'lower' if block==FULL else None)
    if block==FULL:setup+=pair(block,x,z,face,b,m,'corner')
    else:setup+=[f'setblock {x} 80 {z} {s}']
    # Displays bypass updateShape; always identify these as forced, collision-free art probes.
    props={'facing':face,'shape':'corner','branch_right':str(b).lower()}
    if block==FULL:props.update({'mirrored':str(m).lower(),'half':'lower'})
    nbt='{Tags:["reopened_asset_probe"],block_state:{Name:"'+block+'",Properties:{'+','.join(k+':"'+v+'"' for k,v in props.items())+'}}}'
    setup+=[f'summon minecraft:block_display {x} 84 {z} '+nbt]
    angle=main*90;joint=[16 if b^m else 0,0]
    for _ in range(main):joint=[16-joint[1],joint[0]]
    cx=x+joint[0]/16+(2.2 if joint[0]==16 else -2.2);cz=z+joint[1]/16+(2.2 if joint[1]==16 else -2.2)
    yaw=math.degrees(math.atan2(cx-(x+.5),(z+.5)-cz))
    put('view_'+str(i).zfill(2),['gamemode spectator @s',f'tp @s {cx:.3f} 80 {cz:.3f} {yaw:.3f} 8',f'tellraw @s '+json.dumps({'text':f'CASE {i:02} connected command build {s}; display at Y84 is FORCED art, no collision','color':'yellow'})])
    capture += [f'execute if block {x} 80 {z} {s} run say REOPENED_CASE_{i:02}_LOWER_EXACT',f'execute unless block {x} 80 {z} {s} run say REOPENED_CASE_{i:02}_LOWER_NORMALIZED']
    if block==FULL:
        u=state(block,face,b,m,'upper');capture += [f'execute if block {x} 81 {z} {u} run say REOPENED_CASE_{i:02}_UPPER_EXACT',f'execute unless block {x} 81 {z} {u} run say REOPENED_CASE_{i:02}_UPPER_NORMALIZED']
    notify += [f'setblock {x} 79 {z} minecraft:dirt',f'setblock {x} 79 {z} minecraft:smooth_stone']
    manifest.append({'case':i,'position':[x,80,z],'expected_state':s,'properties':props,'arms':neighbors,'physical_edges':[FACES[e] for e in edges],'joint_world_xz':[x+joint[0]/16,z+joint[1]/16],'camera':[cx,80,cz,yaw,8],'setup_kind':'command connected build; NOT normal BlockItem placement'})
setup+=['function reopened_plaster:capture','say REOPENED_SETUP_FINISHED']
put('setup',setup);put('capture',capture);put('notify',notify+['function reopened_plaster:capture']);put('support_arms',support+notify+['function reopened_plaster:capture']);put('identity',['grabby env'])
# Two empty center plots: genuine BlockItem placement remains a human check.
normal=[]
for label,x,side,nface in [('A',8,-1,'east'),('B',20,1,'west')]:
    z=84
    normal+=pair(BLANK,x+side,z,'south')+pair(BLANK,x,z-1,nface)
    normal += [f'tellraw @s '+json.dumps({'text':f'Normal {label}: place support wall at {x},80,{z} facing south (look north); center begins empty.'})]
put('normal_plots',normal)
(PACK/'SCENE_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
(DOC/'evidence/FIXTURE_MANIFEST.json').write_text(json.dumps({'run':str(RUN),'candidate':str(JAR),'sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'cases':manifest,'runtime_executed':False},indent=2)+'\n')
shutil.copytree(PACK,DOC/'fixture',dirs_exist_ok=False)
print(f'Prepared 24 command builds + 24 forced model displays in NEW disposable {RUN}; not launched or visually accepted.')
