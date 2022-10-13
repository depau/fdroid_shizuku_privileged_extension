package org.fdroid.fdroid.privileged

import android.app.Application
import android.content.Context
import android.content.pm.*
import android.content.pm.PackageInstaller.SessionParams
import android.os.Build
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException

// Adapted and converted to Kotlin from
// https://github.com/RikkaApps/Shizuku-API/tree/21f9561/demo/src/main/java/rikka/shizuku/demo/util

object ShizukuPackageInstallerUtils {
    private val PACKAGE_MANAGER: IPackageManager by lazy {
        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(
                    "package"
                )
            )
        )
    }

    fun getPrivilegedPackageInstaller(): IPackageInstaller {
        val packageInstaller: IPackageInstaller = PACKAGE_MANAGER.packageInstaller
        return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(packageInstaller.asBinder()))
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    fun createPackageInstaller(
        installer: IPackageInstaller?,
        installerPackageName: String?,
        userId: Int,
        application: Application
    ): PackageInstaller {
        val installerAttributionTag = null
        return if (Build.VERSION.SDK_INT >= 31) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(installer, installerPackageName, installerAttributionTag, userId)
        } else if (Build.VERSION.SDK_INT >= 26) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
            ).newInstance(installer, installerPackageName, userId)
        } else {
            PackageInstaller::class.java.getConstructor(
                Context::class.java,
                PackageManager::class.java,
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(
                application, application.packageManager, installer, installerPackageName, userId
            )
        }
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    fun createSession(session: IPackageInstallerSession?): PackageInstaller.Session {
        return PackageInstaller.Session::class.java.getConstructor(IPackageInstallerSession::class.java)
            .newInstance(session)
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun getInstallFlags(params: SessionParams?): Int {
        return SessionParams::class.java.getDeclaredField("installFlags")[params] as Int
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun setInstallFlags(params: SessionParams?, newValue: Int) {
        SessionParams::class.java.getDeclaredField("installFlags")[params] = newValue
    }
}