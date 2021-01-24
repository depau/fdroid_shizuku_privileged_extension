package org.fdroid.fdroid.privileged;

import android.os.Build;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class PrivilegedServiceTest {
    @Test
    public void testGetMatchStaticSharedLibraries() {
        Integer flag = PrivilegedService.getMatchStaticSharedLibraries();
        if (Build.VERSION.SDK_INT < 29) {
            assertEquals(null, flag);
        } else {
            assertNotEquals(null, flag);
            // Must match PackageManager.MATCH_STATIC_SHARED_LIBRARIES
            assumeTrue(flag == 0x04000000);
        }
    }
}
