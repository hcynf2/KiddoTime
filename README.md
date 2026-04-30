# KiddoTime

**Screen time, made friendly.**

KiddoTime is an Android parental screen-time control app that enforces daily app limits through interactive cooldown games rather than hard blocks. Children complete a short mini-game when a limit is reached, earn stars and badges for cooperating, and can ask a parent for extra time. Parents get a full usage dashboard, bedtime locks, behaviour analytics, and PIN-protected controls — all stored entirely on-device with no account required.

---

## Features

### For Parents
- **Per-app daily limits** — set a time limit (hours + minutes) on any installed app
- **Total daily screen-time cap** — a single limit across all apps combined
- **Bedtime lock** — automatically lock selected apps at a chosen time; locks release at 06:00; 30-min and 10-min warnings shown beforehand
- **Usage dashboard** — today's total, daily average, 7-day weekly highlights, longest session, most-used apps
- **Behaviour analytics** — average time to stop after a limit fires, smooth-stop streak, hardest app to stop
- **Limit event history** — every limit event timestamped with on-time / late / open status
- **Time requests** — enable or disable the child's ability to request 30 extra minutes once per day; approve (PIN required) or deny from the dashboard
- **Cooldown game analytics** — completion rates per game type, What's Next? activity choice breakdown
- **Parent PIN** — 4–6 digit PIN encrypted with AES-256-GCM; required to approve time requests and dismiss the post-game lock screen
- **Privacy & Data** — export or permanently delete all stored data

### For Children
- **Friendly dashboard** — star balance, streak counter, today's stop record, weekly streak strip
- **Stars & badges** — earn a star for every on-time stop; unlock badges for milestones (first stop, 3-day streak, 7-day streak, perfect day, 10 stars, 25 stars)
- **Time requests** — ask for 30 extra minutes on a limited app (once per day, if enabled by parent)
- **Cooldown games** — three rotating mini-games that must be completed before returning to the device:
  - **Card Matching** — flip and match pairs
  - **Clean-Up** — drag items to their correct places
  - **What's Next?** — choose an offline activity (Book, Toys, Snack, Wash Hands, Cuddle)

### System
- **AppMonitorService** — foreground service that polls every second using `UsageStatsManager`; restarts automatically on device reboot via `BootReceiver`
- **OverlayService** — draws the cooldown game and lock screen over any app using `TYPE_APPLICATION_OVERLAY`
- **Game rotation** — the same game never appears twice in a row; rotation state is persisted across process restarts

---

## Tech Stack

| Area | Technology |
|------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM — `AndroidViewModel`, `StateFlow`, Repository pattern |
| Database | Room (SQLite), version 5 |
| Async | Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) |
| Navigation | Navigation Compose (`NavHost`) |
| Security | `androidx.security:security-crypto` (AES-256-GCM via `EncryptedSharedPreferences`) |
| App monitoring | `UsageStatsManager` (`queryEvents`, `queryUsageStats`) |
| Overlay | `WindowManager` + `TYPE_APPLICATION_OVERLAY` |
| Build | Gradle KTS, KSP for Room annotation processing |

---

## Requirements

- **Android 8.0+** (minSdk 26)
- **Compile SDK / Target SDK:** 35
- **Android Studio:** Hedgehog or later (for Kotlin 2.x Compose compiler plugin support)
- **Java:** JDK 11 (bundled with Android Studio)

### Permissions

| Permission | Purpose |
|-----------|---------|
| `PACKAGE_USAGE_STATS` | Read per-app usage time; must be granted manually in system settings |
| `SYSTEM_ALERT_WINDOW` | Draw the game and lock screen overlays over other apps |
| `FOREGROUND_SERVICE` | Keep AppMonitorService alive while monitoring |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for foreground services declared with `specialUse` type on API 34+ |
| `RECEIVE_BOOT_COMPLETED` | Restart the monitor service after device reboot |
| `POST_NOTIFICATIONS` | Show the persistent foreground service notification |

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/KiddoTime.git
cd KiddoTime
```

### 2. Set JAVA_HOME (macOS with Android Studio)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### 3. Build

```bash
# Type-check only (fast — use after every change)
./gradlew :app:compileDebugKotlin

