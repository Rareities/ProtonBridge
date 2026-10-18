# ProtonBridge 1.2.1

Bisync uses direct Resync confirmation; mandatory dry runs and saved previews
are removed. Native --recover and --resilient flags are supported for a matching
existing baseline. They do not require Resync merely because they were added.

Updated the pinned rclone fork with upstream bisync, filter, VFS and backend fixes.
The changed engine requires one new Resync. Reduced duplicate RC notifications
and unnecessary optional session probes. Integrated missing upstream security fixes.
Device battery savings have not been measured.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.2.0

Full stable release of the audited ProtonBridge source, with the existing
package and signing certificate.

Removed automatic retries after Android refuses a foreground-service start.
The task now immediately asks for user action, preserves the baseline and avoids
failure follow-ups. Permission/setup restrictions require a user decision.

Retained the verified checksum, filter/editor, privacy, configuration-write and
timestamp fixes. Existing approval/recovery guards and native glob semantics are
unchanged. Published 1.1.9 assets remain immutable.

[Audit decisions](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.9-rc.1

Scheduled foreground-service startup restrictions now defer with limited retries
and clear Android battery/background guidance. A blocked start preserves the
existing bisync baseline and does not trigger failure follow-ups or require resync.
Android may still require you to permit background operation in App info.

Readiness messages now distinguish changed settings from incomplete initialisation,
interrupted runs and invalid saved baseline identities. The engine and baseline
policy are unchanged from 1.1.8; its verified fixes are retained.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.8-rc.1

Fixed Proton listing/download metadata disagreement that could abort bisync after
successful copies. Detailed signed metadata is captured before copying; downloads
stop safely if the snapshot changes. Checksum verification remains enabled.

Fixed saved filters, quoted exclusions, include selection, recycled editor rows
and filter IDs. The same filters and flags apply during preview, initialisation
and normal sync. Use .nomedia to exclude markers at every depth.

Reviewed the complete fork, removed unused DNS/API/service code, repaired long
secret redaction and configuration write verification, and cleaned disposable
previews safely. Download timestamps are preserved for newer/older conflicts.

Repeat Save and preview, then Review and initialise. Initialisation performs a
real resync: review the selected winner and files that may be overwritten.

[Full changelog and audit](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.7-rc.1

Fixed the new Proton revision request that could fail preview with HTTP 422 /
Code 2501. Legacy listings use fresh revision history and authenticated metadata,
including empty files. Stale listing hints or warm caches cannot select an older
checksum; downloads stop if the verified revision changes before opening.

Repeat Save and preview, then review and initialise if recovery is required.
Initialisation is a real resync: check the preview and selected winner first.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.6-rc.1

Fixed legacy Proton listings using an older link checksum than the downloaded
revision. Identified, authenticated revision metadata is fetched and reused with
bounded memory and request concurrency; fresh directory listings are preserved.
Changed or invalid revision identities stop safely before download. Checksum
failures now include recovery guidance, and terminal colour codes are removed.
Preview and review existing bisync tasks again after this engine update.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.5-rc.1

Updated ProtonBridge README, website/privacy and contributor documentation.
Consolidated project guides and release notes into the full changelog.
App behaviour and the reviewed engine pin remain unchanged.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.4-rc.1

Plain rclone flag editor: one --flag value or --flag=value per line. Supports
--conflict-resolve newer with the required modification-time comparison while
preserving checksum checks and losing conflict copies. Removed builder, presets
and command-display buttons. The searchable reference now includes bundled
bisync, global and backend flags with descriptions.

[Full changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.3-rc.1

Battery and background lifecycle optimizations: Android Wi-Fi power saving,
less progress/polling work, optional battery exemption, deferrable new schedules,
and bounded/cancellable optional health checks. Private diagnostic logging and
Markdown content are hardened; thumbnail listeners drain and stop reliably.
Existing schedule choices and bisync safety checks remain intact.

[Full fork changelog](https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md)

---

# ProtonBridge 1.1.2-rc.1

- New ProtonBridge name, icon and separate package identity.
- Startup/About now explain this personal Proton Drive bisync fork, its upstream
  ancestry and AI-development disclaimer.
- Reviewed rclone listing fix reduces repeated ancestor fetching/decryption.
  Host fixture results are not a measured device throughput guarantee.
- New application ID means a separate installation. Import configuration if
  needed, grant permissions and review/initialize bisync tasks. Stop equivalent
  old schedules before enabling new ones.

Full fork changes and commit history:
https://github.com/Rareities/ProtonBridge/blob/master/docs/FORK_CHANGELOG.md

---

## Historical upstream changelog

### 1.12.0
* **New:** Support for accessing cloud files from other apps (SAF DocumentProvider)
* **New:** Support for all DocumentProviders
* **New:** Support for translations, with German as test language
* **Fix:** Crash after refreshing directory
* **Fix:** Crash when deleting remote
* **Fix:** Crash when lifecycle event occurs while creating link
* **Fix:** Crash when setting up local remote
* **Fix:** Crash when sharing file to RCX and cancelling immediately
* **Fix:** Crash when sharing file to RCX with missing permissions
* **Fix:** Crash when sharing file to RCX without name
* **Fix:** Crash when trying to access SAF content
* **Fix:** Crash when trying to authenticate an OAuth remote on devices with defective browser
* **Fix:** Crash when trying to stop failed Streaming
* **Fix:** Crash when updating local storage devices when discovering invalid or unknown devices
* **Fix:** FileNotFound being reported as SAF server error (SAFDAV)
* **Fix:** Local storage spaces not refreshing after changing storage permissions
* **Fix:** Notification for uploads/downloads showing garbage
* **Fix:** OAuth getting stuck ("Error creating remote")
* **Fix:** OneDrive configuration not working
* **Fix:** Password default length and generation algorithm
* **Fix:** Seafile DocumentsProvider access not working (SAFDAV)
* **Fix:** Slow deletions in large directories or containers
* **Fix:** Streaming getting stuck after failure
* **Fix:** Using technical name for remotes in some circumstances
* **Internal:** Appcenter error reporting has replaced Firebase
* **Internal:** Fix source build (rclone, GitHub actions)
* **Internal:** Support for rclone rcd
* **Internal:** Updated dependencies (gradle, java, rclone)

***

### 1.11.4
* **Fix:** Crash when sharing file to rcx contains garbage data, part II
* **Fix:** Crash when deleting remotes
* **Fix:** Crash when opening changelog or license on Android Lollipop. Note that Lollipop is no longer supported and will be removed in a future version. 
* **Fix:** Crash when generating link
* **Fix:** VLC not playing audio files
* **Fix:** Link, Sync and Download being displayed for local remotes
* **Fix:** RCX not working on some 64 Bit ARM devices. This might break again in a future version.

***

### 1.11.3
* **Fix:** Crash when selecting "Add Storage" on TVs and other devices
* **Fix:** Crash when sharing file to rcx contains garbage data
* **Fix:** Crash when using thumbnails
* **Fix:** Crash after granting storage permission, part II
* **Fix:** Crash when navigation gets confused
* **Fix:** Crash when creating config for meta-remotes

***

### 1.11.2
* **Fix:** Crash after granting storage permission
* **Fix:** Crash when canceling loading early
* **Fix:** Crash when importing config on device without ACTION_OPEN_DOCUMENT
* **Fix:** Crash when stream is detected as failed
* **Fix:** Crash when reading circular dependent config
* **Fix:** Crash reporting not working

***

### 1.11.1
* **Fix:** Local drives refreshing when nothing changed
* **Fix:** Local drives not cleaning up properly
* **Fix:** Renamed and generated remote names
* **Fix:** OAuth flag missing for Dropbox, Jottacloud and Mail.ru
* **Fix:** SD card SAF access errors being ignored
* **Fix:** Crash when streaming file fails
* **Fix:** Crash when having local drives enabled
* **Fix:** Crash when picking files for upload (Huawei only)

***

### 1.11.0
* **New:** Mega.nz config
* **New:** Google Photos config
* **New:** Renaming remotes
* **New:** DLNA serving
* **New:** Automatically manage alias remotes for local drives
* **New:** Show storage usage (rclone about)
* **New:** Reauthorize OAuth remotes
* **New:** Rclone 1.51.0
* **New:** Settings shortcut
* **New:** Android R support (preliminary)
* **Fix:** OneDrive configuration
* **Fix:** Streams now only start if available, otherwise fail loudly
* **Fix:** Crash when opening file
* **Internal**
   * Java 8 and Stream support
   * Rclone source build
   * Improved logging
   * InteractiveRunner Framework
   * Moved strings to external resource for translation
   
***

### 1.10.2
* **Fix:** Crash when opening file

***

### 1.10.1
* **Fix:** Thubnails not loading after device rotation
* **Fix:** Crash when importing rclone config
* **Fix:** Crash when dismissing invalid dialog
* **Fix:** Crash when streaming starting stream

***

### 1.10.0
* **New:** Proxy support (for https & http)
* **New:** Various internal refactorings
* **New:** The thumbnail size limit can now be set in the settings
* **New:** Update rclone to 1.50.2
* **Fix:** Logging settings not explaining data collection
* **Fix:** External locations listed multiple times
* **Fix:** OAuth not working on newer rclone versions
* **Fix:** Crash when streaming in background

***

### 1.9.2
* **New:** SAF DCA: faster directory lists & more
* **Fix:** Crash when upload from directory that is no longer accessible
* **Fix:** Creating a public link also toggles "wrap filenames"
* **Fix:** OAuth configurations not working

***

### 1.9.1
* **New:** Update to Rclone v1.49.5
* **Fix:** Crash in LoadingDialog
* **Fix:** Rclone serve not starting correctly
* **Fix:** Crash from robotest (#23)

***

### 1.9.0
* **New:** Update to Rclone v1.49.4
* **New:** Preview of Storage Access Layer for rclone (allows access to Storage Access Framework locations from rclone/rcloneExplorer)
* **New:** Support for creating union remotes
* **Fix:** Crash when no compatible streaming app is installed
* **Fix:** Crash on LoadingDialog
* **Fix:** Crash on Android 5.1.1 for remotes with time zones
* **Fix:** Crash in hide remotes dialog
* **Fix:** Security issue when using thumbnails (details see wiki)

***

### 1.8.2
* **New:** Update to Rclone v1.49.3
* **New:** Improved Android 10 support: Streaming now works with third party players
* **New:** Improved Android TV support: App no longer crashes on unsupported link types and will show up on your home screen if installed
* **New:** Support more external storage options when selecting files for upload
* **New:** Updated dependencies and migrated to AndroidX
* **Fix:** Crash when selecting inaccessible storage location

***

### 1.8.1 (previously released as 1.7.5-android10test)
* **Fix:** Rebuild with go1.13 for Android 10 testing

***

### 1.8.0 (previously released as v1.7.5)
* First Release with non-conflicting package name
* **New:** Update to Rclone v1.49.1
* **New:** Support for devices with x86_64 cpu and new Android W^X policy
* **New:** Import external config files automatically by placing them on external storage in ```Android/data/``````io.github.rareities.protonbridge.x0b``````/files/rclone.conf```
* **New:** Fix files with mime type application/octet-stream if they have a playable extension
* **Fix:** Crash when folder to large
* **Fix:** Onedrive config has been removed from ui because it is no longer compatible with rclone
