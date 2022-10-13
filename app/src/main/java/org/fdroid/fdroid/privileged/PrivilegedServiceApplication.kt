package org.fdroid.fdroid.privileged

import android.app.Application
import android.content.Context
import android.os.Build
import org.fdroid.fdroid.privileged.BuildConfig
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.sui.Sui

// Adapted and converted to Kotlin from
// https://github.com/RikkaApps/Shizuku-API/tree/21f9561/demo/src/main/java/rikka/shizuku/demo/util

class PrivilegedServiceApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    companion object {
        init {
            Sui.init(BuildConfig.APPLICATION_ID)
        }
    }
}