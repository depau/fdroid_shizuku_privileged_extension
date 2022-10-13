/*
 * Copyright (C) 2015-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright 2007, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fdroid.fdroid.privileged

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.*
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import eu.depau.kotlet.android.extensions.notification.NotificationImportanceCompat
import eu.depau.kotlet.android.extensions.notification.registerNotificationChannel
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.IOException
import java.lang.reflect.Field


const val NOTIFICATION_CHANNEL = "org.fdroid.fdroid.privileged.main"

/**
 * This service provides an API via AIDL IPC for the main F-Droid app to install/delete packages.
 */
class PrivilegedService : Service() {
    private lateinit var accessProtectionHelper: AccessProtectionHelper
    private var mCallback: IPrivilegedCallback? = null
    var context: Context = this


    private fun hasPrivilegedPermissionsImpl(): Boolean {
        Log.d(TAG, "hasPrivilegedPermissionsImpl()")

        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val canInstallPackages =
                Shizuku.checkRemotePermission("android.permission.INSTALL_PACKAGES") == PackageManager.PERMISSION_GRANTED
            val canDeletePackages =
                Shizuku.checkRemotePermission("android.permission.DELETE_PACKAGES") == PackageManager.PERMISSION_GRANTED
            Log.d(
                TAG,
                "canInstallPackages: $canInstallPackages, canDeletePackages: $canDeletePackages"
            )
            return canInstallPackages && canDeletePackages

        } else {
            Log.d(TAG, "Requesting Shizuku permission via notification")

            // Send notification with action to open the MainActivity
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.registerNotificationChannel(
                NOTIFICATION_CHANNEL,
                "Miscellaneous",
                "Miscellaneous notifications",
                NotificationImportanceCompat.IMPORTANCE_DEFAULT
            )

            val intent = Intent(this, RequestPermissionActivity::class.java)
            val pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo_symbolic)
                .setContentTitle(getString(R.string.permission_required))
                .setContentText(getString(R.string.permission_required_detail))
                .setContentIntent(pendingIntent).setAutoCancel(true).build()

            notificationManager.notify(1, notification)

            // Also request the permission since Android 13 may block the notification
            Shizuku.requestPermission(42069)

