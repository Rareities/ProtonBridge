# Changelog

## Fresh CloudBridge rebuild for Proton Drive bisync

The base is CloudBridge `ccea16c3645a0d2b3cadea38eda90531153f5169`. This rebuild keeps the upstream README, branding, and existing one-way tasks. It adds a narrowly scoped workflow in the existing task editor and WorkManager integration.

**Task and consent.** A saved task can link a local directory and a direct Proton Drive remote. Initial setup and recovery require an explicit Resync. The app resolves the authenticated Proton folder before asking for approval. The approved identity reaches the native command, where rclone verifies it again before mutation. The app binds approval to the task, local directory identity, selected remote, stable configuration, and exact engine commit. Token rotation is excluded from the app configuration fingerprint. The engine independently binds its own baseline to the real account and folder. The worker uses the live native configuration so refreshes remain durable. Changing the task or account cannot silently authorize a new baseline.

**Run and failure handling.** Scheduled work uses the existing task system and requires a successful matching baseline. The state marker is invalidated before transfers and restored only after a successful matching run. Cancellation and failure leave the task requiring explicit review. Arguments use a structured command, strict checksums and a 10% deletion cap. The local version wins a conflict during the explicitly approved Resync. The workflow rejects unsupported filtered tasks and keeps app-private storage out of endpoints. It uses private work directories and an exclusive bisync lock. Native output is drained and credential diagnostics are redacted.

**Android engine.** The bundled source is Rareities/rclone `04da7ebc056537be4106436b9984d9c68d151959`. Android builds use bionic DNS and PIE at API 23 with 16 KiB ELF alignment for all supported ABIs. The existing Android UI, app version and release identity come from the CloudBridge base. For a local build before the engine commit reaches GitHub, run `./gradlew -Pde.schuelken.cloudbridge.rCloneRepoUrl=file:///workspace/final/rclone assembleOssDebug` from this app checkout.

**Audit decisions.** The former fork's explicit approval, private state, authenticated reconnect handling, scheduled operation and useful failure feedback informed the replacement. Upstream owns the wider app's scheduling, transfer, backup, import, sharing, storage and notifications. The rewrite leaves out the old package rename, branding, README edits, arbitrary flag editor, duplicated scheduler, background probe, backup redesign, release bot and temporary release notes. The existing Internxt UI retains compatible engine behavior, with raw config dump output removed from error logging.

**Validation.** The final app passed 50 host tests with no failures or skips and passed lint. It built five debug APKs and the instrumentation APK. The packaged engines match the native build outputs and the expected machine type, Android interpreter, 16 KiB alignment and pinned revision. The live Proton, device and signed-release checks were not run.

## Rebuild commits

- `014e6f4fbef3099853444daa98fb7109b858f68e` · 2026-11-19 10:22:36 NZDT (+1300) · “feat: add guarded Proton Drive two-way tasks”

## Historical audit index

These commits identify behavior examined from the former fork. They remain historical source material. The sections above record which behaviors were reimplemented and why.

