# F-Droid Shizuku Privileged Extension

This is a port of the
official [F-Droid Privileged Extension](https://gitlab.com/fdroid/privileged-extension) to
[Shizuku](https://shizuku.rikka.app/), an app that makes it easy to grant apps ADB permissions.

When the app is installed, F-Droid will automatically detect it and use it to perform
non-interactive app installations.

## Installation

Use the F-Droid repo: https://depau.github.io/fdroid_shizuku_privileged_extension/fdroid/repo/

Make sure you open the app after installing it, so it can request the required permissions.

## Android 13 gotchas

Android 13 doesn't allow new apps to send notifications, so the app can't ask you to grant the
Shizuku permission when needed.

To work around this, make sure you open the app once and grant the permission manually.

## License

This app is a fork of the
official [F-Droid Privileged Extension](https://gitlab.com/fdroid/privileged-extension), which is
licensed under the Apache License, Version 2.0.

Some parts of the codebase are based on
the [Shizuku API demo app](https://github.com/RikkaApps/Shizuku-API/tree/master/demo), which is
licensed under the MIT license.

----

Here's relevant parts of the original README

# F-Droid Privileged Extension

This enables F-Droid to install and delete apps without needing "Unknown Sources" to be enabled (
e.g. just like Google Play does). It also enables F-Droid to install updates in the background
without the user having to click "install".

When F-Droid is installed as a normal Android app, installing, updating, and removing apps can only
be done by sending requests to the Android operating system. F-Droid cannot execute these operations
itself. Android shows a screen on every install/update/delete to confirm this is what the user
actually wants. This is a security feature of Android to prevent apps or websites from installing
malware without user intervention.

F-Droid Privileged Extension grants elevated permissions to F-Droid, which allows it to do installs
and uninstalls without needing user approval. It gives only F-Droid access to its install and delete
commands. In order for F-Droid Privileged Extension to get these "privileged" powers, it must be
installed as part of your system by either being flashed as an _update.zip_ or by being built into
an Android device or ROM. On Android 4 and older, it can be installed directly if you have root on
your device.

## Design

F-Droid Privileged Extension is designed on the principals of "least privilege", so that elevated
powers are only granted where they are absolutely needed, and those powers are limited as much as
possible. Therefore, the code that runs with increased powers is very small and easy to audit. This
is in contrast to how typical built-in app stores are granted all of the privileges available to a "
system priv-app".

Advantages of this design:

* "Unknown Sources" can remain disabled
* Can easily be built into devices and ROMs
* Reduced disk usage in the system partition
* System updates don't remove F-Droid

## Building with Gradle

Build a complete "update.zip" to flash to a device to install F-Droid and the Privileged Extension:

    ./create_ota.sh debug binaries

Build an "update.zip" to flash to a device to install just the Privileged Extension:

    ./create_ota.sh debug

Build the standalone APK using:

    ./gradlew assembleRelease

In order to have final, signed release versions that are ready for installing, a release signing key must be set up in _signing.properties_ with these contents:

    key.store=/path/to/release-keystore.jks
    key.store.password=mysecurestorepw
    key.alias=release
    key.alias.password=mysecurekeypw

## Supporting a different app

It is possible to use Privileged Extension with any app.  To do that,
make a "whitelabel" build of Privileged Extension that includes the
_Application ID_, key fingerprint, and app name for the app that the
custom build will support.  These are set by the script below, and
should be committed to a fork git repo:

```bash
$ export ApplicationID=my.app
$ export AppName=MyApp
sed -i "s,org.fdroid.fdroid.privileged,$ApplicationID,g" \
    create_ota.sh app/src/main/scripts/*
$ sed -i "s,F-Droid,$AppName,g" \
    create_ota.sh app/build.gradle app/src/main/scripts/* \
    app/src/main/res/values*/strings.xml
```

## Testing in the Emulator

To test the Privileged Extension in the emulator, one has to modify
the _system.img_ file. It is located under the Android SDK install
path.  For example, here is the `android-23` (Marshmallow, 6.0) x86_64
image with Google APIs:

```
$ANDROID_HOME/system-images/android-23/google_apis/x86_64/system.img
```

To install it, first build the standalone APK, and then run these in
the base directory of this git repo.  This copies the APK into the
right place, and sets up the correct SELinux context.

### _android-14_ through _android-25_

```console
$ ./gradlew assembleDebug
$ mkdir /tmp/system
$ sudo mount -o loop /path/to/system.img /tmp/system
$ sudo mkdir /tmp/system/priv-app/F-DroidPrivilegedExtension
$ sudo cp app/build/outputs/apk/F-DroidPrivilegedExtension-debug.apk \
    /tmp/system/priv-app/F-DroidPrivilegedExtension/F-DroidPrivilegedExtension.apk
$ sudo chcon -R --reference=/tmp/system/app/webview /tmp/system/priv-app/F-DroidPrivilegedExtension
$ sudo umount /tmp/system
```

### _android-26_ and newer

Starting with _android-26_, the _system.img_ files have a different
format that needs to be unpacked before it can be mounted.  It
has to be repacked after mounting as well.  This requires the _simg2img_ and
_make_ext4fs_ utilities.

```console
$ sudo apt-get install android-tools-fsutils
$ ./gradlew assembleDebug
$ simg2img /path/to/system.img system.img.raw
$ mkdir /tmp/system
$ sudo mount -t ext4 -o loop system.img.raw /tmp/system
$ sudo mkdir /tmp/system/priv-app/F-DroidPrivilegedExtension
$ sudo cp app/build/outputs/apk/F-DroidPrivilegedExtension-debug.apk \
    /tmp/system/priv-app/F-DroidPrivilegedExtension/F-DroidPrivilegedExtension.apk
$ sudo chcon -R --reference=/tmp/system/app/webview /tmp/system/priv-app/F-DroidPrivilegedExtension
$ make_ext4fs -s -T -1 -S file_contexts -L system -l 512M -a system system.img.new /tmp/system
$ sudo umount /tmp/system
$ mv system.img.new /path/to/system.img
```

Upon booting the emulator, it should have the Privileged Extension
installed.  It is also possible to install the F-Droid app this way,
or via the normal methods.

## via _adb_ on _android-19_ and older

On old Android versions (4.4 and older), it is possible using only
_adb_, but then each time the emulator is rebooted, it will lose the
changes.  Take a snapshot after completing this process to save the
state.

```console
$ adb -e root
$ adb -e remount
$ adb -e shell mkdir /system/priv-app/F-DroidPrivilegedExtension
$ sudo cp app/build/outputs/apk/F-DroidPrivilegedExtension-debug.apk \
    /tmp/system/priv-app/F-DroidPrivilegedExtension/F-DroidPrivilegedExtension.apk
$ sudo chcon -R --reference=/tmp/system/app/webview /tmp/system/priv-app/F-DroidPrivilegedExtension
```
