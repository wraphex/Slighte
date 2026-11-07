# Slighte

Adjust screen brightness by swiping left/right on the status bar. An Xposed module built on [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) and [libxposed](https://github.com/libxposed/api).

## Features

- Swipe horizontally anywhere on the status bar — your finger's x position *is* the brightness percentage (tap to jump, not a relative drag)
- Gamma curve (γ = 2.4), so the dim end gets finer steps
- During the gesture only `DisplayManager.setTemporaryBrightness` is called (no setting writes); the value is persisted to `Settings.System.SCREEN_BRIGHTNESS` on lift
- Vertical / diagonal drags still pull down the shade; only a clear horizontal swipe claims the gesture
- Floor is 1/255, not `config_screenBrightnessSettingMinimum` (that is just the Settings *slider* minimum)

## Requirements

| Item | Version |
| --- | --- |
| Android | 8.0+ (minSdk 26, targetSdk 37) |
| Xposed framework | One supporting libxposed API 102 (e.g. LSPosed) |
| Scope | `com.android.systemui` |

## Install

1. Download or build the APK
2. Enable the module in your Xposed manager and tick the scope **System UI (com.android.systemui)**
3. Restart System UI or reboot

## Build

```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # release build (currently signed with the debug key, see the TODO in app/build.gradle.kts)
./gradlew test              # pure-logic unit tests
```

Requires JDK 17 and the Android SDK (compileSdk 37).

## How it works

`HookEntry` hooks `PhoneStatusBarView#onAttachedToWindow` and attaches an `OnTouchListener` to the status bar view from the `after` callback. Each view gets its own `BrightnessHelper`, keyed weakly in a `WeakHashMap` so views recreated on configuration changes don't leak.

The gesture logic in `BrightnessHelper` is a small state machine:

- `ACTION_DOWN` — record the origin, set `lastSent = null` (meaning "this gesture is not claimed yet")
- `ACTION_MOVE` — while unclaimed, check `isHorizontalSwipe(dx, dy, slop)` (`dx > slop && dx > dy * 2`); return `false` and let the system handle it otherwise. Only when it passes does it call `requestDisallowInterceptTouchEvent(true)` and start sending levels
- `ACTION_UP` — persist the final value to Settings
- `ACTION_CANCEL` — clean up state only, no write

Brightness pipeline: percentage → `brightnessSetting()` (continuous float) → `levelOf()` (truncated to an integer in 1..255).

The reflective `setTemporaryBrightness` call adapts to the runtime signature: newer builds take `(int displayId, float)` in the 0..1 domain, older ones take a single argument in the raw level domain.

## Layout

```
app/src/main/java/wraphex/slighte/
├── SlighteApp.kt              # ModuleApplication entry point
└── hook/
    ├── HookEntry.kt           # Xposed entry, installs the OnTouchListener
    └── BrightnessHelper.kt    # gesture state machine + brightness mapping + writes
app/src/test/java/wraphex/slighte/hook/
├── BrightnessValueTest.kt     # curve: monotonicity, bounds, gamma behaviour
└── SwipeLogicTest.kt          # swipe detection and send throttling
```

## Known limitations

- Depends on `com.android.systemui.statusbar.phone.PhoneStatusBarView`. If the class is missing the module logs an error and goes inert (custom ROMs without an AOSP-style status bar may not work)
- Hardware brightness is owned by the system; auto-brightness or night light may override the value

## License

[MIT](LICENSE) © 2026 wraphex
