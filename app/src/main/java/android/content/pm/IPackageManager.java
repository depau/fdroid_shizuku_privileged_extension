package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

// Adapted from
// https://github.com/RikkaApps/Shizuku-API/tree/21f9561/demo-hidden-api-stub/src/main/java/android/content/pm

public interface IPackageManager extends IInterface {
    IPackageInstaller getPackageInstaller()
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}