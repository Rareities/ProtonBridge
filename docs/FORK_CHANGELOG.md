# ProtonBridge — changes, usage and project notes

This records the Rareities changes on top of the direct upstream repositories.
Commit authors are git metadata, not a claim about who directed each line.
Integration merges are included without counting their features twice.

## 1.2.1 — direct Resync, native recovery and upstream updates

- Remove bisync dry-run/preview UI, reports, approval expiry, copied preview listings
  and the mandatory preview step. Resync saves the task, confirms folders, filters,
  flags and preferred copy, then performs real transfers. Scheduled runs never
  automatically choose Resync. Normal Run keeps using the completed baseline.
- Permit native `--recover` and `--resilient` boolean flags. A failed/interrupted
  ordinary run may retry its matching saved baseline when either is enabled;
  rclone validates whether recovery is possible. Adding these operational flags
  does not change file-selection identity. Missing baselines, changed endpoints,
  filters, configuration or engine still require Resync. Neither flag guarantees
  recovery of a failed first Resync. Correct spellings are shown for typos.
- Merge 52 official rclone commits through `35a03a47e00e2cadec744f977e5fa9d851127016`,
  pinned by fork merge [`814954580`](https://github.com/Rareities/rclone/commit/8149545802697161ce320aaabaca6b84f0396d50).
  Includes bisync skipped-conflict/listing fixes, include-filter performance work,
  VFS memory/race fixes and Drive fast-list hang fixes. Retain Proton metadata,
  checksum requirements, contained companion modules and atomic listing locks.
  This is upstream development source, not a new official stable rclone release.
- Reduce repeated work: publish RC notification summaries only when displayed
  counts change; stop repeatedly logging retained failed jobs at every poll.
  Optional session checks skip empty tokens and TOTP-only configurations.
  Lock ownership, streaming lifecycle, idle shutdown and transfer refresh remain.
  No measured device battery saving is claimed.
- Reconcile upstream CloudBridge security/reliability commit `f97ff8e3` with the fork:
  private validated share/download staging, loopback streaming URL capability,
  exact remote-name validation, checked missing-remote failures and fail-closed
  Keystore protection of SAF credentials. Preserve stronger existing redacted
  logging, native stream ownership and signed-build provenance protections.
- Validation: **222 app host tests, zero failures/skips**, 14 release-script tests,
  lint, all four native ABI builds, debug split/universal APKs and device-test APK
  build. Engine host/race regressions, four upstream bisync scenarios and native
  trusted-listing recovery checks passed; independent integration reviews completed.
  Device tests were compiled, not executed on a physical phone.
- Base version code 210; ARM64 217. The new engine pin requires a one-time Resync
  before an older engine baseline can be used. Existing releases stay immutable.

## 1.2.0 — audited stable release

- Publish the audited ProtonBridge source as **1.2.0**, a full stable release with
  the same application ID and signing certificate as the recent prereleases.
  Base version code is 200; ARM64 is 207. All split/universal codes exceed 1.1.9.
- Include the necessity-audit change from [`4674c396`](https://github.com/Rareities/ProtonBridge/commit/4674c39621dce5e21e6c71771dc9d582fbc53800):
  Android foreground-service refusal immediately reports user action, preserves
  the bisync baseline and suppresses failure follow-ups. Remove automatic retries
  of that permission/setup rejection. Ordinary network/charging/contention waits
  remain; no permissions, battery exemptions or platform bypasses are added.
- Retain the verified checksum snapshot, filter/editor, privacy, configuration-write,
  restore, timestamp and preview-cleanup fixes described in 1.1.8. Keep rclone pinned
  to `f1d1627e2e91867b5fed5eb6bb4d4e8d094924c8` and the same baseline fingerprint policy.
  A healthy 1.1.8/1.1.9 baseline remains valid; upgrading from 1.1.7 or changing
  filters/settings may require a new preview and reviewed initialisation.
- Extend signed publication to accept stable `vX.Y.Z` tags as well as `-rc.N` tags.
  Stable releases are published without the prerelease marker and become GitHub's
  latest release. Preserve immutable tags, exact version/notes matching,
  monotonically increasing ABI codes, draft asset staging, signing checks and
  checksum/source provenance. Earlier candidates stay immutable.
- Validation before the release commit: independent review; **205 Android tests
  with no failures or skips**; **14 release-script tests**; lint; debug APK build
  and native engine provenance verification. The signed workflow repeats checks
  and verifies the distribution before publication.
- The stable channel is a publishing designation. The recorded fixture, host and
  build verification does not establish every live account/device scenario.
  Android permissions and background limits still require appropriate user setup.

### Necessity audit included in 1.2.0

Reviewed every functional change in 1.1.9 and 1.1.8 against its source, regression
coverage and the earlier complete fork/companion audit. Coverage includes the
32 application fork commits through `8a052dab` and 26 engine fork commits through
`f1d1627e`, plus the companions compared with their exact imported versions. Independent Android and
engine reviews checked the stricter requirement: correct demonstrated defects,
report setup errors clearly and do not add workarounds for platform permissions.

**Removed:** automatic retries after Android rejects foreground-service startup.
The reported scheduled-start rejection does not establish a transient condition;
backoff cannot grant permission or change battery settings. Repeated attempts add
wakeups without a demonstrated benefit. The audited source immediately reports
**needs user action**, starts no native transfer, preserves the saved baseline and
suppresses failure follow-ups. Manual retries remain the user's choice.

| Change reviewed | Decision and evidence |
|---|---|
| Android rejection classification | Keep exact exception/cause classification and actionable status. An OS/setup refusal is not an rclone transfer failure. SDK 33 worker tests preserve ready-state bytes and reject unrelated permission errors separately. |
| Baseline readiness diagnostics | Keep clearer reasons; the approval guards are unchanged. Changed filters/flags/configuration/engine, missing initialisation or an interrupted run legitimately require review. No forced recovery or approval bypass. |
| Proton checksum snapshot consistency | Keep. Signed HTTP fixtures and actual bisync reproduce a hash captured before copying and changed on download open. Resolve consistent authenticated metadata before capture and reject changed snapshots. The user's live checksum cause is still unproven; a successful exclusion is not proof of that cause. |
| Filter/editor changes | Keep reproduced recycled-row, saved-rule parsing, dropdown-identity and draft-loss repairs, plus shared tested argument composition. Flags were already appended during all three bisync stages in 1.1.7; no imaginary blanket missing-flags defect required a special initialisation workaround. |
| Include selection and deletion review | Keep restrictive GUI include semantics and explicit review for older include/delete-excluded tasks. The migration prevents silently widening destination deletion. Expert filter order and literal pattern whitespace remain available. |
| `.nomedia` handling | Keep documented native semantics only: `/.nomedia` is root-only, `**/.nomedia` is nested-only, `.nomedia` matches at every depth. No automatic exclusion, deletion, marker rewriting or checksum bypass. |
| Secret redaction | Keep. Old long/unterminated JSON fixtures expose truncation leakage or Java regex stack overflow; repaired redaction tests pass. |
| Configuration/password writes and restore | Keep readback/durability verification and recovery admission. Failure-injection tests show suppressed failed writes and uncertain marker retirement. The app stops/reports uncertainty rather than concealing storage failures. |
| Newer/older conflict timestamps | Keep the scoped local override: downloads must preserve source times for the existing chronological conflict mode to compare them meaningfully. Other operations retain their policy. |
| Disposable preview cleanup | Keep bounded cleanup only after confirmed native exit. Tests preserve baselines/live previews and reject traversal/symlink escapes. |
| Removed DNS/API/service/duplicate scheduling code | Keep those removals: unused paths had no caller/operational role; Android's system DNS and the actual WorkManager scheduling path remain. |
| Other previously audited fork changes | Keep evidence-backed authentication, listing, integrity, process ownership, scheduling constraints, privacy and signed-build provenance protections. Rechecks found no additional justified rollback candidate. |

Existing waits for a missing network, charging, quiet hours or another live native
operation remain: those are observable conditions that can change. They are
separate from retrying a rejected Android service permission. No exemption,
expedited-job conversion, exact-alarm permission or setup-dependent bypass is added.

Validation after removal: independent review, **205 Android tests with no failures
or skips**, lint, debug APK build/engine verification and release-script tests all
passed. The engine source/pin did not change, so its already-passing backend,
bisync, companion, race and vet checks did not need repeating.

This audit is included in 1.2.0. The published 1.1.9 APK/tag remains immutable;
historical release entries below describe what those releases actually shipped.

## 1.1.9-rc.1 — handle Android background-start restrictions safely

- Handle Android `ForegroundServiceStartNotAllowedException` distinctly from
  transfer errors. Scheduled tasks defer with bounded WorkManager backoff instead
  of immediately recording a failed transfer. Waiting does not send failure
  notifications or queue failure follow-ups, and the temporary notification and
  transfer admission are released. Cancellation still takes priority.
- After the retry budget is exhausted, or if a manual start is denied, report
  **needs user action** with Android battery/background-settings guidance. The
  rejection occurs before native work and before any bisync state transition;
  the existing baseline remains unchanged. This error does not itself require
  resync. Other permission errors and timeouts remain separate failures.
- Distinguish missing initialisation, pending preview approval, changed task/filter/
  configuration/engine fingerprints, interrupted runs and invalid saved baseline
  identities in readiness diagnostics. Keep all existing approval/recovery guards.
  Changing filters on 1.1.7 can invalidate approval independently of a scheduled
  foreground-service rejection.
- Retain the reviewed 1.1.8 fixes. A working marker exclusion does not repair the
  demonstrated listing/download checksum inconsistency, recycled filter-editor
  callbacks, long-secret redaction or configuration-write defects. No engine or
  fingerprint-policy change is introduced by this release, so a healthy 1.1.8
  baseline does not need reinitialisation merely because the app version changed.
- Regression tests inject the real Android exception through a worker foreground
  updater on API 33 and check retry history, terminal user-action reporting and
  byte-for-byte preservation of ready state. A negative permission-error fixture
  checks that unrelated errors are not retried as start restrictions. Readiness
  tests retain the same baseline guards and verify distinct diagnostics.
- Validation passed before committing: independent review, **205 Android host/unit
  tests with no failures or skips**, lint, debug APKs and engine provenance. The
  signed workflow repeats host checks and verifies package,
  version codes, certificate, architectures and distribution checksums.
- **Platform limit:** retries cannot force Android to permit a scheduled foreground
  service. For long unattended transfers, review ProtonBridge's App info → Battery/
  background settings, or open the app and run the task. Allowing unrestricted
  background operation can use more battery. No automatic battery exemption,
  exact-alarm permission, expedited-job conversion or checksum bypass is added.

## 1.1.8-rc.1 — consistent Proton checksums, working filters and fork cleanup

### Proton revision consistency

- Pin rclone to [`f1d1627e`](https://github.com/Rareities/rclone/commit/f1d1627e2e91867b5fed5eb6bb4d4e8d094924c8).
  Resolve authenticated detailed metadata before bisync captures its listing and
  pre-copy result records. Revision history can identify the correct revision
  while supplying attributes that disagree with the subsequent detailed download
  response. Previously, opening that download could change the object checksum
  after bisync had recorded it, causing successful transfers to end with a
  checksum-listing abort, including `.nomedia`.
- Legacy listings select fresh canonical history identity, then use the existing
  query-free detailed revision route. Explicit advertised signed metadata must
  authenticate too; invalid metadata remains an error. Cached detailed metadata
  is bounded, keyed to the canonical revision and reverified against current keys.
  History metadata cannot seed the authoritative cache.
- Downloads verify canonical identity and known size, SHA1 and modification time
  before consuming content or changing the listed object. A changed or inconsistent
  snapshot closes the response and fails safely. Unknown fields remain unknown.
  Checksums, signatures and recovery requirements stay enabled.
- Regressions reproduce the history/detail disagreement with signed fixtures
  whose hashes are computed from actual content. A real bisync fixture reproduces
  the pre-copy/post-open mismatch and verifies corrected Path2 initialisation and
  subsequent sync with a 29-byte marker, an empty marker and unchanged companions.
  This reproduces a mechanism consistent with the reported failure; there is no
  live Proton account trace proving it is the only cause of that particular file.

### Filters, flags and initialisation

- Saved GUI patterns accept conventional signed lines and CRLF, remove accidental
  surrounding spaces/quotes and reject blank, malformed or control-character
  patterns. Task flags accept `--exclude /.nomedia`, quoted values and `--exclude=…`.
  All flags and saved filters are composed through the same builder for **Save and
  preview**, **Review and initialise**, and normal runs. Non-filter tuning flags
  remain present in all three stages.
- Basic exclusions take priority over basic includes; includes now restrict the
  selection with an implicit final exclusion. Pure expert `--filter` rules retain
  their order and pattern whitespace. Mixing a filter reset with saved/basic
  restrictions is rejected. One-way tasks using the old unrestricted include
  behavior with **Delete excluded files** must review and save that setting before
  running, preventing a silently wider deletion scope.
- Recycled filter rows detach old callbacks so editing one row cannot change another.
  Preserve unfinished filter edits across recreation; use one dropdown snapshot
  for displayed names and saved IDs, and reset empty filter lists correctly.
- Editor help explains root `/.nomedia` versus all-depth `.nomedia` patterns.
  Excluding a marker leaves it untouched and does not omit adjacent deck/media
  files; do not exclude entire Anki media folders. Native-engine tests check both
  pattern depths, nested included files, all three stages and retained source markers.
- Bump the baseline fingerprint policy: repeat preview and review initialisation
  for existing tasks. Initialisation is a real `--resync`; review what may be
  overwritten, especially when preferring Proton/Path2. No automatic exclusion,
  deletion, checksum bypass or resync is introduced.

### Audit repairs and removed code

- Redact diagnostic secrets before bounding lines; handle long or unterminated
  JSON secret values without recursive regex stack overflow or truncated leaks.
- Verify installed configuration and wrapped-password bytes after atomic writes,
  synchronize files and directory entries, and retain rollback state on uncertain
  restore-marker retirement. A double failure also blocks native preparation in
  the current process until forced recovery. A committed restore remains successful
  if only obsolete-journal cleanup fails. These barriers cannot guarantee recovery
  after every physical storage failure or a process restart with no writable marker.
- Preserve downloaded timestamps for newer/older conflict policies with a narrowly
  scoped local-backend override; other operations retain existing timestamp behavior.
- Retire disposable preview files only after confirmed native exit, with exact UUID
  paths, flat-file bounds and symlink checks. Persistent baselines are never removed.
- Remove unused revision API shim, obsolete global pure-Go DNS override and app DNS
  formatter, unused Android scheduling service lifecycle/manifest registration, and
  redundant post-restore scheduling reconciliation. Retain the actual scheduling
  adapter, WorkManager dispatcher and useful Internxt authentication/retry changes.

### Scope of the complete fork review

- Reviewed all **30 prior application fork commits** against direct upstream
  [`ece92754`](https://github.com/thies2005/CloudBridge/commit/ece9275441e448ec520a7cbc336c59bfe643b22c):
  374 rename-aware file entries covering production Java/Kotlin, resources, host and
  device tests, workflows, build scripts, branding and documentation. Rename-only
  changes were separated from functional changes; all added production classes
  have actual callers. The existing chronological commit ledger below remains the
  detailed history.
- Reviewed all **25 prior rclone fork commits** against
  [`4f19e9cd`](https://github.com/rclone/rclone/commit/4f19e9cd6f442282d7fa8d5fdbd28ebb68be1131):
  259 file entries including core listing guards, bisync/backend changes, Internxt
  authentication, native builds and test/workflow changes. Compared every local
  runtime delta of the Proton companions against their exact imported revisions:
  bridge `6f7fc6530d4a77d33d08e36464e018600ae4b93d` and API
  `9dcca00ba1dc779a8958c9d4988b79ce2f9f3233`.
- Retained useful bisync approval/recovery, battery-aware scheduling, process
  exclusion, encrypted credentials/configuration, backup recovery, private RC
  access, Proton concurrency/cache limits, signed-release provenance and attribution.
  No speculative cryptographic-format rewrite or broad removal of useful safety
  mechanisms resulted from the review. Disposable fixtures validate local behavior;
  device battery drain and live-account behavior still need installed-app testing.
- Validation completed before the application commit: independent code review;
  engine/companion regressions, race checks and vet; **201 Android host/unit tests
  with no failures or skips**, including real local-engine filter/config tests;
  lint; all four native ABI builds and APK engine/provenance checks. The signed
  publication workflow additionally verifies package, increasing version codes
  and the existing signing certificate before publishing.

## 1.1.7-rc.1 — fix Proton preview revision lookup

- Pin the rclone engine to [`b8c5405e`](https://github.com/Rareities/rclone/commit/b8c5405e236da9f2b3a549ea199adabd0ab5ff0d).
  Fix the new preview request introduced in 1.1.6 that could return Proton HTTP
  422 / Code 2501 before any transfer. Resolve legacy file metadata through the
  existing revision-history route without requesting a block range or block URLs.
- Refresh the unique active revision for every legacy listing, even when the
  folder listing advertises a stale revision ID or authenticated metadata is
  already cached. Only reuse signed metadata under the freshly selected revision
  identity; verify signatures against current keys. Never use an old Link.XAttr
  checksum or conceal an actual history/permission error with cached data.
- Retain the verified revision identity in listed file objects. Downloads check
  that identity against fresh history and stop if it changed, before fetching
  content. Preserve exact detailed-response identity checks, unknown metadata
  semantics, bounded request concurrency and signed-metadata cache limits.
- Regression coverage includes the reported rejected request, empty files,
  revision A changing to B with a stale hint and warm cache, history errors,
  invalid signed attributes and snapshot changes before download. Host engine/
  backend/bisync regressions use disposable fixtures and race checks. Android
  artifacts are built and verified separately; live-account acceptance still
  depends on retrying the installed update.
- After updating, repeat **Save and preview**, then review and initialise the
  failed task if recovery is required. Initialisation performs a real resync and
  can copy/overwrite files according to the selected winner; check the preview.
  No checksum bypass, automatic resync or exclusion of `.nomedia` is introduced.

## 1.1.6-rc.1 — authoritative Proton revision checksums and recovery reports

- Pin the rclone engine to [`8627faf6`](https://github.com/Rareities/rclone/commit/8627faf6c0d8725f4ea8203de325939a874ea841).
  Legacy Proton listings omit active-revision attributes and may expose an older
  checksum in Link.XAttr. Resolve the listed revision's own authenticated metadata
  rather than comparing that older checksum against downloaded content. Explicit
  revision attributes, when present, remain authoritative and must verify.
- Fetch only a minimal revision metadata page with block URLs disabled. Coalesce
  duplicate requests, limit concurrent metadata fetches to four, and reuse signed
  metadata by share/link/revision identity within an 8 MiB payload budget and
  2048-entry cap. No names, directory inventory or active IDs are reused: each
  listing is fresh, and metadata cache hits reverify against current keys.
  Downloads and successful signed commits seed reuse for post-transfer listings.
- Use the fetched revision's signer; reject changed advertised revision IDs,
  mismatched responses and missing/ambiguous active identity. Invalid preferred
  metadata never falls back to older link attributes or a valid cache entry.
- Remove terminal colour escape codes before redacting/displaying diagnostics.
  Checksum-listing aborts retain failure status and explain verification uncertainty,
  comparing the two copies and repeating preview/review/initialisation. No automatic
  force, resync, exclusion or checksum bypass is added.
- Signed legacy-API fixtures reproduce the reported pair of differing checksums
  and cover listing → download metadata → relisting → changed revision → upload
  commit → relisting. Tests cover current-key revalidation, corrupt preferred
  attributes, identity failures, cache bounds, cancellation and concurrent reads.
  Backend/bisync, companion auth/crypto/block, race and static checks passed before
  pushing the completed engine fix. Local SHA1 fixtures initialise differing
  29-byte `.nomedia` files successfully without mutating files during preview.

The first release build was cancelled after a repeated Anki failure exposed
the legacy wire-shape gap. This candidate contains the
completed fix. The actual account response/device is unavailable, so signed
protocol regression coverage does not constitute live-account acceptance. Files
changing during sync can still correctly stop reconciliation; keep Anki closed
when synchronising its database. No full listing-to-download snapshot binding or
silent repair is claimed. Engine changes require fresh preview and explicit approval.

Protocol reference: [Proton SDK legacy revision endpoint and metadata schema](https://github.com/ProtonDriveApps/sdk/blob/8ffae007b0fbb8bcef53514719d6f093bb32dcf9/client/js/src/internal/apiService/driveTypes.ts#L9410).

## 1.1.5-rc.1 — consistent branding and consolidated documentation

- Update the README to describe ProtonBridge throughout, with current download
  names, package/activity identity, build toolchain, bisync controls and attribution.
- Update repository documentation pages, privacy text, contributor instructions
  and issue-template links to this fork. Remove upstream download/store claims.
- Consolidate the added guides, audit/roadmap files and release notes into this
  document. Historical details remain available through immutable Git history.
- Signed releases now extract their notes from the matching version section here;
  missing, empty or duplicate sections stop release validation.
- Retain the existing app identity, signing certificate, engine pin and runtime
  behaviour. This update contains documentation and release tooling changes.

## 1.1.4-rc.1 — plain flag editor and complete rclone reference

- Replace the optional flag builder, performance presets and command display
  with a plain multiline flag editor and one searchable rclone reference.
  Previously saved concrete flags and transfer counts remain intact.
- Accept `--flag value` as well as `--flag=value` and bare switches, one flag
  per line. Normalize each into one argv token without shell interpretation;
  preserve values containing spaces, Unicode and equals signs. Numeric, boolean,
  duplicate, credential and managed-option checks remain in place.
- Support `--conflict-resolve newer` and the bundled engine's other resolution
  modes. Add modtime comparison for newer/older so rclone actually applies the
  chosen policy, while retaining checksum verification and numbered loser copies.
  Flag changes remain part of preview approval fingerprints.
- Load bisync help and all global/backend flag descriptions directly from the
  bundled engine. Reference loading is local, bounded and cancellable; search
  retains complete matching entries, including continuation descriptions.
  The full reference clearly distinguishes engine flags from options managed by
  app controls or the remote editor.

A real local 20-file bisync fixture confirmed that newer content wins on both
sides and older content survives in numbered conflict copies, without `--force`
or relaxing checksum/deletion checks. No live-account/device acceptance test is
claimed. See [release notes](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.4-rc.1.md).

Committed at [`ed232938`](https://github.com/Rareities/ProtonBridge/commit/ed232938441169c37bee15a4a292d81bd95d663a)
and published as [signed v1.1.4-rc.1](https://github.com/Rareities/ProtonBridge/releases/tag/v1.1.4-rc.1).
All 180 current app tests, seven version checks, lint, APK assembly and
instrumentation compilation passed. Signed CI verified all five APKs; public
asset digests and the downloaded ARM64 signature/identity/native payload passed
independent checks. [Verification record](validation/flag-editor-release-audit.json)
includes the real conflict fixture, source/run IDs and all APK digests.


## 1.1.3-rc.1 — battery, background lifecycle and privacy

- Let Android manage Wi-Fi power saving instead of forcing high-performance
  Wi-Fi for every transfer. Use WorkManager's foreground CPU ownership for
  scheduled and file transfers; retain operation-scoped CPU ownership for RC
  document streams/jobs outside WorkManager.
- Block on native process exit, with a one-second coordinator for cancellation
  and deadlines, instead of inspecting exit ten times a second. Preserve
  graceful/forced cancellation and actual-exit admission/lease protection.
- Generate native transfer stats every five seconds, throttle progress
  notifications to five seconds, heartbeat to fifteen seconds, and poll active
  RC jobs every three seconds. Final outcomes still publish without a throttle;
  idle RC polling continues to block and unused daemons retain their idle stop.
- Make the battery-optimization exemption optional during onboarding and
  permission checks. New idle-trigger defaults allow Android to defer wakeups;
  existing saved choices remain unchanged. Optional update checks now require
  battery not low, including already-scheduled periodic work.
- Bound/drain local configuration dumps and optional session probes. Skip
  probes during active native work, budget two minutes plus termination grace,
  cap each probe at thirty seconds, avoid extra list-remotes calls, propagate
  coroutine cancellation and notify only with positive authentication evidence.
  Stop writing raw account names/backend diagnostics into system logs.
- Always drain and own thumbnail-server pipes, cancel listeners on service
  stop, and remove private thumbnail endpoint logging.
- Keep Markdown rendering local: block JavaScript, network subresources,
  file/content access and custom-scheme navigation. Explicit HTTP(S) links open
  externally. Destroy its executor/WebView with the host activities and ignore
  late callbacks.
- Confirm foreground promotion before a file-transfer process starts and make
  its cancellation action available in the first notification.

The engine pin, checksum/cryptographic verification, bisync approval and baseline
recovery policy are unchanged. Battery savings have not been measured on a phone.
See [audit and measurement guidance](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/BATTERY_AND_PRIVACY.md) and
[release notes](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.3-rc.1.md).

Implemented in [`96d0f0d2`](https://github.com/Rareities/ProtonBridge/commit/96d0f0d2f6208da08251b3832d89f844b3040d8a).
[Signed candidate v1.1.3-rc.1](https://github.com/Rareities/ProtonBridge/releases/tag/v1.1.3-rc.1)
passed the signed CI pipeline and all five APK checks. Local validation passed
181 app tests without failures/skips, seven version checks, lint, APK assembly
and instrumentation compilation. Public checksums and the downloaded ARM64
identity/signature/native payload were independently verified. The
[verification record](validation/battery-privacy-release-audit.json) includes
source/run IDs, all APK digests and measurement limits.


## 1.1.2-rc.1 — ProtonBridge identity and listing performance

Pin the reviewed [rclone performance fix](https://github.com/Rareities/rclone/commit/de1b01ca4ca02488ef2942ea6f2f00c0a35dc08e)
and increase versionName/base versionCode to 1.1.2-rc.1 / 120. Fresh Proton
listings reuse the verified folder key for child attributes, avoiding repeated
ancestor fetch/decrypt work per file while keeping authentication, collision
checks and unknown-size handling. Tests include real signed/encrypted data,
fresh second listings, corrupt metadata, cancellation and concurrent scans.

A 20-file localhost fixture showed 42 → 2 ancestor GETs and 2.047 → 0.712 seconds
per scan. This is not a measured Android or live-Proton speedup; small-file
transfer overhead still needs profiling. Backend, bridge/authentication and
bisync race tests passed, the host CLI built, and independent review passed.
The signed pipeline validates Android engines, app tests/lint and APK identity.

Rebrand this personal fork as ProtonBridge, with package/namespace
`io.github.rareities.protonbridge`, new bridge/bisync icons, matching debug identity,
updater/support links and startup/About fork purpose and disclaimer. Preserve
upstream attribution and historical release/audit records. This is a separate
installation from CloudBridge; permissions and task approval must be established
in the new app. The release validator recognizes historical CloudBridge version
properties when checking prior APK version codes.

Application changes are committed at
[`7227ad5d`](https://github.com/Rareities/ProtonBridge/commit/7227ad5d12416e1efda1581704e811b36d7cd474)
(rebrand, icons, docs, engine pin and version) and
[`8c002e3d`](https://github.com/Rareities/ProtonBridge/commit/8c002e3de84e37f5edc07f56dcc22b2f4decdb40)
(explicit fork/upstream maintainer attribution). Engine
[`18a0cc7d`](https://github.com/Rareities/rclone/commit/18a0cc7dd5449cbfa797f2023faf82dc5df5ffe8)
adds CI coverage after the pinned performance source commit without changing its
runtime code.

[Signed v1.1.2-rc.1](https://github.com/Rareities/ProtonBridge/releases/tag/v1.1.2-rc.1)
was published from `8c002e3d`. Local validation passed 176/176 app tests, seven
release-version tests, lint and instrumentation compilation. Signed CI passed
engine/app checks and verified all five APKs. Public asset digests match the
checksum ledger; the downloaded ARM64 APK independently passed package/label,
version, signature, certificate and embedded engine hash/pin/branding checks.

See [release notes](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.2-rc.1.md) for scope and validation limits and
[the verification record](validation/protonbridge-release-audit.json) for run IDs
and APK digests.

## 1.1.1-rc.1 — safety and bisync follow-up


### Signed promotion and repository cleanup

The reviewed application source is merged into `master` at
[`fe23bd52`](https://github.com/Rareities/ProtonBridge/commit/fe23bd52d90e46ca63384db4b174bc50b4a3cc05).
[`461d911d`](https://github.com/Rareities/ProtonBridge/commit/461d911d)
removes temporary debug publishing and the test-branch signing exception;
debug APK builds are now manual-only. No additional app source blocker was
found during the aggregate follow-up review. The pinned engine is unchanged;
rclone's documentation follow-up is merged at
[`63bf067c`](https://github.com/Rareities/rclone/commit/63bf067cab7ceeddd330aaff45b0f8ffa9e51855).

The [signed prerelease](https://github.com/Rareities/ProtonBridge/releases/tag/v1.1.1-rc.1)
contains five APKs with the previous release's package and certificate.
176 host tests, lint, dependency checks, native regression checks and signed
APK verification passed. The downloaded ARM64 APK was independently checked
for its identity, certificate, checksum and native engine hash. The user
reports the preceding debug build works perfectly; an independent device or
live-account matrix was not run.

Obsolete integration/test branches and old Actions artifacts were removed.
Unmerged historical tips have archive tags; existing normal signed releases,
current validation artifacts and active engine-review branches were retained.
The exact audit scope, CI runs and cleanup inventory are recorded in
[promotion-audit.json](validation/promotion-audit.json).


Release candidate following 1.1.0-rc.1. Retains the pinned rclone engine
`47048daf3c80e1de633ec9a1f09981f4c30f9a30`; no engine source patch is needed for
these managed-app fixes.

- Serialize bisync configuration/account validation and actual native launch.
  Remote edits and new RC jobs cannot interleave that preparation interval;
  native ownership continues protecting the running child after launch.
- Persist a local configuration generation before manual edits, reconnects,
  deletes, renames and imports. OAuth account replacement now retires old
  approval, even when only token fields change. Automatic refresh does not.
  The generation is excluded from backups and rollback snapshots.
- Resolve transfer defaults once into a full task snapshot, used by both
  approval fingerprint and command construction. The editor uses the same rule.
- Commit restore rollback files, pending marker and directory entries before
  replacing live components; use SQLite FULL synchronization and installed-file
  barriers. Durably retire the pending marker before deleting rollback snapshots.
- Move to approval policy v4. Existing approvals require a fresh preview and
  reviewed initialization; no automatic resync or baseline deletion is added.
- Bump versionName and ABI-safe base versionCode together to 1.1.1-rc.1 / 110.
  Validate tags, checked-in versions and prior ABI codes before signing.
- Add fork ancestry, project purpose, AI-development disclaimer and the complete
  commit-linked changelog at the top of README without removing original content.

Validation results and remaining device/live-account limits are recorded in
this changelog and the signed release
[notes](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.1-rc.1.md).

### Simpler bisync editor follow-up

- Keep Preview and Review/initialise in the main flow; collapse optional backups,
  access markers, manual flags and helper tools under Advanced settings.
- Explain each helper and rename Proton connection to Proton login help.
- Allow opening a completed, unexpired preview for review without waiting for a
  separate background configuration check. Click-time and worker fingerprint
  checks remain mandatory before any initialization.
- Display queued/running work, pre-preview failures and configuration-check errors
  so disabled actions have an actionable explanation.
- Keep saved options when Advanced is collapsed; extend editor regressions and
  adapt disposable-device actions/screenshots to the collapsible controls.

### Task startup and execution feedback follow-up

- Bound Android foreground-service startup and the local, non-mutating rclone
  flag check to 30 seconds. Timeouts fail with an actionable message; normal
  transfers have no new overall deadline.
- Publish task phases and elapsed time to WorkManager and the editor. A quiet
  engine reports time since its last output instead of leaving “Preparing task”
  indefinitely. Transfers still finish according to the native exit result.
- Preserve specific retry reasons for required network, charging, quiet hours,
  competing native operations and competing transfers.
- Keep progress publication advisory: notification failures cannot abandon a
  native child, bypass cancellation or release its ownership before exit.
- Add real child-process regressions for silent-check deadlines and ordinary
  successful completion with a deadline.

These address confirmed feedback and unbounded-startup problems. The exact
cause on the user's installed APK is not established without device logs.

Validation for the editor/execution follow-up: **176 host tests** passed with
no failures, errors or skips. Lint, all five OSS debug APK builds and instrumentation
Kotlin compilation passed. Independent review passed. All APK native payloads
match the pinned engine. See [validation record](validation/bisync-execution-followup.json).
No device or live-account execution was performed for this follow-up.

### First-run UUID error follow-up

Run now checks baseline readiness before resolving its UUID/path. A new task
shows Preview → review → Initialise guidance instead of “Invalid UUID string”.
Malformed baselines cannot enable Run or advisory preview; explicitly saving a
task repairs a malformed task UUID without reusing the old baseline.

Validation: **172 host tests passed**, lint and all five OSS debug APK builds
passed (using the same verified pinned native engines). Three new regressions
cover first Run, malformed baseline identifiers and explicit task-save repair.
Independent source review passed. Device execution remains unrun.

### Follow-up commits

- [d4521de6c1](https://github.com/Rareities/ProtonBridge/commit/d4521de6c15a86ee85e8f485299ee0ac239fda9c) —
  opt-in, independently reviewed debug-test prerelease publication from the
  isolated test branch, with fail-fast uploads and no release signing secrets.
- [eb41c11444](https://github.com/Rareities/ProtonBridge/commit/eb41c11444ff4b8e2359b94d96ed307ea8bb428e) —
  exact test-branch admission in the signed-release workflow and updated notes.
  The existing environment deployment allowlist remains a required external gate.

- [f59a257740](https://github.com/Rareities/ProtonBridge/commit/f59a2577405c16a4ce3ac93cb1ebdf47a0fcb5f9) —
  simpler bisync editor, actionable running/queued feedback, persisted waiting
  reasons, foreground/local-check deadlines and validated regression coverage.
- [dbbe9ee32f](https://github.com/Rareities/ProtonBridge/commit/dbbe9ee32f912f7c3060ce617d792b3bf84ca4dc) —
  first-run UUID fix documentation and validation.
- [cbcda693b2](https://github.com/Rareities/ProtonBridge/commit/cbcda693b2c924ebc0939fb0099ad02c47d2a28f) —
  personal-fork README introduction and full commit-linked changelog.

- [e8f2a5ad5a](https://github.com/Rareities/ProtonBridge/commit/e8f2a5ad5aae553eca7a5fd5d014d413120d9316) —
  first-run initialization guidance and safe task/baseline UUID validation.

- [f5b8f5fc3e](https://github.com/Rareities/ProtonBridge/commit/f5b8f5fc3e282116ad1ecfd7eabc1e6c6ce247db) —
  configuration/account approval, process admission, durable restore, resolved
  defaults, versioning, regression tests and validation evidence.
- [852525046b](https://github.com/Rareities/rclone/commit/852525046bf0856349eee51b814d11057f2effb5) —
  engine documentation clarifying credential locking and standalone account
  changes. The Android engine pin stays at the previously reviewed source.
- The README and this changelog are maintained in the accompanying documentation
  commits. The reviewed app changes are promoted together to `master`, preserving
  the full commit history. The rclone documentation follow-up is merged into its
  `master`; the Android engine remains pinned to the previously reviewed source.
  Temporary debug-test publication was used to obtain user acceptance and then
  removed. Normal release APKs retain the existing package and release certificate.

### Promotion audit and cleanup

- Re-review of all app changes since 1.1.0-rc.1 found no new concrete blocker.
  The user reported the debug build works correctly on their device; broader
  live-account and physical-device compatibility gates remain as documented.
- Remove the temporary debug-prerelease publisher and exact test-ref allowance
  in release signing. Keep release signing restricted to `master` and its existing
  protected environment. Debug builds become manual to prevent duplicate builds.
- Retire superseded temporary branches and Actions build artifacts after signed
  release success. Preserve prior signed releases for rollback; archive unmerged
  obsolete branch history before removing the branch. Automation review branches
  remain available for future upstream intake.

### Validation status

- **169 host tests / 48 suites** passed with no failures, errors or skips.
- **6 Python release-version regression tests** passed.
- **Lint and all five OSS debug APK builds** passed with the existing lint baseline.
- Instrumentation Kotlin compilation passed, including the new real Android
  directory-sync/SQLite FULL test wired into the disposable-emulator suite.
- Independent source review passed after all findings were corrected.
- All five APK native payloads match the reviewed engine provenance. These builds
  reused the hash-verified published engines; `:rclone:buildAll` was excluded.
- Device execution, live-account switching and physical power-loss acceptance
  were **not run** in this environment. Host tests inject directory barrier
  failures because Robolectric cannot fsync an Android directory descriptor.
- Detailed local results: [validation record](validation/release-safety-followup.json).


### Upstream comparison bases

- CloudBridge: `a6589372cbf86e0b72fb374b500ff81ac87cc7a6` against
  thies2005/CloudBridge `ece9275441e448ec520a7cbc336c59bfe643b22c`.
- Engine: `47048daf3c80e1de633ec9a1f09981f4c30f9a30` against
  rclone/rclone `4f19e9cd6f442282d7fa8d5fdbd28ebb68be1131`.
- The accompanying engine documentation follow-up clarifies process-shared
  credential persistence and the standalone CLI account-change limitation.
  This is documentation only; the app keeps the reviewed engine pin.

## Published 1.1.0-rc.1 and engine 1.76.0-cloudbridge.1

### CloudBridge

## Bisync and task editing

- Complete two-way setup: persistent UUID baselines, preview, explicit approval,
  initial precedence, conflict preservation, percentage deletion limits,
  backup directories and optional access markers.
- Ready-task previews use a private snapshot and preserve the healthy baseline.
  View timestamped affected-file reports, private history and effective arguments.
- Bind approval and queued work to settings, endpoints, filters, configuration
  identity and the exact engine. Changed or uncertain state requires review;
  never silently add resync, force, listing deletion or trash cleanup.
- Preserve newly saved identity across rotation and unavailable remote selection.
  Resolve configured alias/crypt/cache wrappers correctly when executing tasks.
- Validate custom flags against the bundled engine and use individual argv
  elements. Reject positional commands, credentials and overrides of managed
  safety, state, TLS, listeners or diagnostic controls.
  Connection destinations, authority headers and CA/client certificates belong
  in the remote editor; task flags cannot redirect stored credentials.
- Add Balanced, Low memory and Limited bandwidth templates. Copy settings into
  the task while preserving filters and safety choices. Reject connection caps
  that can deadlock the selected transfers/checkers. No measured energy-saving
  claim is made for a template.
- Add explicit Proton reconnect/verification with transient one-time codes and
  persistent, debounced account pauses. Reconnect does not repair a baseline.
- Run remote creation and editing outside Android's main thread. Editing
  submits one configuration operation; failed or cancelled saves keep an honest
  failure result. Bound authentication steps, response size and process lifetime,
  and drain both native output pipes during setup and OAuth authorisation.
- Restore the remote editor and provider picker safely after rotation without
  placing credential values in Android's saved-state bundle. Unsaved credential
  edits are discarded and an existing remote is reloaded from private storage.
- Preserve queued button taps during task-state refresh. Viewing a completed
  preview or running an eligible task no longer loses the tap when the screen
  recomputes the same enabled state.

## Scheduling and execution

- Timezone/DST-aware daily, weekday and interval schedules, next-run display,
  quiet hours, network/charging/battery constraints and missed-run coalescing.
- Unique admission, saved-revision checks and bounded follow-up chains prevent
  overlap, cycles and running a queued request against newly edited locations.
- Nonzero exit, cancellation, failed streams and uncertain completion cannot
  become success or trigger success follow-ups.
- Retain native ownership until process exit and cleanup. Fix startup-permit
  leaks, competing RC callbacks and early restart. Retire idle RC after its
  grace period; periodic health checks remain opt-in and disabled by default.
- Exercise all six existing copy/sync modes with the real WorkManager and
  bundled executable, including destination-only-file semantics.

## Security and privacy

- Keep native encrypted rclone configuration with a Keystore-wrapped password,
  bounded staging, parse-first restore and journalled crash rollback.
- Encrypt all new backup contents with standard AES-256 ZIP entries, including
  task names, paths, preferences and proxy credentials. Only fixed component
  names and sizes remain visible; rclone.conf is separately encrypted too.
- Retain legacy archive import. Old proxy-enabled archives without a protected
  password require explicit proxy reconfiguration, avoiding leakage of an old
  credential to a restored proxy host or silent direct-network fallback.
- Move proxy passwords out of plaintext preferences into authenticated Android
  Keystore storage. Failed unlock/migration stops affected work; key loss needs
  explicit re-entry rather than plaintext downgrade.
- Reject document-provider parent/dot traversal, malformed root IDs and unsafe
  rename targets. Remove grant-all behaviour that could expand a reader's URI
  permission to write permission. Remove unused unbounded ZIP extractors.
- Bound and batch diagnostic writes, retire the writer when idle, and retain
  redaction, private history, restricted notifications and scoped shortcuts.
- Remove raw authentication responses, malformed JSON and OAuth URL buffers from
  legacy setup diagnostics. Report operation outcomes without retaining secrets
  supplied by a backend in an unexpected error response.
- Keep authenticated loopback RC and require authenticated TLS for supported
  LAN serving. Validate authentication, traversal and untrusted certificates.

## Engine, upstream and build

- Pin to reviewed Rareities/rclone master from the matching
  `v1.76.0-cloudbridge.1` candidate. Packaged provenance records the exact SHA,
  module/toolchain versions and each ABI hash; no moving branch selection.
  The engine source is `47048daf3c80e1de633ec9a1f09981f4c30f9a30`.
- Include atomic Proton credential persistence, authenticated modern-name
  lookup, strict metadata/checksums, Unicode ambiguity rejection, baseline
  identity locks, reader/upload fixes and existing Android/Internxt patches.
  Managed app jobs prohibit unknown-draft replacement. The standalone engine
  retains its documented explicit replacement option, disabled by default;
  enabling it manually does not establish ownership of another client's draft.
- Incorporate current reviewed upstream changes and useful alternate-branch
  work without replacing the tested lifecycle or database architecture.
- Build with Go 1.27.2, NDK 29 and API 23 as Android PIE executables with 16 KiB
  ELF load alignment. Use bionic system DNS instead of raw DNS-server addresses
  that could bypass Private DNS. OEM/VPN/custom trust testing remains external.

## Signing and installation

Dedicated fork signing certificate SHA-256:

`c1b947a38264f1d65f7986c0b65f649ce882fc5232094726777067050dac9bcd`

Historical 1.1.0 release ID: `de.schuelken.cloudbridge`. Historical debug ID:
`de.schuelken.cloudbridge.debug`. Android will not update an application signed
by somebody else's certificate with this fork key. Export and verify a backup
before any deliberate replacement; do not uninstall merely to bypass signing
protection. The key is private, with protected master-only CI signing.

Test/build runners do not receive publication permission. A separate publisher
uploads verified non-debuggable APKs and checksums. Missing or mismatched signing
prerequisites fail the build. `SHA256SUMS.txt` covers the published assets.
The older duplicate automatic-beta publisher is retired in favour of this one
explicit, verified release workflow.

## Acceptance and remaining gates

See [docs/RELEASE_PLAN_COVERAGE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/RELEASE_PLAN_COVERAGE.md), [docs/INTEGRATION_ACCEPTANCE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/INTEGRATION_ACCEPTANCE.md) and the
release workflow evidence. Verification covers native and GUI bisync, all
ordinary task modes, WorkManager charging/cancellation, RC/provider ownership,
force-stop/reopen, encrypted rollback, hostile archives and secret canaries.

No personal Proton credentials were used. Live disposable-account official-client
round trips, actual long-lived refresh/revocation, account/crypto migration,
physical Android/API/ABI/16 KiB hardware, genuine foreground quotas and controlled
battery measurements remain external gates. Photos, Shared With Me, instant
events, desktop and biometric-per-run unlocking are deferred. This candidate is
not a universal security, service-compatibility or battery-efficiency guarantee.

### rclone

## Proton Drive

- Bind auth callbacks to the owning configuration, credential generation, UID
  and mailbox-key identity. Stale callbacks cannot overwrite a newer session.
- Adopt compatible rotated credentials before refresh and after rejection;
  preserve credentials through transient failures and losing refresh races.
- Persist the entire UID/access-token/refresh-token/salted-key tuple with one
  configuration save under a process-shared lock. Reload while locked and reject
  stale generations, changed configuration paths and conflicting credential
  overrides. Persistence failures stop the operation instead of disappearing
  inside a callback. Existing native configuration encryption is retained.
- Preserve original configuration-section identity through generated backend
  override suffixes. Configured aliases and policy overrides do not invent a new
  credential destination.
- Contain the reviewed Proton API source and licence, including same-UID checks,
  spent-token rejection, bounded late-winner adoption, correct callback lock
  ordering and negative crypto tests. No signature bypass or custom SRP/crypto.
- Resolve authenticated decrypted names when modern official-client objects
  are absent from legacy hash lookup, including upload-conflict lookup. Reject
  ambiguous names and incomplete directory metadata.
- Preserve plaintext size/hash validation, download Close propagation, upload
  cancellation, consumed-reader non-replay, timestamp handling and honest app
  identification. Never replace unfamiliar drafts or empty account trash as a
  recovery shortcut.

## Bisync integrity

- Opt-in `--require-checksum` rejects absent common/per-file checksums, unknown
  plaintext sizes and damaged, truncated or duplicated listing rows before
  reconciliation. Strict preflight also rejects ambiguous normalised filenames
  during the existing traversal, without adding a cloud listing pass.
- Lock both the unordered endpoint pair and its actual listing basename, also
  for previews. Reversed paths and legacy filename collisions cannot bypass
  admission or reuse another ordered pair's baseline.
- Bind legacy-compatible listing names to a versioned ordered endpoint identity.
  Only successful explicit initialisation establishes that identity. Preserve
  ownership-safe lock renewal/removal, conflicts and deletion thresholds.
- Retain all upstream Proton/bisync fixes already present, required Android DNS
  support and Internxt compatibility. Integrate the current official master,
  including its additional release-tooling/documentation changes.

## Migration

Older **unbound** bisync listings require explicitly reviewed `--resync`. Confirm
both roots, precedence and backups first. CloudBridge instead offers a new
private baseline after its own preview/approval flow. No automatic resync, force,
listing deletion or trash purge is introduced.

The credential guard coordinates updated processes sharing the same config.
Separate config files and older binaries are not coordinated. Avoid editing
the same configuration with an older external client during a refresh.

## Distribution and verification

Built with **Go 1.27.2 or newer**, including its October 2026 security fixes.
Windows amd64, Linux amd64/arm64 and macOS amd64/arm64 archives include licence,
changelog, exact source SHA, module/toolchain metadata and binary hashes.
`SHA256SUMS.txt` covers published assets. Android executables are built separately
by ProtonBridge with NDK 29, bionic system DNS, PIE and 16 KiB load alignment.

The release workflow runs backend/config/march/bisync regressions, race/static
checks, negative crypto and fake-server tests, and disposable CLI acceptance.
Coverage includes competing OS-process credential writes, failed saves, name
ambiguity, both-direction changes, deletion/modification conflicts, missing roots
and markers, thresholds and forced interruption. Windows cannot execute the
upstream privileged symlink fixtures on this build host; the unchanged fixtures
run on Linux CI instead. Test/build runners have read-only repository permissions;
publication runs separately and does not execute candidate code with write access.

**External gates:** live disposable-Proton official-client round trips, actual
long-lived refresh/revocation and account/crypto-migration matrices. No personal
credentials were used. Photos, Shared With Me, instant events and universal
service compatibility are not claimed. Pair this with a ProtonBridge build whose
provenance pins this release's exact engine source.

## Exhaustive fork commit ledger

Authors below are git metadata. Merge rows represent integration, not an extra independent feature implementation. Official upstream release-tooling commits d03990188 and 4f19e9cd6 are upstream intake, not credited as fork inventions.

### ProtonBridge

| Commit | Author | Change |
|---|---|---|
| [fdf65556f5](https://github.com/Rareities/ProtonBridge/commit/fdf65556f596da8798cb1566a3a2e8398b552600) | ChatGPT | feat: add guarded Proton bisync workflows and task scheduling |
| [e24703f3e0](https://github.com/Rareities/ProtonBridge/commit/e24703f3e04e4d07dec861d6c9e6111fe1b7c313) | ChatGPT | ci: install supported Android SDK packages |
| [39251088ce](https://github.com/Rareities/ProtonBridge/commit/39251088ce172cee10c9b83a3c25fcd2e66545c3) | ChatGPT | fix: validate native bisync lifecycle and complete task workflows |
| [ab161aa76c](https://github.com/Rareities/ProtonBridge/commit/ab161aa76cca548dcda728324d899b389f3ba4e1) | ChatGPT | fix: harden privacy boundaries and ship a signed bisync release candidate |
| [a50c7a0784](https://github.com/Rareities/ProtonBridge/commit/a50c7a078448d2edfdd80989e46a8c847c7f0327) | ChatGPT | test: validate signed release without test-only shrunk helper calls |
| [e18a057f1c](https://github.com/Rareities/ProtonBridge/commit/e18a057f1c925510af6dd08d90f697a5ed9307b0) | ChatGPT | fix: complete remote setup lifecycle and preserve queued task actions |
| [a6589372cb](https://github.com/Rareities/ProtonBridge/commit/a6589372cbf86e0b72fb374b500ff81ac87cc7a6) | Rareities | Merge verified Proton bisync, privacy and signed release candidate |
| [f5b8f5fc3e](https://github.com/Rareities/ProtonBridge/commit/f5b8f5fc3e282116ad1ecfd7eabc1e6c6ce247db) | ChatGPT | fix: bind approvals to configuration changes and durable restore state |
| [cbcda693b2](https://github.com/Rareities/ProtonBridge/commit/cbcda693b2c924ebc0939fb0099ad02c47d2a28f) | ChatGPT | docs: explain personal fork and record full commit-linked changelog |
| [e8f2a5ad5a](https://github.com/Rareities/ProtonBridge/commit/e8f2a5ad5aae553eca7a5fd5d014d413120d9316) | ChatGPT | fix: explain missing bisync initialization before resolving baseline UUID |
| [dbbe9ee32f](https://github.com/Rareities/ProtonBridge/commit/dbbe9ee32f912f7c3060ce617d792b3bf84ca4dc) | ChatGPT | docs: record first-run bisync UUID fix and validation |
| [f59a257740](https://github.com/Rareities/ProtonBridge/commit/f59a2577405c16a4ce3ac93cb1ebdf47a0fcb5f9) | Mahant | fix: simplify bisync setup and report task startup and waiting phases |
| [307e8772f8](https://github.com/Rareities/ProtonBridge/commit/307e8772f8c3574cf9374410f2160f47a0328c1b) | Mahant | docs: link bisync execution fixes and verification in fork changelog |
| [eb41c11444](https://github.com/Rareities/ProtonBridge/commit/eb41c11444ff4b8e2359b94d96ed307ea8bb428e) | Mahant | ci: allow isolated bisync test prerelease on reviewed branch |
| [d4521de6c1](https://github.com/Rareities/ProtonBridge/commit/d4521de6c15a86ee85e8f485299ee0ac239fda9c) | Mahant | ci: publish verified debug APK prereleases from isolated test branch |
| [42988641a5](https://github.com/Rareities/ProtonBridge/commit/42988641a5848c4560c6aedb85be1a1859849349) | Mahant | docs: record test branches and prerelease distribution gates |
| [461d911d63](https://github.com/Rareities/ProtonBridge/commit/461d911d63eef19a60449931ff9a2e8686393ee9) | Mahant | chore: retire temporary test publishing and prepare signed promotion |
| [fe23bd52d9](https://github.com/Rareities/ProtonBridge/commit/fe23bd52d90e46ca63384db4b174bc50b4a3cc05) | Mahant | Merge audited bisync fixes, fork documentation and release cleanup |
| [ad272aaa52](https://github.com/Rareities/ProtonBridge/commit/ad272aaa5254c80799552a27adcbc57a6e5467e1) | Mahant | docs: record signed promotion audit and repository cleanup |
| [7227ad5d12](https://github.com/Rareities/ProtonBridge/commit/7227ad5d12416e1efda1581704e811b36d7cd474) | Mahant | feat: rebrand fork as ProtonBridge and improve Proton listings |
| [8c002e3de8](https://github.com/Rareities/ProtonBridge/commit/8c002e3de84e37f5edc07f56dcc22b2f4decdb40) | Mahant | docs: clarify ProtonBridge and upstream maintainer attribution |
| [62a282181f](https://github.com/Rareities/ProtonBridge/commit/62a282181ff6d1c6ebcf19243e2fc25b44f2b89a) | Mahant | docs: record signed ProtonBridge release verification |
| [96d0f0d2f6](https://github.com/Rareities/ProtonBridge/commit/96d0f0d2f6208da08251b3832d89f844b3040d8a) | Mahant | perf: reduce battery overhead and harden background privacy |
| [e1595bcd07](https://github.com/Rareities/ProtonBridge/commit/e1595bcd07c468a134829d7608c8df1c61d9dd19) | Mahant | docs: record battery and privacy release verification |
| [ed23293844](https://github.com/Rareities/ProtonBridge/commit/ed232938441169c37bee15a4a292d81bd95d663a) | Mahant | fix: simplify flag editing and support conflict resolution |
| [c0113618d1](https://github.com/Rareities/ProtonBridge/commit/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d) | Mahant | docs: record flag editor release verification |
| [28b96c9701](https://github.com/Rareities/ProtonBridge/commit/28b96c9701566c2563bd79ad5ee959425a9e6a46) | Mahant | docs: consolidate ProtonBridge documentation and release notes |
| [76b711c035](https://github.com/Rareities/ProtonBridge/commit/76b711c035944afa7b8677172d907ed545df2270) | Mahant | fix: pin consistent Proton revision metadata and explain checksum failures |
| [5047b4c387](https://github.com/Rareities/ProtonBridge/commit/5047b4c387f1dd185bb2790f58e97c052493bcce) | Mahant | fix: pin verified legacy Proton revision metadata resolution |
| [691ebb7b1c](https://github.com/Rareities/ProtonBridge/commit/691ebb7b1c8bb9fec2ccb32d481042eff6cc9ba5) | Mahant | fix: update Proton revision lookup for preview and downloads |
| [42b292ae4f](https://github.com/Rareities/ProtonBridge/commit/42b292ae4ff520ae6838e684457a9f72beba42d9) | Mahant | fix: stabilize Proton bisync metadata and apply filters consistently |

### rclone

| Commit | Author | Change |
|---|---|---|
| [4102e56edc](https://github.com/Rareities/rclone/commit/4102e56edc0c7a43938ab4d025312922e8f796a8) | Rareities | ci: verify Proton Drive and bisync changes in fork |
| [8af9adf738](https://github.com/Rareities/rclone/commit/8af9adf73816aebb26a81409611b24428fa3958e) | Rareities | chore: add selective official and OSS candidate preparation |
| [397ef12001](https://github.com/Rareities/rclone/commit/397ef12001c0a478147acc509f65b653cbd78dd5) | Rareities | ci: check official and OSS sources daily and propose review-only PRs |
| [e42c8ec0a2](https://github.com/Rareities/rclone/commit/e42c8ec0a2e0870d5556f0c57fdd073f51895f06) | Rareities | ci: fetch existing candidate ref before leased updates |
| [aaa9fb2028](https://github.com/Rareities/rclone/commit/aaa9fb2028fcd1f6bb0a94d229a6db5266915a91) | Rareities | docs: describe official and Forgejo Proton source review |
| [3a5758eeaa](https://github.com/Rareities/rclone/commit/3a5758eeaa7250635f756f56c84128445d33ebe2) | Rareities | Merge pull request #1 from Rareities/setup/dual-upstream-review |
| [789cfc5c4f](https://github.com/Rareities/rclone/commit/789cfc5c4fde57ad301485a809e23a4aa84e5e31) | ChatGPT | protondrive: harden sessions and metadata for safe Android bisync |
| [5b1b5c0dcc](https://github.com/Rareities/rclone/commit/5b1b5c0dccfea83712b574a3fefa9d9916d45655) | ChatGPT | ci: isolate upstream candidate tests from publication credentials |
| [2e8c114a56](https://github.com/Rareities/rclone/commit/2e8c114a56dca65cdc5ab5570362864a9b0f2a56) | ChatGPT | fix: guard Proton listings and bisync process boundaries |
| [30c9c95089](https://github.com/Rareities/rclone/commit/30c9c95089eefb893cde2a5446de78d69619bc53) | ChatGPT | test: exercise disposable bisync lifecycle in native CI |
| [14260f9712](https://github.com/Rareities/rclone/commit/14260f97124267bb789eab2ae06e4d9259cee1bb) | ChatGPT | test: remove only the owned hidden access sentinel portably |
| [719ed66b9e](https://github.com/Rareities/rclone/commit/719ed66b9e7e6a76b9bfe3fe1ae194f89ea5ccc9) | ChatGPT | bisync: bind legacy listings to verified endpoint pairs |
| [1cbc4186a6](https://github.com/Rareities/rclone/commit/1cbc4186a67a205de7b323280cffe9245d60fdf7) | ChatGPT | test: fix timestamps in access-marker fixture |
| [c935845b1a](https://github.com/Rareities/rclone/commit/c935845b1a71686f9841500141ab84ce224c77e9) | ChatGPT | test: cover opposing bisync deletions and modifications |
| [6b9a27b7a6](https://github.com/Rareities/rclone/commit/6b9a27b7a6530621bbbcadad9c4a88c52c777b37) | ChatGPT | fix: persist Proton sessions atomically and prepare verified fork release |
| [76f218c559](https://github.com/Rareities/rclone/commit/76f218c559a5be7c423eba7a381e71df260c7b89) | ChatGPT | Merge reviewed official rclone master release tooling |
| [6a9f0fc48c](https://github.com/Rareities/rclone/commit/6a9f0fc48ca23660022fe24615bf5e988710d418) | ChatGPT | build: check known vulnerabilities and preserve executable archive modes |
| [47048daf3c](https://github.com/Rareities/rclone/commit/47048daf3c80e1de633ec9a1f09981f4c30f9a30) | Rareities | Merge reviewed Proton bisync and verified fork release |
| [852525046b](https://github.com/Rareities/rclone/commit/852525046bf0856349eee51b814d11057f2effb5) | ChatGPT | docs: clarify Proton persistence and account-change baseline limits |
| [63bf067cab](https://github.com/Rareities/rclone/commit/63bf067cab7ceeddd330aaff45b0f8ffa9e51855) | Mahant | Merge reviewed Proton persistence and baseline-limit documentation |
| [de1b01ca4c](https://github.com/Rareities/rclone/commit/de1b01ca4ca02488ef2942ea6f2f00c0a35dc08e) | Mahant | protondrive: reuse verified parent keys in fresh listings |
| [18a0cc7dd5](https://github.com/Rareities/rclone/commit/18a0cc7dd5449cbfa797f2023faf82dc5df5ffe8) | Mahant | ci: exercise fresh Proton listing regression and race checks |
| [3eed9a27ea](https://github.com/Rareities/rclone/commit/3eed9a27eaeafafc4974d4480c84d0af0a580482) | Mahant | protondrive: keep authenticated revision metadata consistent |
| [8627faf6c0](https://github.com/Rareities/rclone/commit/8627faf6c0d8725f4ea8203de325939a874ea841) | Mahant | protondrive: resolve legacy listings from authenticated revision metadata |
| [b8c5405e23](https://github.com/Rareities/rclone/commit/b8c5405e236da9f2b3a549ea199adabd0ab5ff0d) | Mahant | protondrive: fix legacy preview metadata lookup and stale revision hints |
| [f1d1627e2e](https://github.com/Rareities/rclone/commit/f1d1627e2e91867b5fed5eb6bb4d4e8d094924c8) | Mahant | protondrive: keep resync metadata stable before copying and retire obsolete DNS override |


## Current usage and operating limits

### Bisync and flags

Use **Resync → confirm settings → Run** with independent backups.
Resync performs real transfers after confirmation. Review initial winning side,
ordered endpoints, filters, optional backups and the deletion percentage (default
10 percent). Backup folders must be outside synced roots. Confirmation is bound
to settings, configuration identity and the engine pin when the job starts.
Missing/interrupted state requires explicit recovery; there is no automatic force
or resync repair.

Managed bisync requires size and common checksums. Missing metadata or damaged
baseline rows stop reconciliation. Conflicts default to preserving both contents;
`--conflict-resolve newer` or `older` additionally compares modification times,
which must be meaningful for your files. Numbered losing copies are retained.

The editor accepts one `--flag`, `--flag value` or `--flag=value` per line. Spaces,
Unicode and equals signs in values are preserved without shell interpretation.
Repeatable filters are allowed, duplicate scalar flags are rejected. Task-owned
state, endpoints, credentials, listeners, executable hooks and safety controls
cannot be overridden. The searchable reference contains the bundled bisync,
global and backend flag descriptions. It is local help, not a cloud connection.
The Proton connection control checks the configured connection; it does not
initialise a bisync baseline.

### Scheduling and battery

Schedules support network, charging, battery-not-low and quiet-hour constraints.
Intervals have a 15-minute minimum; missed runs coalesce. Android Doze, quotas,
force-stop and permissions may delay starts. Manual runs are explicit requests.
Existing idle-wakeup preferences are preserved. For lower idle drain disable
General settings → Allow tasks to run when the device is idle, and use Android's
Optimized battery setting if deferred starts are acceptable. The app cannot
revoke a previously granted battery exemption itself.

If Android rejects a scheduled foreground-service start, no transfer has begun
and that attempt does not alter the bisync baseline. The audited source reports user action immediately without automatically retrying
that permission rejection. Open ProtonBridge to retry, or review
Android App info → Battery/background settings for unattended sync. An
unrestricted setting may increase battery use; the app does not change it for you.
This startup restriction is separate from the requirement to review changed flags
or recover a genuinely interrupted bisync.

Android manages Wi-Fi power saving; WorkManager owns worker CPU locks. Active
RC streams retain operation-scoped CPU protection. Stats/notifications update
every five seconds, heartbeat every fifteen, active RC polling every three.
Optional update checks require battery not low; optional connection probes are
bounded and cancellable. Encryption/scans/transfers still consume power. No
physical-device battery saving percentage or live-account acceptance is claimed.

### Credentials, backups and privacy

The live rclone configuration uses native rclone encryption in private storage.
Its random password is wrapped using Android Keystore AES-GCM. Background access
is unattended; there is no mandatory biometric prompt per sync. Active native
children receive the password in their environment, not command arguments.
A compromised running app, rooted device or broken OS can defeat these boundaries.

Configuration migration/import/restore uses staged validation and rollback
journals. Protected backup exports contain secrets and need careful handling;
keep their passphrase safe. Android automatic backup is disabled. Exported task
requests require a valid shortcut capability or confirmation. RC/WebDAV services
are local and authenticated. Logs can contain filenames:
redact them before sharing. Markdown rendering blocks scripts and subresources;
explicit web links open externally. Provider and optional GitHub requests expose
ordinary network metadata to those services; there is no app analytics or ad SDK.

### Build and release

Use the exact Go/NDK versions in `gradle.properties`, JDK 17 and Android SDK 36.
The native `librclone.so` is a PIE executable launched as a child process, not a
JNI shared library. Builds use CGO/bionic DNS and 16 KiB ELF load alignment.
On Windows use the Gradle wrapper and Android NDK, not a desktop MinGW compiler.

Debug builds append `.debug`. Release signing uses configured keystore/CI secrets;
never commit signing material. Version names and base codes are checked in
`gradle.properties`; base codes end in zero and exceed every prior split APK.
The explicit master-only `fork-release.yml` workflow validates this version's
section, runs tests/lint, signs five APKs and verifies package/certificate/native
provenance before publication. It does not publish to Google Play.

### Evidence and archived detail

Current validation records are kept as machine-readable JSON under `validation/`.
Host fixtures and compilation do not prove live Proton/Android acceptance or
physical-device energy improvements. Commit-linked change history above records
actual work; older plans are historical proposals rather than proof of completion.
The following superseded files are consolidated here and retained in Git history:

- [AUDIT_AND_REMEDIATION_PLAN.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/AUDIT_AND_REMEDIATION_PLAN.md)
- [BUILD_GUIDE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/BUILD_GUIDE.md)
- [WINDOWS_BUILD_GUIDE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/WINDOWS_BUILD_GUIDE.md)
- [docs/ADR-001-credential-storage-at-rest.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/ADR-001-credential-storage-at-rest.md)
- [docs/ADR-002-native-rclone-config.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/ADR-002-native-rclone-config.md)
- [docs/BATTERY_AND_PRIVACY.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/BATTERY_AND_PRIVACY.md)
- [docs/BISYNC_GUIDE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/BISYNC_GUIDE.md)
- [docs/BISYNC_VALIDATION.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/BISYNC_VALIDATION.md)
- [docs/INTEGRATION_ACCEPTANCE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/INTEGRATION_ACCEPTANCE.md)
- [docs/INTEGRATION_RESULTS.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/INTEGRATION_RESULTS.md)
- [docs/RELEASE_PLAN_COVERAGE.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/RELEASE_PLAN_COVERAGE.md)
- [docs/releases/v1.1.0-rc.1-validation.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.0-rc.1-validation.md)
- [docs/releases/v1.1.0-rc.1.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.0-rc.1.md)
- [docs/releases/v1.1.1-rc.1.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.1-rc.1.md)
- [docs/releases/v1.1.2-rc.1.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.2-rc.1.md)
- [docs/releases/v1.1.3-rc.1.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.3-rc.1.md)
- [docs/releases/v1.1.4-rc.1.md](https://github.com/Rareities/ProtonBridge/blob/c0113618d1c3d5cc7b0e054ecf323bc7cc58011d/docs/releases/v1.1.4-rc.1.md)
