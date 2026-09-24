# Rom Shortcut Maker

Android app scaffold for creating home-screen shortcuts that launch games in RetroArch.

## What it does

- **Shortcut Builder**: Enter a title, ROM path, and RetroArch core to create pinned home-screen shortcuts.
- **Game Picker**: Android Storage Access Framework (SAF) file chooser for selecting ROM files.
- **Core Presets**: Browse and select from 8+ pre-configured RetroArch cores (SNES9x, Genesis Plus GX, mGBA for Game Boy, Game Boy Color, and GBA, Nestopia, Mupen64Plus, TurboGrafx, Dolphin, PCSX2) with system labels.
- **Live Preview**: See shortcut name, ROM path, core path, and RetroArch package before creating.
- **Sends RetroArch Intent Extras**: Passes `ROM` and `LIBRETRO` extras used by RetroArch's Android launch flow.

## Project layout

- `app/src/main/java/com/romshortcutmaker/MainActivity.kt` - Shortcut builder UI with file picker and core presets.
- `app/src/main/java/com/romshortcutmaker/data/CorePreset.kt` - Data model for 8 common RetroArch cores (SNES, Genesis, N64, GB/GBC/GBA, NES, TurboGrafx, GameCube, PS2).
- `app/src/main/java/com/romshortcutmaker/FilePickerHelper.kt` - Storage Access Framework (SAF) file picker utilities.
- `app/src/main/java/com/romshortcutmaker/ui/theme/Theme.kt` - Compose Material 3 dark theme.
- `app/src/main/AndroidManifest.xml` - Launcher activity declaration.

## Notes

- Android builds require a local Android SDK. Set `local.properties` with `sdk.dir=...` or export `ANDROID_HOME` before running `assembleDebug`.
- The generated VS Code task runs `assembleDebug` through `gradlew`.
- Replace the example ROM and core paths with valid paths on your device.
- RetroArch package names may vary by build. The default is `com.retroarch`.
- If your installed RetroArch build uses a different launch entry point, adjust the intent resolution logic in `MainActivity.kt`.
