"""Camera tour of ordinary placed neighborhoods/transforms; no block writes."""
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]


def main():
    pack=ROOT/'tmp/plaster-corner-0.1.8d/client-resume/saves/Plaster Corner Baseline/datapacks/plaster_probe'
    assert (pack.parent.parent/'level.dat').exists()
    funcs=pack/'data/plaster_probe/function'
    cases=[{'label':'owner_cap','position':[0,80,0],'vertex':[1,1]},
           {'label':'north_half_false','position':[0,80,48],'vertex':[0,0]},
           {'label':'north_half_true','position':[12,80,48],'vertex':[1,0]}]
    for c in json.loads((pack/'NEIGHBORS.json').read_text()):
        cases.append({**c,'label':'neighbor_'+c['label'],'vertex':[1,1]})
    for c in json.loads((pack/'STRUCTURES.json').read_text()):
        vx,vz=1,1
        if c['mirror']=='left_right':vz=1-vz
        if c['mirror']=='front_back':vx=1-vx
        if c['rotation']=='clockwise_90':vx,vz=1-vz,vx
        elif c['rotation']=='180':vx,vz=1-vx,1-vz
        elif c['rotation']=='counterclockwise_90':vx,vz=vz,1-vx
        cases.append({**c,'label':f'structure_{c["case"]:02}_{c["rotation"]}_{c["mirror"]}','vertex':[vx,vz]})
    cases += [{'label':'actual_corner_first','position':[60,80,36],'vertex':[1,1]},
              {'label':'actual_reversed_arms','position':[72,80,36],'vertex':[1,1]},
              {'label':'decorated_pair_oblique','position':[48,80,84],'vertex':[1,1]}]
    if (pack/'CONTROLS.json').exists():
        cases += json.loads((pack/'CONTROLS.json').read_text())
    for i,c in enumerate(cases):
        x,y,z=c['position'];vx,vz=c['vertex']
        cx,cz=x+vx+(2.2 if vx else -2.2),z+vz+(2.2 if vz else -2.2)
        yaw=math.degrees(math.atan2(cx-(x+.5),(z+.5)-cz))
        c['camera']=[cx,82,cz,yaw,35]
        lines=['time set noon','gamemode spectator @s',f'tp @s {cx} 82 {cz} {yaw} 35',f'say ACCEPTANCE_SCENE_{i:02}_{c["label"]}']
        if i==0:lines += ['execute if block 0 80 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower] run say OWNER_EXACT_LOWER_PASS',
                          'execute if block 0 81 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper] run say OWNER_EXACT_UPPER_PASS']
        (funcs/f'acceptance_view_{i:02}.mcfunction').write_text('\n'.join(lines)+'\n')
        tour=[f'execute as @a at @s run function plaster_probe:acceptance_view_{i:02}']
        if i+1<len(cases):tour.append(f'schedule function plaster_probe:acceptance_tour_{i+1:02} 12s replace')
        else:tour.append('say ACCEPTANCE_SCENES_DONE')
        (funcs/f'acceptance_tour_{i:02}.mcfunction').write_text('\n'.join(tour)+'\n')
    (pack/'ACCEPTANCE_VIEWS.json').write_text(json.dumps(cases,indent=2)+'\n')
    print(f'Prepared {len(cases)} observation-only camera views')


if __name__=='__main__':main()
