package io.horizontalsystems.thorchainkit

import java.io.File

public actual abstract class PlatformContext internal constructor() {
    public abstract val dataDir: File
}

public fun PlatformContext(dataDir: File): PlatformContext = DesktopPlatformContext(dataDir)

private class DesktopPlatformContext(override val dataDir: File) : PlatformContext()
