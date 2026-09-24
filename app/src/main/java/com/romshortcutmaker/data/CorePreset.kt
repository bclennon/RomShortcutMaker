package com.romshortcutmaker.data

data class CorePreset(
    val name: String,
    val coreFileName: String,
    val thumbnailDatabase: String,
    val systems: List<String>
) {
    fun resolveCorePath(retroArchPackage: String): String {
        return "/data/user/0/$retroArchPackage/cores/$coreFileName"
    }
}

val COMMON_CORES = listOf(
    CorePreset(
        name = "SNES9x",
        coreFileName = "snes9x_libretro_android.so",
        thumbnailDatabase = "Nintendo - Super Nintendo Entertainment System",
        systems = listOf("SNES", "Super Nintendo")
    ),
    CorePreset(
        name = "Mupen64Plus",
        coreFileName = "mupen64plus_next_gles3_libretro_android.so",
        thumbnailDatabase = "Nintendo - Nintendo 64",
        systems = listOf("N64", "Nintendo 64")
    ),
    CorePreset(
        name = "Genesis Plus GX",
        coreFileName = "genesis_plus_gx_libretro_android.so",
        thumbnailDatabase = "Sega - Mega Drive - Genesis",
        systems = listOf("Genesis", "Mega Drive", "SMS", "Game Gear")
    ),
    CorePreset(
        name = "mGBA",
        coreFileName = "mgba_libretro_android.so",
        thumbnailDatabase = "Nintendo - Game Boy Advance",
        systems = listOf("Game Boy", "Game Boy Color", "GBA")
    ),
    CorePreset(
        name = "Beetle PCE",
        coreFileName = "mednafen_pce_libretro.so",
        thumbnailDatabase = "NEC - PC Engine - TurboGrafx 16",
        systems = listOf("TurboGrafx-16", "PC Engine")
    ),
    CorePreset(
        name = "Nestopia",
        coreFileName = "nestopia_libretro_android.so",
        thumbnailDatabase = "Nintendo - Nintendo Entertainment System",
        systems = listOf("NES", "Famicom")
    ),
    CorePreset(
        name = "Dolphin",
        coreFileName = "dolphin_libretro.so",
        thumbnailDatabase = "Nintendo - GameCube",
        systems = listOf("GameCube", "Wii")
    ),
    CorePreset(
        name = "PCSX2",
        coreFileName = "pcsx2_libretro.so",
        thumbnailDatabase = "Sony - PlayStation 2",
        systems = listOf("PlayStation 2", "PS2")
    )
)
