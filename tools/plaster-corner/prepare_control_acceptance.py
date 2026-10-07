"""Supplementary straight/T ordinary command controls (not BlockItem trials)."""
import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from prepare_scene import ROOT,FULL,BLANK,state


def main():
    pack=ROOT/'tmp/plaster-corner-0.1.8d/client-resume/saves/Plaster Corner Baseline/datapacks/plaster_probe'
    assert (pack.parent.parent/'level.dat').exists()
    configs=[(84,'east','straight',False),(96,'east','straight',True),(108,'north','t_junction',False),(120,'north','t_junction',True)]
    lines=[];manifest=[]
    for x,f,shape,mirror in configs:
        z=36;label=f'{f}_{shape}_true_mirror_{str(mirror).lower()}'
        lines += [f'fill {x-3} 80 {z-3} {x+3} 81 {z+3} minecraft:air']
        if shape=='t_junction':
            for xx,zz,face in [(x-1,z,'north'),(x+1,z,'north'),(x,z+1,'east')]:
                for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {xx} {y} {zz} '+state(BLANK,face,half=half,shape='straight'))
        for half,y in [('upper',81),('lower',80)]:lines.append(f'setblock {x} {y} {z} '+state(FULL,f,True,mirror,half,shape))
        lines += [f'say CONTROL_{label}']
        for half,y in [('lower',80),('upper',81)]:
            expected=state(FULL,f,True,mirror,half,shape)
            lines += [f'execute if block {x} {y} {z} {expected} run say CONTROL_{label}_{half}_EXACT_PASS',
                      f'execute unless block {x} {y} {z} {expected} run say CONTROL_{label}_{half}_NORMALIZED']
        manifest.append({'label':'control_'+label,'position':[x,80,z],'requested':state(FULL,f,True,mirror,'lower',shape),'vertex':[1,1]})
    (pack/'CONTROLS.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (pack/'data/plaster_probe/function/control_setup.mcfunction').write_text('\n'.join(lines)+'\n')
    print('Prepared four supplementary straight/T branch/mirror/orientation controls')


if __name__=='__main__':main()
