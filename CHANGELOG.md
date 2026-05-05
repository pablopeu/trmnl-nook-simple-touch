# Changelog

All notable changes to this project will be documented in this file.

## [v0.14.0] - 2026-05-05

### Fixed
- **Rare stuck fetching state** - The app could get permanently stuck showing "Fetching..." after a network hiccup. Added a fetch watchdog that cancels the request and retries on the next cycle.
- **Wi-Fi recovery after connectivity timeout** - When Wi-Fi gets stuck in a bad reconnection state, the app now toggles Wi-Fi off/on before falling back to the no-wifi screen.

### Changed
- **Connectivity timeout reduced from 30s to 5s** - Faster recovery when Wi-Fi is unavailable.
- **Image URL scheme rewritten to match API request** - If the API is accessed over HTTP, the image URL is automatically rewritten to match, fixing self-hosted setups.
- **Silent fetch for showcase cells** - Showcase cells now fetch in the background without flashing status text.

---

## [v0.13.2] - 2026-04-22

### Fixed
- **Wi-Fi recovery after connectivity timeout** - When a battery-powered Nook wakes from sleep and its Wi-Fi radio gets stuck in a bad reconnection state, the app now toggles Wi-Fi off/on (up to 2 attempts) before falling back to the no-wifi screen. Previously the device would show "Couldn't connect" and stay stuck until manually rebooted.

---

## [v0.12.2] - 2026-03-29

### Fixed
- **Menu → Next no longer gets stuck on "Connecting..."** - Opening the menu and tapping Next was cancelling the WiFi connectivity wait mid-fetch.

---

## [v0.12.1] - 2026-03-29

### Fixed
- **Aggressive sleep wake cycles no longer flash the log screen.** The app now renders the new image before sleeping, so you see the image instead of status text.
- Screen timeout is always restored if you navigate away or the app exits mid-sleep.

---

## [v0.12.0] - 2026-03-29

### Fixed
- **Aggressive sleep now reliably fires after every scheduled refresh.** Previously the super-sleep check used `fetchReason` and flag state that could be clobbered by `onResume()`, meaning the device often stayed awake after displaying a new image. Simplified to: if Aggressive Sleep is enabled and the fetch was not triggered by the user (menu tap), call `sleepNow()` immediately after the image renders — no flags, no race conditions.
- `sleepPending` is now correctly cleared in `onResume()` and the screen timeout is always restored to 120 s on wake, regardless of sleep path.

### Notes
- All source edits must target the worktree at `/home/coder/trmnl-nook-sleep/` — see AGENTS.md.

---

## [v0.11.0] - 2026-03-28

### Added
- **Aggressive Sleep** (`Settings → General → Aggressive sleep`): puts the device to sleep immediately after each scheduled image refresh rather than waiting for the screensaver timeout. Battery savings vs. standard deep sleep are TBD.
- **Sleep button** (`Settings → System → Sleep`): manually triggers an immediate sleep from the settings screen.
- `android.permission.WRITE_SETTINGS` permission — used to set `SCREEN_OFF_TIMEOUT = 1000 ms` to trigger Android's natural screen-off path (no root required). Restored to 120 s on wake.
- `AGENTS.md`: documents the build environment, all failed sleep approaches, and the working `WRITE_SETTINGS` trick.

### Changed
- Screensaver/sleep-ready delay reduced from 5 s to 2 s.
- Deep sleep hint always visible when "Sleep between updates" is enabled.
- README: added Aggressive Sleep section and listed it in Features.

### Removed
- `android.permission.DEVICE_POWER` (was unused / ungrantable).

---

## [v0.10.0] - 2026-03-25

### Added
- Gift Mode restart flow improvements.

---

*Older releases are documented on the [GitHub Releases](https://github.com/usetrmnl/trmnl-nook-simple-touch/releases) page.*
