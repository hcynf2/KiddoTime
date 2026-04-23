# KiddoTime — CLAUDE.md

Android parental screen-time control app. Jetpack Compose + MVVM, Material 3, Room, Kotlin Coroutines.

---

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin   # type-check only (fast)
./gradlew :app:assembleDebug        # full build
```

---

## Architecture

| Layer | Location | Notes |
|-------|----------|-------|
| UI | `screens/` | Jetpack Compose screens |
| ViewModel | `viewmodel/` | `AndroidViewModel`, `StateFlow` |
| Repository | `data/*Repository.kt` | Thin wrappers over DAOs + SharedPrefs |
| DAO | `data/*Dao.kt` | Room `@Dao` interfaces |
| Entities | `data/*.kt` | Room `@Entity` data classes |
| Services | `service/`, `overlay/` | Foreground service + overlay service |

Navigation is a single `NavHost` in `MainActivity`. Routes are in `navigation/Routes.kt`.

---

## Database

Room database: `kiddotime_database`, current version **5**.

| Table | Purpose |
|-------|---------|
| `app_limits` | Per-app daily limits |
| `limit_events` | Every time a limit fires; `appClosedAt` stamped when overlay dismissed |
| `screen_time_requests` | Child time-extension requests; `resolvedAt` stamped on approve/deny |
| `cooldown_events` | Every game overlay session; `completedAt` + `whatNextChoice` stamped on completion |

**Always add a `MIGRATION_N_(N+1)` object and pass it to `.addMigrations(...)` when bumping the version.** Never use `fallbackToDestructiveMigration`.

---

## SharedPreferences namespaces

| File key | Class | Contents |
|----------|-------|----------|
| `kiddotime_secure_prefs` | `PinRepository` | Encrypted parent PIN (AES-256-GCM) |
| `kiddotime_game_prefs` | `GamePreferences` | Last/current game for rotation + resume |
| `kiddotime_rewards` | `StarRepository` | Star balance, pending celebration flag |
| `kiddotime_badges` | `BadgeRepository` | Earned + seen badge sets |
| `bedtime_prefs` | `BedtimeRepository` | Enabled, hour, minute, selected apps, lock state |
| `kiddotime_screen_limit` | `ScreenTimeLimitRepository` | Total daily cap ms |
| `kiddotime_time_request_prefs` | `TimeRequestPreferences` | Parent toggle for time requests |

---

## Overlay / game flow

```
AppMonitorService  →  OverlayService.start()
                           ↓
                    GamePreferences.pickGame()   // never repeats same game twice
                           ↓
               CleanUpGameView / WhatNextGameView / CardMatchingGameView
                           ↓  (completion callback)
                    CooldownEventRepository.recordComplete()
                           ↓
                    showLockScreen()  →  PIN entry or back/home to dismiss
```

- Each game view has a completion callback: `onAllItemsPlaced`, `onActivityChosen(label)`, `onAllRoundsComplete`
- `OverlayService` has a `CoroutineScope(SupervisorJob + Dispatchers.IO)` cancelled in `onDestroy`
- `@Volatile currentCooldownEventId` tracks the in-progress DB row

---

## Key patterns

**Permission re-check on resume**
`ParentScreen` uses `DisposableEffect(lifecycleOwner)` to call `viewModel.recheckPermission()` on every `ON_RESUME`. `recheckPermission()` is guarded by `if (_uiState.value.hasPermission) return` to prevent duplicate flow launches.

**Reactive stats**
`LimitEventDao.observeEventCount()` returns `Flow<Int>`; `ChildViewModel` collects it to auto-refresh on any DB change.

**1-request-per-day cap**
`ScreenTimeRequestRepository.countTodayRequests()` computes midnight boundary and queries the DB. `ChildViewModel.submitRequest()` checks this before inserting.

**Cooldown analytics**
`CooldownEventRepository` records start at game show time and stamps completion (with optional `whatNextChoice`) when the child finishes. `ParentViewModel.buildCooldownStats()` aggregates counts per game type and choice frequencies.

---

## Conventions

- All DB writes go through the repository, never directly via DAO from a ViewModel
- `viewModelScope.launch` for fire-and-forget; `suspend fun` in repos/DAOs
- `StateFlow<UiState>` pattern — one state object per ViewModel, copied with `.copy()`
- Material 3 components only — no Material 2 imports
- No `fallbackToDestructiveMigration` — always write explicit migrations
- Bottom sheets opened via a `DashboardSheet` enum state variable, not separate nav routes
- Compile-check after every set of changes: `./gradlew :app:compileDebugKotlin`