            return false
        }
    }

    private fun getInstallerPackageName(requestedInstallerPackageName: String?): String {
        val isRoot = Shizuku.getUid() == 0
        return if (isRoot || requestedInstallerPackageName == null) {
            "com.android.shell"
        } else {
            requestedInstallerPackageName
        }
    }

    private fun getPackageInstaller(
        iPackageInstaller: IPackageInstaller,
        requestedInstallerPackageName: String? = null
    ): PackageInstaller {
        val isRoot = Shizuku.getUid() == 0
        val userId = if (isRoot) Process.myUserHandle().hashCode() else 0

        // The reason for use "com.android.shell" as installer package under adb is that
        // getMySessions will check installer package's owner
        val installerPackageName = getInstallerPackageName(requestedInstallerPackageName)
        return ShizukuPackageInstallerUtils.createPackageInstaller(
            iPackageInstaller, installerPackageName, userId, application
        )
    }

    private fun installPackageImpl(
        packageURI: Uri,
        requestedInstallerPackageName: String?,
        callback: IPrivilegedCallback?
    ) {
        Log.d(TAG, "installPackage()")

        if (!accessProtectionHelper.isCallerAllowed || !hasPrivilegedPermissionsImpl()) {
            return
        }

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        val iPackageInstaller = ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller()
        val packageInstaller = getPackageInstaller(iPackageInstaller, requestedInstallerPackageName)

        lateinit var session: PackageInstaller.Session
        try {
            val sessionId = packageInstaller.createSession(params)
            val iSession = IPackageInstallerSession.Stub.asInterface(
                ShizukuBinderWrapper(
                    iPackageInstaller.openSession(sessionId).asBinder()
                )
            )
            session = ShizukuPackageInstallerUtils.createSession(iSession)

            val buffer = ByteArray(65536)

            val input = contentResolver.openInputStream(packageURI)
            val out = session.openWrite("PackageInstaller", 0, -1 /* sizeBytes, unknown */)
            try {
                var c: Int
                while (input!!.read(buffer).also { c = it } != -1) {
                    out.write(buffer, 0, c)
                }
                session.fsync(out)
            } finally {
                IoUtils.closeQuietly(input)
                IoUtils.closeQuietly(out)
            }

            // Create a PendingIntent and use it to generate the IntentSender
            val broadcastIntent = Intent(BROADCAST_ACTION_INSTALL)
            val pendingIntent = PendingIntent.getBroadcast(
                this@PrivilegedService,
                sessionId,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            session.commit(pendingIntent.intentSender)

        } catch (e: IOException) {
            Log.d(TAG, "Failure", e)
            Toast.makeText(this@PrivilegedService, e.localizedMessage, Toast.LENGTH_LONG).show()
        } finally {
            IoUtils.closeQuietly(session)
        }

        mCallback = callback
    }

    @SuppressLint("MissingPermission")
    private fun deletePackageImpl(packageName: String, callback: IPrivilegedCallback) {
        Log.d(TAG, "deletePackage()")

        if (!accessProtectionHelper.isCallerAllowed || !hasPrivilegedPermissionsImpl()) {
            return
        }

        mCallback = callback

        val iPackageInstaller = ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller()
        val packageInstaller = getPackageInstaller(iPackageInstaller)

        // Create a PendingIntent and use it to generate the IntentSender
        val broadcastIntent = Intent(BROADCAST_ACTION_UNINSTALL)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }

    private fun getInstalledPackagesImpl(flagsIn: Int): List<PackageInfo> {
        Log.d(TAG, "getInstalledPackages()")

        var flags = flagsIn
        if (Build.VERSION.SDK_INT >= 29) {
            val matchStaticSharedLibraries = matchStaticSharedLibraries
            if (matchStaticSharedLibraries != null) {
                flags = flags or matchStaticSharedLibraries
            }
        }
        @Suppress("DEPRECATION") return packageManager.getInstalledPackages(flags)
    }

    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val returnCode = intent.getIntExtra(
                EXTRA_LEGACY_STATUS, PackageInstaller.STATUS_FAILURE
            )
            val packageName = intent.getStringExtra(
                PackageInstaller.EXTRA_PACKAGE_NAME
            )
            Log.d(
                TAG, "Received broadcast for package $packageName with return code $returnCode"
            )
            try {
                mCallback?.handleResult(packageName, returnCode)
            } catch (e1: RemoteException) {
                Log.e(TAG, "RemoteException", e1)
            }
        }
    }


    private val binder: IPrivilegedService.Stub = object : IPrivilegedService.Stub() {
        override fun hasPrivilegedPermissions(): Boolean {
            return accessProtectionHelper.isCallerAllowed && hasPrivilegedPermissionsImpl()
        }

        override fun installPackage(
            packageURI: Uri,
            flags: Int,
            requestedInstallerPackageName: String?,
            callback: IPrivilegedCallback?
        ) {
            installPackageImpl(packageURI, requestedInstallerPackageName, callback)
        }

        override fun deletePackage(packageName: String, flags: Int, callback: IPrivilegedCallback) {
            deletePackageImpl(packageName, callback)
        }

        override fun getInstalledPackages(flagsIn: Int): List<PackageInfo> {
            return getInstalledPackagesImpl(flagsIn)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        accessProtectionHelper = AccessProtectionHelper(this)

        val intentFilter = IntentFilter()
        intentFilter.addAction(BROADCAST_ACTION_INSTALL)
        registerReceiver(
            mBroadcastReceiver, intentFilter, BROADCAST_SENDER_PERMISSION, null /*scheduler*/
        )
        val intentFilter2 = IntentFilter()
        intentFilter2.addAction(BROADCAST_ACTION_UNINSTALL)
        registerReceiver(
            mBroadcastReceiver, intentFilter2, BROADCAST_SENDER_PERMISSION, null /*scheduler*/
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBroadcastReceiver)
    }

    companion object {
        const val TAG = "PrivilegedExtension"
        private const val BROADCAST_ACTION_INSTALL =
            "org.fdroid.fdroid.privilegedExtension.ACTION_INSTALL_COMMIT"
        private const val BROADCAST_ACTION_UNINSTALL =
            "org.fdroid.fdroid.privilegedExtension.ACTION_UNINSTALL_COMMIT"
        private const val BROADCAST_SENDER_PERMISSION = "android.permission.INSTALL_PACKAGES"
        private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"

        /**
         * Get private constant.
         *
         * @see [PackageManager.MATCH_STATIC_SHARED_LIBRARIES](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r31/core/java/android/content/pm/PackageManager.java.L530)
         */
        @Suppress("KDocUnresolvedReference")
        @JvmStatic
        val matchStaticSharedLibraries: Int?
            @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi") get() {
                try {
                    return try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            throw NoSuchElementException()
                        }

                        val allStaticFields =
                            HiddenApiBypass.getStaticFields(PackageManager::class.java)
                        (allStaticFields.first {
                            it is Field && it.name == "MATCH_STATIC_SHARED_LIBRARIES"
                        } as Field).getInt(null)

                    } catch (e: NoSuchElementException) {
                        val declaredField =
                            PackageManager::class.java.getDeclaredField("MATCH_STATIC_SHARED_LIBRARIES")
                        declaredField[null] as Int
                    }
                } catch (e: NoSuchFieldException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: ClassCastException) {
                    e.printStackTrace()
                }
                return null
            }
    }
}