# Full debug APK
./gradlew :app:assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Install and grant permissions

After installing on a device or emulator, open the app and:

1. Tap **I'm a Parent**
2. Follow the **Grant Permission** prompt → this opens the system Usage Access settings screen
3. Enable KiddoTime in that list
4. Press back — the dashboard loads automatically

For the overlay to work, Android will also prompt for the **Display over other apps** permission on first use.

---

## Project Structure

```
app/src/main/java/com/kiddotime/app/
│
├── MainActivity.kt               # Single activity; hosts NavHost
│
├── navigation/
│   └── AppNavigation.kt          # Routes: splash, mode_select, parent, child, data_privacy
│
├── screens/
│   ├── SplashScreen.kt           # Animated splash (fade in → hold → fade out)
│   ├── ModeSelectScreen.kt       # "Who is using the device?" entry point
│   ├── ParentScreen.kt           # Parent dashboard + all bottom sheets
│   ├── ChildScreen.kt            # Child dashboard (stars, streak, badges, requests)
│   └── DataPrivacyScreen.kt      # Export / delete all data
│
├── viewmodel/
│   ├── ParentViewModel.kt        # Dashboard stats, limits, bedtime, PIN, requests
│   └── ChildViewModel.kt         # Stars, badges, streak, time requests
│
├── data/
│   ├── AppDatabase.kt            # Room database (v5); all migrations 1→5
│   ├── AppLimit.kt / Dao / Repository
│   ├── LimitEvent.kt / Dao / Repository
│   ├── ScreenTimeRequest.kt / Dao / Repository
│   ├── CooldownEvent.kt / Dao / Repository
│   ├── PinRepository.kt          # AES-256-GCM encrypted PIN
│   ├── StarRepository.kt         # Star balance + celebration flag
│   ├── BadgeRepository.kt        # Earned / seen badge sets + evaluation logic
│   ├── BedtimeRepository.kt      # Bedtime config + lock state
│   ├── ScreenTimeLimitRepository.kt  # Total daily cap
│   ├── TimeRequestPreferences.kt # Parent toggle for time requests
│   ├── GamePreferences.kt        # Game rotation (last/current game)
│   ├── UsageStatsHelper.kt       # UsageStatsManager query helpers
│   └── TimeRequestStats.kt       # Analytics data class
│
├── service/
│   ├── AppMonitorService.kt      # Foreground service; polls UsageStatsManager every 1s
│   └── NotificationHelper.kt     # Persistent notification for foreground service
│
├── overlay/
│   ├── OverlayService.kt         # Draws game + lock screen via WindowManager
│   ├── CardMatchingGameView.kt   # Card flip & match mini-game
│   ├── CleanUpGameView.kt        # Drag-to-sort mini-game
│   ├── WhatNextGameView.kt       # Offline activity chooser
│   └── LockScreenView.kt        # PIN entry / dismissal screen
│
├── receiver/
│   └── BootReceiver.kt           # Restarts AppMonitorService after reboot
│
└── ui/theme/
    ├── Theme.kt
    ├── Color.kt
    └── Type.kt
```

---

## Database Schema

Room database name: `kiddotime_database` — current version **5**

| Table | Key columns | Purpose |
|-------|-------------|---------|
| `app_limits` | `packageName`, `appName`, `dailyLimitMs` | Per-app daily limits |
| `limit_events` | `packageName`, `limitReachedAt`, `appClosedAt` | Every limit-fire event; `appClosedAt` stamped when overlay is dismissed |
| `screen_time_requests` | `packageName`, `status`, `extraMs`, `resolvedAt` | Child time-extension requests |
| `cooldown_events` | `gameType`, `startedAt`, `completedAt`, `whatNextChoice` | Every game overlay session |

All migrations are explicit — `fallbackToDestructiveMigration` is never used.

---

## SharedPreferences Namespaces

