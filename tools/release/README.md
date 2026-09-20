# Same-version installed-file preflight

Python 3.11+ is required. This read-only helper consumes the existing
`britannia_mod_build.properties` provenance and an approved full hash/source.
It complements Gradle `artifactIdentity` and the running server's `/grabby env`;
it does not add another identity format or install anything.

```text
python tools/release/verify_installation.py --candidate <selected-all.jar> --mods <instance/mods> --instance <instance> --expected-sha256 <64-digits> --expected-source <40-digits> --repository <checkout>
```

Exit 0 means `FILE_MATCH`: the clean bundled candidate has the approved identity,
its source is an ancestor of `patch-18`, and exactly one active Britannia jar in
the named instance has matching bytes/source. A legitimate task build branch is
accepted; the embedded branch name is not an authorization gate. `--approved-ref`
can select another explicit source lineage for an operator's checkout.

Exit 1 means `FILE_MISMATCH`, with concrete reasons. Renamed jars are identified
by their implementation class, and disabled `.old` files do not count as active.
The JSON deliberately always reports `LOADED_RUNTIME_UNVERIFIED`. Files cannot
show what an existing JVM loaded. After restart, record startup origin/session
and the existing `/grabby env` artifact/build/server lines separately. A matching
version string, recent file timestamp or preflight success is not runtime proof.

The portable selected sidecar contains filename, bytes, SHA-256, mod version,
source, dirty flag and timestamp, without installation paths or credentials.
Instance/process records belong in the private recovery receipt or dated matrix.
Never transmit authentication launch arguments or private server configuration.

Run the focused tool tests with:

```text
python -m unittest discover -s tools/release/tests -v
```

Client recovery is an explicit operator operation: close the relevant client,
back up its original jar outside `mods`, install only the selected bundled jar,
and rerun preflight. This helper does not force a running client to exit or mutate
an installation. Production server replacement/restart is a separate operation.
