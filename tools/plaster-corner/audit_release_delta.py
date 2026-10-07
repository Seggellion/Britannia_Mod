"""Read-only cumulative release audit; no credentials, deployments or ref writes."""
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/hotfixes/combined-0.1.8d'
def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()
def artifact(path):
    path = Path(path)
    with zipfile.ZipFile(path) as z:
        identity = dict(line.split('=',1) for line in z.read('britannia_mod_build.properties').decode().splitlines() if '=' in line)
    return dict(path=str(path), size=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest(), identity=identity)
def ancestor(ref, target):
    return subprocess.run(['git','merge-base','--is-ancestor',ref,target],cwd=ROOT).returncode==0

c='c1f2b5b819834ab971a2fc65b86fcd484d31e7c9'
b='339cc70bb78cb1b99de114e57ca0f8e75f8e8a80'
head=git('rev-parse','HEAD')
fixes=[]
for commit in ['9baa6e42','7ce2de4e','e4418d6f','96b7127a','57136c93','bed34811']:
    paths=git('diff-tree','--no-commit-id','--name-only','-r',commit).splitlines()
    implementation=[p for p in paths if p.startswith('src/main/') and '/gametest/' not in p]
    changed=git('diff','--name-only',c,head,'--',*implementation).splitlines()
    fixes.append(dict(commit=git('rev-parse',commit),subject=git('show','-s','--format=%s',commit),
        integrated=ancestor(commit,head),present_at_c_source=ancestor(commit,c),
        present_at_published_b_source=ancestor(commit,b),implementation_paths=implementation,
        implementation_paths_changed_since_c=changed))
artifacts=[artifact(ROOT/'build/libs/britannia_mod-0.1.8c-all.jar'),
    artifact('C:/projects/britannia/patch18-closeout/20260914-161120/release-final-20260914-223029/P-britannia_mod-0.1.8b-all-339cc70b.jar'),
    artifact('C:/projects/britannia/backup/mod_files/britannia_mod-0.1.8b.jar')]
result=dict(audited_head=head,c_reference=c,c_exact_released_source_proven=False,
    c_limitation='Retained C artifact is dirty; no C release tag, publication receipt or installed runtime identity establishes the exact deployed tree.',
    b_published_source=b,b_public_snapshot='f039c4b259d925872618bfe64ee574b20776856c',
    b_receipt='C:/projects/britannia/patch18-closeout/20260914-161120/release-final-20260914-223029/PUBLICATION-RECEIPT.txt',
    b_live_deployment_proven=False,artifacts=artifacts,network_fixes=fixes,
    delta_from_c=dict(commits=git('log','--format=%H %s',c+'..'+head).splitlines(),
        implementation=git('diff','--name-only',c,head,'--','src/main/java','src/main/resources').splitlines(),
        tests=git('diff','--name-only',c,head,'--','src/test').splitlines(),
        tooling=git('diff','--name-only',c,head,'--','build.gradle','gradle.properties','tools').splitlines()),
    delta_from_published_b=git('log','--format=%H %s',b+'..'+head).splitlines())
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'RELEASE_DELTA.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(head=head,network_fixes=[{k:f[k] for k in ['subject','integrated','present_at_c_source','present_at_published_b_source','implementation_paths_changed_since_c']} for f in fixes]),indent=2))
