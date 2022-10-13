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

import android.app.Instrumentation
import android.util.Pair
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AccessProtectionHelperTest {
    private lateinit var instrumentation: Instrumentation

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
    }

    @Test
    fun testAccessAllowed() {
        val accessProtectionHelper = AccessProtectionHelper(
            instrumentation.context, testCorrectWhitelist
        )

        // system app:
        Assert.assertTrue(accessProtectionHelper.isPackageAllowed("android"))
        // included app:
        Assert.assertTrue(accessProtectionHelper.isPackageAllowed("com.android.camera"))
    }

    @Test
    fun testAccessDisallowed() {
        // uses normal whitelist
        val accessProtectionHelper = AccessProtectionHelper(instrumentation.context)

        // system app:
        Assert.assertFalse(accessProtectionHelper.isPackageAllowed("android"))
        // included app:
        Assert.assertFalse(accessProtectionHelper.isPackageAllowed("com.android.camera"))
    }

    @Test
    fun testAccessWrongWhitelist() {
        // uses normal whitelist
        val accessProtectionHelper =
            AccessProtectionHelper(instrumentation.context, testWrongWhitelist)

        // system app:
        Assert.assertFalse(accessProtectionHelper.isPackageAllowed("android"))
        // included app:
        Assert.assertFalse(accessProtectionHelper.isPackageAllowed("com.android.camera"))
    }

    companion object {
        var testCorrectWhitelist = setOf(
            // Android System Apps:
            Pair("android", "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"),
            // Android included Apps:
            Pair(
                "com.android.camera",
                "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"
            )
        )
        var testWrongWhitelist = setOf(
            // wrong package name
            Pair("NOPE", "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8")
        )
    }
}