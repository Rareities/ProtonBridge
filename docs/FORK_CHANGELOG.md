# ProtonBridge fork changelog

Only changes introduced by the Rareities ProtonBridge fork are listed here.
Inherited CloudBridge/RCX/rcloneExplorer features and upstream release notes are excluded.
The rclone fork has its own [separate changelog](https://github.com/Rareities/rclone/blob/master/CHANGELOG_FORK.md).
Historical entries record changes even when later corrected or removed.

## 1.2.2 — fork maintenance

- Remove unused session-scheduler inspection/latch helper and superseded legacy import constructors; retain active scheduling and validated atomic import paths.
- Verify installed bisync-state and manual reconnect-generation bytes before reporting critical writes successful; reject stale or failed replacements.
- Preserve authenticated encrypted-backup proxy and rollback semantics when applying the stricter validated import plan.
- Keep the custom ProtonBridge application identity, attribution and Proton-focused bisync workflow.
- Maintain the exact rclone fork revision pin and signed APK/source provenance.
- Separate the application and engine fork histories; remove inherited upstream release notes
  and engine implementation changes from this application changelog.

## Fork changes retained in the application

- ProtonBridge branding, package ID, icon, startup description and fork attribution.
- Persistent bisync tasks, private UUID baselines, scheduling, run history and explicit Resync.
- Native recovery flag support on matching baselines, with no mandatory dry-run workflow.
- Plain flag editor and bundled searchable flag reference; shared flag/filter arguments.
- Stable filter identities, saved-rule validation and restrictive basic include/exclude composition.
- Account-aware Proton reconnection, paused-account admission and rotating-session identity handling.
- Native process ownership, cancellation, concurrent operation admission and configuration exclusion.
- Bounded redacted diagnostics and explicit Android background-start failure reporting.
- Verified atomic configuration/password writes, restore rollback and uncertain-state recovery.
- Newer/older conflict timestamp handling and explicit first-run precedence.
- Optional bounded session checks, idle listener shutdown and duplicate notification suppression.
- Fork-specific immutable signed release pipeline, ABI/version checks and engine provenance verification.
- Host/device integration tests and disposable native test tooling for the fork's behavior.

## Complete application fork commit history

This inventory excludes commits reachable from direct CloudBridge upstream and excludes merge commits.
Mixed integration commits below describe only their fork-specific contribution.

| Commit | Fork change |
|---|---|
| [fdf65556f5](https://github.com/Rareities/ProtonBridge/commit/fdf65556f596da8798cb1566a3a2e8398b552600) | feat: add guarded Proton bisync workflows and task scheduling |
| [e24703f3e0](https://github.com/Rareities/ProtonBridge/commit/e24703f3e04e4d07dec861d6c9e6111fe1b7c313) | ci: install supported Android SDK packages |
| [39251088ce](https://github.com/Rareities/ProtonBridge/commit/39251088ce172cee10c9b83a3c25fcd2e66545c3) | fix: validate native bisync lifecycle and complete task workflows |
| [ab161aa76c](https://github.com/Rareities/ProtonBridge/commit/ab161aa76cca548dcda728324d899b389f3ba4e1) | fix: harden privacy boundaries and ship a signed bisync release candidate |
| [a50c7a0784](https://github.com/Rareities/ProtonBridge/commit/a50c7a078448d2edfdd80989e46a8c847c7f0327) | test: validate signed release without test-only shrunk helper calls |
| [e18a057f1c](https://github.com/Rareities/ProtonBridge/commit/e18a057f1c925510af6dd08d90f697a5ed9307b0) | fix: complete remote setup lifecycle and preserve queued task actions |
| [f5b8f5fc3e](https://github.com/Rareities/ProtonBridge/commit/f5b8f5fc3e282116ad1ecfd7eabc1e6c6ce247db) | fix: bind approvals to configuration changes and durable restore state |
| [cbcda693b2](https://github.com/Rareities/ProtonBridge/commit/cbcda693b2c924ebc0939fb0099ad02c47d2a28f) | docs: explain personal fork and record full commit-linked changelog |
| [e8f2a5ad5a](https://github.com/Rareities/ProtonBridge/commit/e8f2a5ad5aae553eca7a5fd5d014d413120d9316) | fix: explain missing bisync initialization before resolving baseline UUID |
| [dbbe9ee32f](https://github.com/Rareities/ProtonBridge/commit/dbbe9ee32f912f7c3060ce617d792b3bf84ca4dc) | docs: record first-run bisync UUID fix and validation |
| [f59a257740](https://github.com/Rareities/ProtonBridge/commit/f59a2577405c16a4ce3ac93cb1ebdf47a0fcb5f9) | fix: simplify bisync setup and report task startup and waiting phases |
| [307e8772f8](https://github.com/Rareities/ProtonBridge/commit/307e8772f8c3574cf9374410f2160f47a0328c1b) | docs: link bisync execution fixes and verification in fork changelog |
| [eb41c11444](https://github.com/Rareities/ProtonBridge/commit/eb41c11444ff4b8e2359b94d96ed307ea8bb428e) | ci: allow isolated bisync test prerelease on reviewed branch |
| [d4521de6c1](https://github.com/Rareities/ProtonBridge/commit/d4521de6c15a86ee85e8f485299ee0ac239fda9c) | ci: publish verified debug APK prereleases from isolated test branch |
| [42988641a5](https://github.com/Rareities/ProtonBridge/commit/42988641a5848c4560c6aedb85be1a1859849349) | docs: record test branches and prerelease distribution gates |
| [461d911d63](https://github.com/Rareities/ProtonBridge/commit/461d911d63eef19a60449931ff9a2e8686393ee9) | chore: retire temporary test publishing and prepare signed promotion |
| [ad272aaa52](https://github.com/Rareities/ProtonBridge/commit/ad272aaa5254c80799552a27adcbc57a6e5467e1) | docs: record signed promotion audit and repository cleanup |
| [7227ad5d12](https://github.com/Rareities/ProtonBridge/commit/7227ad5d12416e1efda1581704e811b36d7cd474) | feat: rebrand fork as ProtonBridge and improve Proton listings |
| [8c002e3de8](https://github.com/Rareities/ProtonBridge/commit/8c002e3de84e37f5edc07f56dcc22b2f4decdb40) | docs: clarify ProtonBridge and upstream maintainer attribution |
| [62a282181f](https://github.com/Rareities/ProtonBridge/commit/62a282181ff6d1c6ebcf19243e2fc25b44f2b89a) | docs: record signed ProtonBridge release verification |
| [96d0f0d2f6](https://github.com/Rareities/ProtonBridge/commit/96d0f0d2f6208da08251b3832d89f844b3040d8a) | perf: reduce battery overhead and harden background privacy |
| [e1595bcd07](https://github.com/Rareities/ProtonBridge/commit/e1595bcd07c468a134829d7608c8df1c61d9dd19) | docs: record battery and privacy release verification |
| [ed23293844](https://github.com/Rareities/ProtonBridge/commit/ed232938441169c37bee15a4a292d81bd95d663a) | fix: simplify flag editing and support conflict resolution |
| [c0113618d1](https://github.com/Rareities/ProtonBridge/commit/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d) | docs: record flag editor release verification |
| [28b96c9701](https://github.com/Rareities/ProtonBridge/commit/28b96c9701566c2563bd79ad5ee959425a9e6a46) | docs: consolidate ProtonBridge documentation and release notes |
| [76b711c035](https://github.com/Rareities/ProtonBridge/commit/76b711c035944afa7b8677172d907ed545df2270) | fix: pin consistent Proton revision metadata and explain checksum failures |
| [5047b4c387](https://github.com/Rareities/ProtonBridge/commit/5047b4c387f1dd185bb2790f58e97c052493bcce) | fix: pin verified legacy Proton revision metadata resolution |
| [691ebb7b1c](https://github.com/Rareities/ProtonBridge/commit/691ebb7b1c8bb9fec2ccb32d481042eff6cc9ba5) | Promote the fork engine pin and improve bisync diagnostic guidance. |
| [42b292ae4f](https://github.com/Rareities/ProtonBridge/commit/42b292ae4ff520ae6838e684457a9f72beba42d9) | Fix filter composition/editor state, timestamp handling, redaction, configuration/restore durability and pin the fork engine. |
| [8a052dab51](https://github.com/Rareities/ProtonBridge/commit/8a052dab51409b02d5e9b4a346a36a7107e2714e) | fix: defer blocked background starts without invalidating bisync state |
| [4674c39621](https://github.com/Rareities/ProtonBridge/commit/4674c39621dce5e21e6c71771dc9d582fbc53800) | fix: report Android start restrictions without redundant retries |
| [8c047e03d8](https://github.com/Rareities/ProtonBridge/commit/8c047e03d82460f6073243b5c218ced1087a6905) | release: publish audited ProtonBridge 1.2.0 stable |
| [7fab8aaaa1](https://github.com/Rareities/ProtonBridge/commit/7fab8aaaa1783fea2a721ac3490eaf9a124d4e24) | Remove mandatory bisync preview; direct Resync; native recovery flags and baseline migration; suppress redundant notifications and optional empty-session probes. |
