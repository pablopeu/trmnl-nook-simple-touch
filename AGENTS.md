# Agent notes (legacy Android / NOOK Simple Touch)

This repo targets very old Android and an Eclipse ADT / Ant-era workflow.
Do not suggest modern Android tooling or APIs unless they are explicitly
compatible with Android 2.1 (API 7).

Current notes:
- Main entry activity: `DisplayActivity`.
- API credentials/base URL live in app settings (`ApiPrefs`).

## Key Patterns

ADB-over-TCP on NOOK is flaky after sleep/wake. The `nook-adb.sh` script
auto-recovers from `offline` state (kill-server → reconnect cycle), so
**always use `tools/nook-adb.sh`** — never call `adb` directly.

Saved file logs (when "Save to file" enabled) live at:
- `/media/My Files/trmnl.log`

To read logs:
```bash
# Using nook-adb.sh wrapper (recommended)
tools/nook-adb.sh --ip <ip> get-logs [n]  # default: last 200 lines

# Or direct adb command
adb -s <ip>:5555 shell "tail -n 200 '/media/My Files/trmnl.log'"
```

### HTTP Requests
- All HTTPS goes through `BouncyCastleHttpClient` (TLS 1.2 support for old Android)
- HTTP (non-TLS) supported when "Allow HTTP" setting enabled (for BYOS/local servers)
- Self-signed certs supported when "Allow self-signed certificates" enabled
- **Always retry failed requests** with 3s backoff (network often flaky after wake)
- Wait for WiFi connectivity before attempting fetches (`waitForWifiThenFetch()`)
- Image fetches need retry too, not just API calls

### Testing on Device

**Standard workflow for testing changes:**

```bash
# Always use build-install-run (builds, installs, launches app)
NOOK_IP=<ip> tools/nook-adb.sh build-install-run

# Or with logcat output:
NOOK_IP=<ip> tools/nook-adb.sh build-install-run-logcat
```

**Initial worktree setup:**
- Run `.mux/init` to symlink `local.properties` and JAR files from main repo
- This is required for worktrees to build successfully

**Other useful commands:**
- Run logcat in background: `tools/nook-adb.sh logcat` to monitor while testing
- Read file logs: `tools/nook-adb.sh --ip <ip> get-logs [n]` (default: 200 lines)
- Device often goes offline when WiFi auto-disabled; use "Auto-disable WiFi" setting OFF during dev
- ADB reconnect: `tools/nook-adb.sh connect` after device comes back online
- Clean build: `tools/nook-adb.sh --clean build-install-run`

### Boot & Error UX

### Prefs presets (SaaS / BYOS)

Device config presets live in `~/trmnl-prefs/` and are shared across all worktrees via symlink (created by `.mux/init`).

To switch settings quickly, create preset files like `~/trmnl-prefs/myserver.args`. Use one argument per line so the shell doesn't have to parse quoting.

Example: `prefs/selfhosted.args`

```
--string
api_id
A1:B2:C3:D4:E5:F6
--string
api_token
YOUR_TOKEN
--string
api_base_url
http://192.168.1.232:2300/api
--bool
allow_http
true
--bool
allow_sleep
false
```

Apply with:

```
tools/nook-adb.sh --ip <ip> set-preset selfhosted
```

For SaaS, create `prefs/saas.args` with the hosted credentials/base URL, then:

```
tools/nook-adb.sh --ip <ip> set-preset saas
```
- Boot screen: header with icon + status text + streaming logs below
- Update status via `setBootStatus("message")` during boot
- On error: show boot header with "Error - tap to retry" + full logs
- Call `hideBootScreen()` only when content successfully loads

### Logging
- `logD()`/`logW()` stream to screen during boot (while `!bootComplete`)
- After boot completes, logs only go to Android logcat
- `logE()` always shows on screen

## Nook Device Details

### Nook Settings App
The device has a third-party settings app installed:
- **Package**: `com.home.nmyshkin.nooksettings`
- **Launchable activity**: `net.dinglisch.android.tasker.Kid`

To launch it from Java code:
```java
Intent intent = new Intent();
intent.setComponent(new ComponentName(
    "com.home.nmyshkin.nooksettings",
    "net.dinglisch.android.tasker.Kid"));
startActivity(intent);
```

To launch via ADB:
```bash
adb -s <ip>:5555 shell am start -n com.home.nmyshkin.nooksettings/net.dinglisch.android.tasker.Kid
```

The TRMNL API credentials (Device ID + Token) are found inside this app under **Developer Perks**.

### Finding the launchable activity of an installed APK
```bash
# Pull APK from device, then inspect with aapt
adb -s <ip>:5555 pull $(adb -s <ip>:5555 shell pm path <pkg> | sed 's/package://') /tmp/app.apk
aapt dump badging /tmp/app.apk | grep launchable
```

## Coder Agent Workflow

### Running the devcontainer
This repo has a `.devcontainer/` with a full Android SDK (Eclipse ADT bundle, Java 8, Ant).
Use the `devcontainer` CLI (available at `/tmp/coder-script-data/bin/devcontainer`) to start it:

```bash
devcontainer up --workspace-folder /home/coder/trmnl-nook
```

The `initializeCommand` installs QEMU binfmt handlers for x86/x86_64 emulation (required because
the ADT tools are 32-bit x86 binaries running on an ARM64 host). This must succeed before the
build image can be used.

Once up, exec commands into the container:
```bash
CONTAINER_ID=$(docker ps -q --filter "label=devcontainer.local_folder=/home/coder/trmnl-nook")
docker exec "$CONTAINER_ID" bash -c "cd /workspace && tools/nook-adb.sh --ip <ip> build-install-run"
```

### API compatibility gotchas
The Nook Simple Touch runs **Android 2.3 (API 10)**. Several commonly-used View methods don't exist:
- `View.setBackground(Drawable)` — added API 16. Use `setBackgroundDrawable(Drawable)` instead.
- `View.setBackground(null)` — same issue. Use `setBackgroundDrawable(null)`.
- Always test against API 10; the Ant build target is API 20 so the compiler won't catch these — they crash at runtime with `NoSuchMethodError`.

### INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES
If `adb install -r` fails with this error, the existing APK was signed with a different debug key.
The `adb uninstall` command may hang on this device. Use `adb install` (without `-r`) after the
existing install is gone, or push + `pm install` directly:

```bash
# Uninstall (may hang — kill if needed)
adb -s <ip>:5555 shell pm uninstall -k <package>

# If uninstall hangs, the package may not be installed — just install fresh:
adb -s <ip>:5555 install bin/trmnl-nook-simple-touch-debug.apk
```

### Pushing to upstream
The upstream repo is `usetrmnl/trmnl-nook-simple-touch`. The Coder workspace user (`bpmct`) does
not have direct write access. Workflow:

1. Fork via `gh repo fork usetrmnl/trmnl-nook-simple-touch --clone=false`
2. Push changes to a branch on the fork: `git push origin main:my-branch-name`
3. Open PR: `gh pr create --repo usetrmnl/trmnl-nook-simple-touch --head bpmct:my-branch-name ...`

Note: GitHub auto-syncs forks, so pushing directly to `main` on the fork will not create a PR
(GitHub sees no diff). Always push to a named branch.

## Index

- `AGENTS/platform-constraints.md`
- `AGENTS/build-tooling.md`
- `AGENTS/release.md`
- `AGENTS/tls-network.md`
- `AGENTS/sleep-wake-cycle.md`
- `AGENTS/ux-patterns.md`
- `AGENTS/references.md`
