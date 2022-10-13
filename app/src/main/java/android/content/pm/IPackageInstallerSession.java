package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

// Adapted from
// https://github.com/RikkaApps/Shizuku-API/tree/21f9561/demo-hidden-api-stub/src/main/java/android/content/pm

public interface IPackageInstallerSession extends IInterface {

    abstract class Stub extends Binder implements IPackageInstallerSession {

        public static IPackageInstallerSession asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}