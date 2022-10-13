package android.content.pm;

import java.util.List;

// Adapted from
// https://github.com/RikkaApps/Shizuku-API/tree/21f9561/demo-hidden-api-stub/src/main/java/android/content/pm

abstract class BaseParceledListSlice<T> {
    public List<T> getList() {
        throw new RuntimeException("STUB");
    }
}