| Historical commit | Author time in Auckland | Status in former fork | Recorded change |
|---|---|---|---|
| [`5fac834be5db`](https://github.com/Rareities/ProtonBridge/commit/5fac834be5db90839d1968e4c9a9d44845c1bb22) | 2026-10-11 03:01:41 NZDT (+1300) | Archived / closed unmerged history | feat: pin hardened engine fork and add guarded Proton bisync |
| [`1a72af628e93`](https://github.com/Rareities/ProtonBridge/commit/1a72af628e93c7ce9b112c342661342595f099a4) | 2026-10-11 12:02:58 NZDT (+1300) | Archived / closed unmerged history | feat: extend bisync, encrypted vault, scheduling and provider lifecycle |
| [`7f03a4355261`](https://github.com/Rareities/ProtonBridge/commit/7f03a4355261af1e060cc06d78d32e720cd01c9e) | 2026-10-11 12:21:24 NZDT (+1300) | Archived / closed unmerged history | feat: bind bisync filters to approval and add Proton reconnect gates |
| [`1339acc9fb7e`](https://github.com/Rareities/ProtonBridge/commit/1339acc9fb7eb9a18c05afec66b162ff17d118f8) | 2026-10-11 12:30:13 NZDT (+1300) | Archived / closed unmerged history | feat(bisync): add saved tuning presets and validate connection budgets |
| [`fdf65556f596`](https://github.com/Rareities/ProtonBridge/commit/fdf65556f596da8798cb1566a3a2e8398b552600) | 2026-10-11 16:46:23 NZDT (+1300) | On master | feat: add guarded Proton bisync workflows and task scheduling |
| [`e24703f3e04e`](https://github.com/Rareities/ProtonBridge/commit/e24703f3e04e4d07dec861d6c9e6111fe1b7c313) | 2026-10-11 16:49:25 NZDT (+1300) | On master | ci: install supported Android SDK packages |
| [`39251088ce17`](https://github.com/Rareities/ProtonBridge/commit/39251088ce172cee10c9b83a3c25fcd2e66545c3) | 2026-10-11 18:49:22 NZDT (+1300) | On master | fix: validate native bisync lifecycle and complete task workflows |
| [`ab161aa76cca`](https://github.com/Rareities/ProtonBridge/commit/ab161aa76cca548dcda728324d899b389f3ba4e1) | 2026-10-11 20:39:16 NZDT (+1300) | On master | fix: harden privacy boundaries and ship a signed bisync release candidate |
| [`a50c7a078448`](https://github.com/Rareities/ProtonBridge/commit/a50c7a078448d2edfdd80989e46a8c847c7f0327) | 2026-10-11 20:46:52 NZDT (+1300) | On master | test: validate signed release without test-only shrunk helper calls |
| [`e18a057f1c92`](https://github.com/Rareities/ProtonBridge/commit/e18a057f1c925510af6dd08d90f697a5ed9307b0) | 2026-10-11 22:01:11 NZDT (+1300) | On master | fix: complete remote setup lifecycle and preserve queued task actions |
| [`a6589372cbf8`](https://github.com/Rareities/ProtonBridge/commit/a6589372cbf86e0b72fb374b500ff81ac87cc7a6) | 2026-10-11 22:12:27 NZDT (+1300) | On master | Merge verified Proton bisync, privacy and signed release candidate |
| [`f5b8f5fc3e28`](https://github.com/Rareities/ProtonBridge/commit/f5b8f5fc3e282116ad1ecfd7eabc1e6c6ce247db) | 2026-10-11 23:51:55 NZDT (+1300) | On master | fix: bind approvals to configuration changes and durable restore state |
| [`cbcda693b2c9`](https://github.com/Rareities/ProtonBridge/commit/cbcda693b2c924ebc0939fb0099ad02c47d2a28f) | 2026-10-11 23:52:27 NZDT (+1300) | On master | docs: explain personal fork and record full commit-linked changelog |
| [`e8f2a5ad5aae`](https://github.com/Rareities/ProtonBridge/commit/e8f2a5ad5aae553eca7a5fd5d014d413120d9316) | 2026-10-11 23:57:39 NZDT (+1300) | On master | fix: explain missing bisync initialization before resolving baseline UUID |
| [`dbbe9ee32f91`](https://github.com/Rareities/ProtonBridge/commit/dbbe9ee32f912f7c3060ce617d792b3bf84ca4dc) | 2026-10-11 23:57:39 NZDT (+1300) | On master | docs: record first-run bisync UUID fix and validation |
| [`f59a2577405c`](https://github.com/Rareities/ProtonBridge/commit/f59a2577405c16a4ce3ac93cb1ebdf47a0fcb5f9) | 2026-10-12 00:13:41 NZDT (+1300) | On master | fix: simplify bisync setup and report task startup and waiting phases |
| [`307e8772f8c3`](https://github.com/Rareities/ProtonBridge/commit/307e8772f8c3574cf9374410f2160f47a0328c1b) | 2026-10-12 00:13:55 NZDT (+1300) | On master | docs: link bisync execution fixes and verification in fork changelog |
| [`eb41c11444ff`](https://github.com/Rareities/ProtonBridge/commit/eb41c11444ff4b8e2359b94d96ed307ea8bb428e) | 2026-10-12 00:28:43 NZDT (+1300) | On master | ci: allow isolated bisync test prerelease on reviewed branch |
| [`d4521de6c15a`](https://github.com/Rareities/ProtonBridge/commit/d4521de6c15a86ee85e8f485299ee0ac239fda9c) | 2026-10-12 00:35:01 NZDT (+1300) | On master | ci: publish verified debug APK prereleases from isolated test branch |
| [`42988641a584`](https://github.com/Rareities/ProtonBridge/commit/42988641a5848c4560c6aedb85be1a1859849349) | 2026-10-12 00:40:14 NZDT (+1300) | On master | docs: record test branches and prerelease distribution gates |
| [`461d911d63ee`](https://github.com/Rareities/ProtonBridge/commit/461d911d63eef19a60449931ff9a2e8686393ee9) | 2026-10-12 01:19:47 NZDT (+1300) | On master | chore: retire temporary test publishing and prepare signed promotion |
| [`fe23bd52d90e`](https://github.com/Rareities/ProtonBridge/commit/fe23bd52d90e46ca63384db4b174bc50b4a3cc05) | 2026-10-12 01:19:47 NZDT (+1300) | On master | Merge audited bisync fixes, fork documentation and release cleanup |
| [`ad272aaa5254`](https://github.com/Rareities/ProtonBridge/commit/ad272aaa5254c80799552a27adcbc57a6e5467e1) | 2026-10-12 01:37:28 NZDT (+1300) | On master | docs: record signed promotion audit and repository cleanup |
| [`7227ad5d1241`](https://github.com/Rareities/ProtonBridge/commit/7227ad5d12416e1efda1581704e811b36d7cd474) | 2026-10-12 10:22:16 NZDT (+1300) | On master | feat: rebrand fork as ProtonBridge and improve Proton listings |
| [`8c002e3de84e`](https://github.com/Rareities/ProtonBridge/commit/8c002e3de84e37f5edc07f56dcc22b2f4decdb40) | 2026-10-12 10:29:05 NZDT (+1300) | On master | docs: clarify ProtonBridge and upstream maintainer attribution |
| [`62a282181ff6`](https://github.com/Rareities/ProtonBridge/commit/62a282181ff6d1c6ebcf19243e2fc25b44f2b89a) | 2026-10-12 10:46:43 NZDT (+1300) | On master | docs: record signed ProtonBridge release verification |
| [`96d0f0d2f620`](https://github.com/Rareities/ProtonBridge/commit/96d0f0d2f6208da08251b3832d89f844b3040d8a) | 2026-10-12 15:18:10 NZDT (+1300) | On master | perf: reduce battery overhead and harden background privacy |
| [`e1595bcd07c4`](https://github.com/Rareities/ProtonBridge/commit/e1595bcd07c468a134829d7608c8df1c61d9dd19) | 2026-10-12 15:35:47 NZDT (+1300) | On master | docs: record battery and privacy release verification |
| [`ed2329384411`](https://github.com/Rareities/ProtonBridge/commit/ed232938441169c37bee15a4a292d81bd95d663a) | 2026-10-12 15:50:37 NZDT (+1300) | On master | fix: simplify flag editing and support conflict resolution |
| [`c0113618d1c3`](https://github.com/Rareities/ProtonBridge/commit/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d) | 2026-10-12 16:09:09 NZDT (+1300) | On master | docs: record flag editor release verification |
| [`28b96c970156`](https://github.com/Rareities/ProtonBridge/commit/28b96c9701566c2563bd79ad5ee959425a9e6a46) | 2026-10-12 16:19:36 NZDT (+1300) | On master | docs: consolidate ProtonBridge documentation and release notes |
| [`76b711c03594`](https://github.com/Rareities/ProtonBridge/commit/76b711c035944afa7b8677172d907ed545df2270) | 2026-10-12 21:42:37 NZDT (+1300) | On master | fix: pin consistent Proton revision metadata and explain checksum failures |
| [`5047b4c387f1`](https://github.com/Rareities/ProtonBridge/commit/5047b4c387f1dd185bb2790f58e97c052493bcce) | 2026-10-12 22:23:51 NZDT (+1300) | On master | fix: pin verified legacy Proton revision metadata resolution |
| [`691ebb7b1c8b`](https://github.com/Rareities/ProtonBridge/commit/691ebb7b1c8bb9fec2ccb32d481042eff6cc9ba5) | 2026-10-12 23:47:11 NZDT (+1300) | On master | fix: update Proton revision lookup for preview and downloads |
| [`42b292ae4ff5`](https://github.com/Rareities/ProtonBridge/commit/42b292ae4ff520ae6838e684457a9f72beba42d9) | 2026-10-13 10:59:58 NZDT (+1300) | On master | fix: stabilize Proton bisync metadata and apply filters consistently |
| [`8a052dab5140`](https://github.com/Rareities/ProtonBridge/commit/8a052dab51409b02d5e9b4a346a36a7107e2714e) | 2026-10-13 11:51:23 NZDT (+1300) | On master | fix: defer blocked background starts without invalidating bisync state |
| [`4674c39621dc`](https://github.com/Rareities/ProtonBridge/commit/4674c39621dce5e21e6c71771dc9d582fbc53800) | 2026-10-13 12:20:33 NZDT (+1300) | On master | fix: report Android start restrictions without redundant retries |
| [`8c047e03d824`](https://github.com/Rareities/ProtonBridge/commit/8c047e03d82460f6073243b5c218ced1087a6905) | 2026-10-13 12:27:19 NZDT (+1300) | On master | release: publish audited ProtonBridge 1.2.0 stable |
| [`7fab8aaaa178`](https://github.com/Rareities/ProtonBridge/commit/7fab8aaaa1783fea2a721ac3490eaf9a124d4e24) | 2026-10-19 00:34:35 NZDT (+1300) | On master | feat: simplify bisync resync and enable native recovery with upstream fixes |
| [`63b1d17310c0`](https://github.com/Rareities/ProtonBridge/commit/63b1d17310c08010ce607e33fef93d0bbee61b5a) | 2026-11-04 23:13:10 NZDT (+1300) | On master | fix: audit fork state durability and reconcile upstream security boundaries |
| [`fd70e69db540`](https://github.com/Rareities/ProtonBridge/commit/fd70e69db5400b86d867f143bee00b3fa89759ae) | 2026-11-04 23:13:10 NZDT (+1300) | On master | merge: record reviewed CloudBridge upstream through a9b6085b |
| [`1e20c886c994`](https://github.com/Rareities/ProtonBridge/commit/1e20c886c99401700b7b38589a0647856d5a6813) | 2026-11-04 23:13:29 NZDT (+1300) | On master | docs: link completed fork audit in application history |
| [`d0dc80d33988`](https://github.com/Rareities/ProtonBridge/commit/d0dc80d339885481e99502dcaa71da8ffd00b41b) | 2026-11-05 21:35:52 NZDT (+1300) | On master | docs: document every application fork commit including archived history |
| [`83b1a1e8eed4`](https://github.com/Rareities/ProtonBridge/commit/83b1a1e8eed430021525aadcbc442649f60a0070) | 2026-11-05 21:42:36 NZDT (+1300) | On master | docs: replace app changelog with dated fork commit history |

The intermediate local rebuild was also compared against these upstream bases. Its source remains available in the preserved local history.
