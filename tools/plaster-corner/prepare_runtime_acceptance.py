"""Disposable datapack probes; no candidate code or live-world changes."""
import gzip
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from dump_scene import ROOT, nbt_io
from prepare_scene import state, FULL, BLANK, HALF


def main():
    world = ROOT / 'tmp/plaster-corner-0.1.8d/client-resume/saves/Plaster Corner Baseline'
    assert (world/'level.dat').exists()
    pack = world/'datapacks/plaster_probe'
    funcs = pack/'data/plaster_probe/function'
    def put(name, lines):
        if name.startswith(('mirror_','rotate_','place_','walk_')):
            lines=['time set noon']+lines
        (funcs/(name+'.mcfunction')).write_text('\n'.join(lines)+'\n')
    probes = [('lower_e',80.2,-.12,49.5),('upper_e',81.2,-.12,49.5),
              ('lower_w',80.2,.12,47.5),('upper_w',81.2,.12,47.5),
              ('control_e',81.2,-.12,51.5),('control_w',81.2,.12,49.5)]
    lines = ['time set noon','gamemode spectator @s','tp @s 48.5 83 86 180 35',
             'kill @e[tag=plaster_physics]']
    report = ['say PLASTER_PHYSICS_POSITIONS_BEGIN']
    for number,(tag,y,motion,x) in enumerate(probes):
        z = 84.35 if not tag.startswith('control') else 86.1
        lines.append(f'summon minecraft:item {x} {y} {z} '+json.dumps({
            'Tags':['plaster_physics','p_'+tag], 'NoGravity':True,
            'PickupDelay':32767, 'Age':-32768,
            'Item':{'id':'minecraft:'+['stone','dirt','glass','sand','gravel','cobblestone'][number],'count':1},'Motion':[motion,0.,0.]}))
        report += [f'say PROBE_{tag}',f'data modify storage plaster_probe:acceptance physics.{tag} set from entity @e[tag=p_{tag},limit=1] Pos']
    lines.append('schedule function plaster_probe:physics_report 20t replace')
    put('physics', lines)
    put('physics_report', report+['say PLASTER_PHYSICS_POSITIONS_END'])
    # Identical south arm on each half is a physical control for the divergent branch.
    main=[]
    for half,y,item in [('lower',80.2,'oak_planks'),('upper',81.2,'birch_planks')]:
        tag='main_s_'+half
        main.append(f'summon minecraft:item 48.5 {y} 85.5 '+json.dumps({'Tags':['plaster_physics','p_'+tag],
            'NoGravity':True,'PickupDelay':32767,'Item':{'id':'minecraft:'+item,'count':1},'Motion':[0.,0.,-.12]}))
        report.append(f'data modify storage plaster_probe:acceptance physics.{tag} set from entity @e[tag=p_{tag},limit=1] Pos')
    put('physics',lines[:-1]+main+lines[-1:])
    put('physics_report',report+['say PLASTER_PHYSICS_POSITIONS_END'])
    # Pair-state assertions are logged after each actual UI click.
    pair = []
    for half,y in [('lower',80),('upper',81)]:
        for shape in ['straight','corner','t_junction']:
            for facing in ['north','east','south','west']:
                for branch in (False,True):
                    for mirrored in (False,True):
                        s=state(FULL,facing,branch,mirrored,half,shape)
                        pair.append(f'execute if block 48 {y} 84 {s} run say PAIR_{half}_{facing}_{shape}_branch_{str(branch).lower()}_mirror_{str(mirrored).lower()}')
    put('pair', pair)
    reset=['time set noon','gamemode creative @s','clear @s','fill 48 80 84 48 81 84 minecraft:air']
    for xx,zz,f in [(47,84,'south'),(46,84,'south'),(48,83,'east'),(48,82,'east')]:
        for half,y in [('upper',81),('lower',80)]:reset.append(f'setblock {xx} {y} {zz} '+state(BLANK,f,half=half,shape='straight'))
    put('minimal_reset',reset+[f'item replace entity @s hotbar.0 with {FULL} 8','tp @s 48.5 80 86.2 180 43.6','say MINIMAL_REPRO_ACTUAL_BLOCKITEM_CLICK_REQUIRED'])
    put('normalize_pair',['function plaster_probe:pair','setblock 48 79 84 minecraft:dirt','setblock 48 79 84 minecraft:smooth_stone','say AFTER_NEIGHBOR_UPDATE','function plaster_probe:pair'])
    for half,y,pitch in [('lower',80,43.6),('upper',81,18)]:
        for hand in ['mirror','rotate']:
            lines=['gamemode creative @s','clear @s',f'tp @s 48.5 80 86.2 180 {pitch}',
                   'item replace entity @s weapon.'+('offhand' if hand=='mirror' else 'mainhand')+' with britannia_mod:interior_decorator_tool',
                   f'say BEFORE_{hand}_{half}','function plaster_probe:pair',
                   f'schedule function plaster_probe:after_{hand}_{half} 10s replace',
                   f'say CLICK_{hand}_{half}_THEN_RUN_PAIR']
            put(f'{hand}_{half}',lines)
            put(f'after_{hand}_{half}',[f'say AFTER_{hand}_{half}','execute as @a run function plaster_probe:pair'])
    put('normalize_east', ['setblock 12 79 12 minecraft:dirt','setblock 12 79 12 minecraft:smooth_stone','function plaster_probe:view_05'])
    # NBT structure uses both halves, exercising vanilla state transform dispatch.
    structures=pack/'data/plaster_probe/structure'
    structures.mkdir(parents=True,exist_ok=True)
    palette=[]
    for half in ['lower','upper']:
        props={'facing':'south','shape':'corner','branch_right':'false','mirrored':'false','half':half}
        palette.append({'Name':(8,FULL),'Properties':(10,{k:(8,v) for k,v in props.items()})})
    blocks=[{'pos':(9,(3,[3,y,3])),'state':(3,y)} for y in (0,1)]
    for facing in ['south','east']:
        for half in ['lower','upper']:
            props={'facing':facing,'shape':'straight','branch_right':'false','half':half}
            index=len(palette)
            palette.append({'Name':(8,BLANK),'Properties':(10,{k:(8,v) for k,v in props.items()})})
            for d in range(3):
                pos=[d,0 if half=='lower' else 1,3] if facing=='south' else [3,0 if half=='lower' else 1,d]
                blocks.append({'pos':(9,(3,pos)),'state':(3,index)})
    root={'DataVersion':(3,3955),'size':(9,(3,[4,2,4])), 'palette':(9,(10,palette)),
          'blocks':(9,(10,blocks)), 'entities':(9,(10,[]))}
    raw=nbt_io.serialise('',root)
    assert nbt_io.serialise(*nbt_io.parse(raw))==raw
    (structures/'pair.nbt').write_bytes(gzip.compress(raw))
    lines=['fill -8 79 96 56 79 140 minecraft:smooth_stone']
    manifest=[]
    for i,(rotation,mirror) in enumerate([(r,m) for m in ['none','left_right','front_back'] for r in ['none','clockwise_90','180','counterclockwise_90']]):
        x,z=(i%4)*12,100+(i//4)*12
        lines += [f'place template plaster_probe:pair {x} 80 {z} {rotation} {mirror}',f'say STRUCTURE_{i:02}_{rotation}_{mirror}']
        dx,dz=3,3
        if mirror=='left_right':dz=-dz
        if mirror=='front_back':dx=-dx
        if rotation=='clockwise_90':dx,dz=-dz,dx
        elif rotation=='180':dx,dz=-dx,-dz
        elif rotation=='counterclockwise_90':dx,dz=dz,-dx
        manifest.append({'case':i,'rotation':rotation,'mirror':mirror,'origin':[x,80,z],'position':[x+dx,80,z+dz]})
    put('structures',lines)
    (pack/'STRUCTURES.json').write_text(json.dumps(manifest,indent=2)+'\n')
    def describe(x,z,label,block=FULL):
        result=[f'say STATE_BEGIN_{label}']
        for half,y in [('lower',80),('upper',81)] if block==FULL else [(None,80)]:
            for shape in ['straight','corner','t_junction']:
                for f in ['north','east','south','west']:
                    for b in (False,True):
                        for m in (False,True) if block==FULL else (False,):
                            s=state(block,f,b,m,half,shape)
                            result.append(f'execute if block {x} {y} {z} {s} run say STATE_{label}_{half or "half"}_{f}_{shape}_branch_{str(b).lower()}_mirror_{str(m).lower()}')
        return result
    lines=['fill 56 79 -8 124 79 40 minecraft:smooth_stone']
    neigh=[]
    configs=[('support',FULL,FULL),('door',FULL,'minecraft:oak_door'),
             ('tagged_gate',FULL,'britannia_mod:wooden_gate'),('solid',FULL,'minecraft:stone'),
             ('post',FULL,'britannia_mod:wooden_post'),('full_with_half',FULL,HALF),
             ('half_with_full',HALF,BLANK),('half_with_half',HALF,HALF),('mutations',FULL,BLANK)]
    for i,(label,center,peer) in enumerate(configs):
        x,z=60+(i%5)*12,12+(i//5)*16
        lines += [f'fill {x-3} 80 {z-3} {x+3} 81 {z+3} minecraft:air']
        for xx,zz,f in [(x-1,z,'south'),(x,z-1,'east')]:
            if peer in (FULL,BLANK):
                for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {xx} {y} {zz} '+state(peer,f,half=half,shape='straight'))
            elif peer==HALF:lines.append(f'setblock {xx} 80 {zz} '+state(HALF,f,shape='straight'))
            elif peer in ('minecraft:oak_door','britannia_mod:wooden_gate'):
                for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {xx} {y} {zz} {peer}[facing={f},half={half}]')
            else:lines.append(f'setblock {xx} 80 {zz} {peer}')
        if center==FULL:lines.append(f'setblock {x} 81 {z} '+state(FULL,'south',half='upper'))
        lines += [f'setblock {x} 80 {z} '+state(center,'south',half='lower' if center==FULL else None)]*2
        lines += describe(x,z,label,center)
        if label=='mutations':
            lines += [f'setblock {x} 80 {z-1} minecraft:air',f'setblock {x} 81 {z-1} minecraft:air']+describe(x,z,'removed_arm')
            for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {x} {y} {z-1} '+state(BLANK,'east',half=half,shape='straight'))
            lines += describe(x,z,'added_arm')
        neigh.append({'label':label,'position':[x,80,z],'center':center,'peer':peer})
    put('neighbors',lines)
    (pack/'NEIGHBORS.json').write_text(json.dumps(neigh,indent=2)+'\n')
    # Logged transform outcomes include post-placement normalization.
    put('structure_states',sum([describe(*[c['position'][j] for j in (0,2)],f'structure_{c["case"]:02}') for c in manifest],[]))
    put('all_probes',['function plaster_probe:pair','function plaster_probe:physics','function plaster_probe:neighbors','function plaster_probe:structures','function plaster_probe:structure_states'])
    # Actual keyboard movement is recorded each server tick; no teleport is a movement pass.
    put('tick',['execute as @a[tag=plaster_walk] run data modify storage plaster_probe:acceptance player_positions append from entity @s Pos',
                'execute as @a[tag=plaster_walk] run data modify storage plaster_probe:acceptance player_ground append from entity @s OnGround'])
    tags=pack/'data/minecraft/tags/function'
    tags.mkdir(parents=True,exist_ok=True)
    (tags/'tick.json').write_text(json.dumps({'values':['plaster_probe:tick']})+'\n')
    put('walk_done',['tag @a remove plaster_walk','say PLASTER_KEYBOARD_RECORDING_DONE'])
    for label,x,y,z,yaw in [('phantom_east',49.4,80,84.2,90),('visible_west_jump',47.6,80,84.2,270),
                           ('normal_south',60.2,80,13.4,180),('normal_east',61.4,80,12.2,90)]:
        put('walk_'+label,['gamemode survival @s','clear @s','effect give @s minecraft:speed 30 3 true',
            f'tp @s {x} {y} {z} {yaw} 0',f'data modify storage plaster_probe:acceptance player_case set value "{label}"',
            'data modify storage plaster_probe:acceptance player_positions set value []',
            'data modify storage plaster_probe:acceptance player_ground set value []',
            'tag @s add plaster_walk',f'schedule function plaster_probe:walk_done_{label} 15s replace',
            f'say KEYBOARD_PROBE_{label}_START'])
        put('walk_done_'+label,['tag @a remove plaster_walk',
            f'data modify storage plaster_probe:acceptance walks.{label}.positions set from storage plaster_probe:acceptance player_positions',
            f'data modify storage plaster_probe:acceptance walks.{label}.ground set from storage plaster_probe:acceptance player_ground',
            f'say KEYBOARD_PROBE_{label}_DONE'])
    # Actual BlockItem corner-first/reversed-order trials use the same floor aim.
    for label,x,z in [('corner_first',60,36),('reverse_arms',72,36)]:
        lines=[f'fill {x-3} 80 {z-3} {x+3} 81 {z+3} minecraft:air','gamemode creative @s','clear @s',
               f'item replace entity @s hotbar.0 with {FULL} 8',f'tp @s {x+.5} 80 {z+2.2} 180 43.6',f'say ACTUAL_PLACE_{label}_CLICK_FLOOR']
        if label=='reverse_arms':
            arms=[]
            for xx,zz,f in [(x,z-1,'east'),(x-1,z,'south')]:
                for half,y in [('upper',81),('lower',80)]:arms.append(f'setblock {xx} {y} {zz} '+state(BLANK,f,half=half,shape='straight'))
            lines[1:1]=arms
        put('place_'+label,lines)
        lines=[]
        if label=='corner_first':
            for xx,zz,f in [(x,z-1,'east'),(x-1,z,'south')]:
                for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {xx} {y} {zz} '+state(BLANK,f,half=half,shape='straight'))
        put('check_'+label,lines+describe(x,z,label))
    print('Prepared physics, actual half interaction, normalization, and 12 vanilla template transforms')


if __name__=='__main__':
    main()