| File key | Class | Contents |
|----------|-------|----------|
| `kiddotime_secure_prefs` | `PinRepository` | AES-256-GCM encrypted parent PIN |
| `kiddotime_game_prefs` | `GamePreferences` | Last/current game for rotation |
| `kiddotime_rewards` | `StarRepository` | Star balance, pending celebration flag |
| `kiddotime_badges` | `BadgeRepository` | Earned + seen badge sets |
| `bedtime_prefs` | `BedtimeRepository` | Enabled flag, hour, minute, selected apps, lock state, lock date |
| `kiddotime_screen_limit` | `ScreenTimeLimitRepository` | Total daily cap in milliseconds |
| `kiddotime_time_request_prefs` | `TimeRequestPreferences` | Parent toggle for time requests |

---

## Overlay / Game Flow

```
AppMonitorService (polls every 1s)
        │
        │  usage >= limit
        ▼
OverlayService.start(appName, packageName)
        │
        ▼
GamePreferences.pickGame()       ← never repeats the same game twice
        │
        ├─ "card"     → CardMatchingGameView   (onAllRoundsComplete)
        ├─ "cleanup"  → CleanUpGameView         (onAllItemsPlaced)
        └─ "whatnext" → WhatNextGameView        (onActivityChosen)
        │
        ▼
CooldownEventRepository.recordComplete(id, completedAt, whatNextChoice)
        │
        ▼
LockScreenView  →  PIN entry  or  back/home to dismiss
        │
        ▼
Broadcast: "com.kiddotime.app.OVERLAY_DISMISSED"
        │
        ▼
AppMonitorService stamps appClosedAt, awards star if within 60s
```

---

## Badge System

| Badge | Trigger |
|-------|---------|
| First Stop | First ever on-time app stop |
| 3-Day Streak | On-time stops for 3 consecutive days |
| Week Champion | On-time stops for 7 consecutive days |
| Perfect Day | Every stop on-time in a single day |
| Star Collector | Accumulated 10 stars |
| Star Champion | Accumulated 25 stars |

Badges are evaluated by `BadgeRepository.evaluate()` on every `ChildViewModel` load. Newly earned badges are surfaced in a dialog on the child's next visit and marked as seen on dismissal.

---

## Architecture Conventions

- All DB writes go through a Repository — ViewModels never access DAOs directly
- `viewModelScope.launch` for fire-and-forget; `suspend fun` in repos/DAOs
- `StateFlow<UiState>` — one state object per ViewModel, modified only via `.copy()`
- Material 3 components only — no Material 2 imports
- Bottom sheets controlled by a `DashboardSheet` enum, not separate navigation routes
- Permission re-check on `ON_RESUME` is guarded by `if (hasPermission) return` to prevent duplicate coroutine launches

---

## Why `UsageStatsManager` instead of Accessibility Services

`UsageStatsManager` is the purpose-built Android API for reading app usage history. It provides both real-time foreground app detection (via `queryEvents` / `ACTIVITY_RESUMED`) and cumulative daily usage data — neither of which is available from Accessibility Services. Using Accessibility Services for monitoring purposes is discouraged by Google, restricted on API 33+, and would be rejected from the Play Store. `UsageStatsManager` also requires a deliberate, transparent permission grant from the user rather than a broad "observe all your actions" dialog. The trade-off is a ~1–2s polling latency compared to an event-driven callback, which is acceptable for daily-limit enforcement.

---

## Known Issues

- **Bedtime hour validation (TC-14):** entering an out-of-range hour value (e.g. 25) is not rejected inline; the save handler silently discards the invalid value and bedtime does not fire. Fix: add `OutlinedTextField` `isError` validation on change.
- **Overlay context (H1):** the game screen does not display which app triggered the overlay. Children have no contextual explanation for why the game appeared.
- **No cross-device sync:** all data is on-device only. Families with multiple Android devices must configure each independently.
- **No child profile name:** the child dashboard displays a generic greeting ("Hi there!") rather than the child's name.

---

## Future Work

- Time-picker widget for bedtime (replace raw 24-hour text input)
- Child profile name and avatar
- Per-day-of-week limit overrides (e.g. stricter limits on school days)
- Export data as readable PDF report
- Multi-device support via optional encrypted cloud sync

---

## License

This project was developed as a university final-year project. All rights reserved.
