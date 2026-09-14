package com.wakeupbuddy

/**
 * App-wide config. The in-app "Update" button and the shareable QR both point at
 * GitHub. The download happens in the phone's browser, so the app itself never
 * needs the INTERNET permission and stays fully offline. Net is used only to update.
 */
object Config {
    const val GITHUB_REPO = "https://github.com/lilesh0070/wakeup"

    // The release asset must be named exactly this (see README "Publishing an update").
    const val APK_ASSET = "WakeUpBuddy.apk"

    // GitHub always redirects this to the newest release's APK -> a direct download.
    const val UPDATE_DOWNLOAD_URL = "$GITHUB_REPO/releases/latest/download/$APK_ASSET"

    // The releases page (fallback / "see all versions").
    const val LATEST_RELEASE_PAGE = "$GITHUB_REPO/releases/latest"
}
