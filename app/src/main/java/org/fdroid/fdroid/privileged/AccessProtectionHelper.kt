/*
 * Copyright (C) 2016 Dominik Schürmann <dominik@dominikschuermann.de>
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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import android.util.Pair
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class AccessProtectionHelper @JvmOverloads internal constructor(
    context: Context,
    private val whitelist: Set<Pair<String, String>> = ClientWhitelist.whitelist
) {
    private val pm: PackageManager = context.packageManager

    /**
     * Checks if process that binds to this service (i.e. the package name corresponding to the
     * process) is in the whitelist.
     *
     * @return true if process is allowed to use this service
     */
    val isCallerAllowed: Boolean
        get() = isUidAllowed(Binder.getCallingUid())

    private fun isUidAllowed(uid: Int): Boolean {
        val callingPackages = pm.getPackagesForUid(uid)
            ?: throw RuntimeException("Should not happen. No packages associated to caller UID!")

        // is calling package allowed to use this service?
        // NOTE: No support for sharedUserIds
        // callingPackages contains more than one entry when sharedUserId has been used
        // No plans to support sharedUserIds due to many bugs connected to them:
        // http://java-hamster.blogspot.de/2010/05/androids-shareduserid.html
        val currentPkg = callingPackages[0]
        return isPackageAllowed(currentPkg)
    }

    fun isPackageAllowed(packageName: String): Boolean {
        Log.d(
            PrivilegedService.TAG,
            "Checking if package is allowed to access privileged extension: $packageName"
        )
        try {
            val currentPackageCert = getPackageCertificate(packageName)
            for (whitelistEntry in whitelist) {
                val whitelistPackageName = whitelistEntry.first as String
                val whitelistHashString = whitelistEntry.second as String

                val digest = MessageDigest.getInstance("SHA-256")

                val packageHash = digest.digest(currentPackageCert)
                val packageHashString = BigInteger(1, packageHash).toString(16)

                val packageNameMatches = packageName == whitelistPackageName
                val packageCertMatches = whitelistHashString == packageHashString

                Log.d(PrivilegedService.TAG, "Allowed package name: $whitelistPackageName")
                Log.d(PrivilegedService.TAG, "Package name:         $packageName")
                Log.d(PrivilegedService.TAG, "Package name allowed: $packageNameMatches")

                Log.d(PrivilegedService.TAG, "Allowed cert hash:    $whitelistHashString")
                Log.d(PrivilegedService.TAG, "Package cert hash:    $packageHashString")
                Log.d(PrivilegedService.TAG, "Cert hash allowed:    $packageCertMatches")

                if (packageNameMatches && packageCertMatches) {
                    Log.d(
                        PrivilegedService.TAG,
                        "Package is allowed to access the privileged extension!"
                    )
                    return true
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e.message)
        }
        Log.e(
            PrivilegedService.TAG,
            "Package is NOT allowed to access the privileged extension!"
        )
        return false
    }

    @Suppress("DEPRECATION")
    private fun getPackageCertificate(packageName: String): ByteArray {
        return try {
            // we do check the byte array of *all* signatures
            @SuppressLint("PackageManagerGetSignatures") val pkgInfo =
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)

            // NOTE: Silly Android API naming: Signatures are actually certificates
            val certificates = pkgInfo.signatures
            val outputStream = ByteArrayOutputStream()
            for (cert in certificates) {
                outputStream.write(cert.toByteArray())
            }

            // Even if an apk has several certificates, these certificates should never change
            // Google Play does not allow the introduction of new certificates into an existing apk
            // Also see this attack: http://stackoverflow.com/a/10567852
            outputStream.toByteArray()
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e.message)
        } catch (e: IOException) {
            throw RuntimeException(e.message)
        }
    }
}