# Changelog

## Fresh CloudBridge rebuild for Proton Drive bisync

The base is CloudBridge `ccea16c3645a0d2b3cadea38eda90531153f5169`. This rebuild keeps the upstream README, branding, and existing one-way tasks. It adds a narrowly scoped workflow in the existing task editor and WorkManager integration.

**Task and consent.** A saved task can link a local directory and a direct Proton Drive remote. Initial setup and recovery require an explicit Resync. The app resolves the authenticated Proton folder before asking for approval. The approved identity reaches the native command, where rclone verifies it again before mutation. The app binds approval to the task, local directory identity, selected remote, stable configuration, and exact engine commit. Token rotation is excluded from the app configuration fingerprint. The engine independently binds its own baseline to the real account and folder. The worker uses the live native configuration so refreshes remain durable. Changing the task or account cannot silently authorize a new baseline.

**Run and failure handling.** Scheduled work uses the existing task system and requires a successful matching baseline. The state marker is invalidated before transfers and restored only after a successful matching run. Cancellation and failure leave the task requiring explicit review. Arguments use a structured command, strict checksums and a 10% deletion cap. The local version wins a conflict during the explicitly approved Resync. The workflow rejects unsupported filtered tasks and keeps app-private storage out of endpoints. It uses private work directories and an exclusive bisync lock. Native output is drained and credential diagnostics are redacted.

**Android engine.** The bundled source is Rareities/rclone `04da7ebc056537be4106436b9984d9c68d151959`. Android builds use bionic DNS and PIE at API 23 with 16 KiB ELF alignment for all supported ABIs. The existing Android UI, app version and release identity come from the CloudBridge base. For a local build before the engine commit reaches GitHub, run `./gradlew -Pde.schuelken.cloudbridge.rCloneRepoUrl=file:///workspace/final/rclone assembleOssDebug` from this app checkout.

**Credential logging.** Removed raw configuration output from Internxt reauthentication error logs.

**Validation.** The final app passed 50 host tests with no failures or skips and passed lint. It built five debug APKs and the instrumentation APK. The packaged engines match the native build outputs and the expected machine type, Android interpreter, 16 KiB alignment and pinned revision. The live Proton, device and signed-release checks were not run.

## Rebuild commits

- `014e6f4fbef3099853444daa98fb7109b858f68e` · 2026-11-19 10:22:36 NZDT (+1300) · “feat: add guarded Proton Drive two-way tasks”
