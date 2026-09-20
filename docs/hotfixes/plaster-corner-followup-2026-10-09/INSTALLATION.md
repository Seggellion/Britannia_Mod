# Prepared installation and rollback — not performed

Use this only after the verified candidate's exterior A/B comparison passes and the owner chooses to install. No active profile or production server has been modified. [Manifest](evidence/INSTALLATION_MANIFEST.json) records all paths and hashes.

Candidate (original filename preserved):

```text
C:\projects\britannia\mod\Britannia_Mod\tmp\plaster-corner-0.1.8d\followup-20261009\artifacts\986ed746866dc944b7b496ab9428b670f65e15c0\britannia_mod-0.1.8d-all.jar
```

Clean source `986ed746866dc944b7b496ab9428b670f65e15c0`; **36,142,668 bytes**; SHA-256 **f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd**.

1. Close the selected client's process before replacing any loaded mod code. The selected profile is `C:\Users\dusti\curseforge\minecraft\Instances\UltimaCraft - Britannia`. Both old/new artifacts report 0.1.8d; identify by hash/source.
2. Preserve the current old JAR outside `mods`. A verified copy is already at `C:\projects\britannia\mod\Britannia_Mod\tmp\plaster-corner-0.1.8d\followup-20261009\rollback\c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6\britannia_mod-0.1.8d.jar`. If the active JAR has changed since preparation, back up that current file separately first; do not overwrite it assuming the historical hash.
3. Move the old load-eligible Britannia JAR out of `mods`, then copy the staged `britannia_mod-0.1.8d-all.jar` into `mods` under that original filename. Keep exactly one load-eligible Britannia mod JAR. Backups belong outside the eligible set.
4. Verify copied size/hash using `Get-Item` and `Get-FileHash -Algorithm SHA256`. Restart the client and independently verify loaded source/hash via `/grabby env` and startup logs. **F3+T is insufficient**: the candidate changes paired-wall Java code as well as art.
5. Prefer the matching disposable environment for initial behavior comparison. Server and client identities must be checked independently; client installation does not update a production server. Any production server update/restart requires its separate deployment workflow and is not performed here.

Rollback: close the client; move the candidate out of `mods`; restore the verified old backup as `britannia_mod-0.1.8d.jar`; ensure only one load-eligible Britannia JAR; verify old SHA-256 `c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6`; restart. Keep the candidate and evidence intact. Do not edit live worlds to perform installation/rollback.
