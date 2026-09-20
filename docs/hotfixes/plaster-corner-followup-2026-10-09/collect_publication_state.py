"""Read verified remote refs/current PR/rules/CI; never push or merge from this collector."""
import json,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];DOC=Path(__file__).resolve().parent;EV=DOC/'evidence'
def cmd(*args):return subprocess.check_output(args,cwd=ROOT,text=True,encoding='utf-8')
def api(path):return json.loads(cmd('gh','api',path))
repo='repos/Seggellion/Britannia_Mod'
refs={line.split()[1]:line.split()[0] for line in cmd('git','ls-remote','origin','refs/heads/patch-18','refs/heads/main','refs/heads/codex/patch18-0.1.8d-source').splitlines()}
pr=json.loads(cmd('gh','pr','view','501','--repo','Seggellion/Britannia_Mod','--json','number,url,state,mergeStateStatus,headRefName,headRefOid,baseRefOid,statusCheckRollup'))
rules=api(repo+'/rulesets/17373102');signature=api(repo+'/commits/'+pr['headRefOid'])['commit']['verification'];branch=api(repo+'/branches/patch-18')
result={'captured_utc':datetime.now(timezone.utc).isoformat(),'client_date':'2026-10-09','local_branch':cmd('git','branch','--show-current').strip(),'local_head':cmd('git','rev-parse','HEAD').strip(),'remote_refs':refs,'pr':pr,'signature':signature,'main_ruleset':{k:rules[k] for k in ['id','name','enforcement','conditions','bypass_actors','rules']},'development_branch_protected':branch['protected'],'main_changed_by_this_task':False,'protection_or_bypass_changed':False}
(EV/'REMOTE_STATE.json').write_text(json.dumps(result,indent=2)+'\n')
failed=cmd('gh','run','view','37687457615','--repo','Seggellion/Britannia_Mod','--log-failed')
filtered=[l for l in failed.splitlines() if any(s in l for s in ['initializationError FAILED','AssertionFailedError at','4124 tests completed','> Task :test FAILED','There were failing tests','BUILD FAILED'])]
(EV/'EXISTING_PR_CI_FAILURES.txt').write_text('\n'.join(filtered)+'\n')
# The current public snapshot predates an excluded-from-JAR fence GameTest harness.
# Do not describe whole-source parity with current HEAD while that retained test differs.
diff=cmd('git','diff','--name-only','986ed746','HEAD','--','src/main','src/test','tools','build.gradle','gradle.properties','.gitattributes').strip()
production=cmd('git','diff','--name-only','986ed746','HEAD','--','src/main','src/test','tools','build.gradle','gradle.properties','.gitattributes',':(exclude)src/main/java/com/seggellion/britannia_mod/gametest/**').strip()
parity={'snapshot':pr['headRefOid'],'snapshot_candidate_source':'986ed746866dc944b7b496ab9428b670f65e15c0','current_source':result['local_head'],'production_delta_since_candidate':production,'retained_test_source_delta_since_candidate':diff,'whole_current_source_snapshot_parity':not diff,'new_public_snapshot_for_plaster_required':False,'reason':'This task changes excluded internal follow-up records/analysis/fixtures only. Production implementation/resources remain equal. Concurrent fence test harness is recorded separately, not hidden or relabeled excluded from public source.'}
(EV/'PUBLIC_SCOPE_AUDIT.json').write_text(json.dumps(parity,indent=2)+'\n')
print(json.dumps({'local_head':result['local_head'],'remote_refs':refs,'pr_state':pr['state'],'merge_state':pr['mergeStateStatus'],'signature_verified':signature['verified'],'production_delta':production,'retained_test_delta':diff},indent=2))
