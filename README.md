# 来电速查 (CallLogStats)

Android app that shows local call statistics for the current number in a floating overlay
during incoming, outgoing and active calls. All data stays on the device; the app does not
declare the `INTERNET` permission.

## Requirements

- JDK 21 (`brew install openjdk@21`)
- Android SDK with `platforms;android-36` and `build-tools;36.0.0`

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
./gradlew lintDebug testDebugUnitTest
```

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## HyperOS setup

HyperOS requires manual configuration for reliable background call detection:

1. **Autostart**: allow 来电速查 to start automatically, otherwise monitoring will not
   resume after a reboot.
2. **Battery policy**: open the app's battery details and select **无限制 (Unlimited)**.
   The default "smart restriction" blocks `PHONE_STATE` delivery in the background even
   while the foreground service is running.
3. **Overlay**: allow display over other apps.
4. **Notifications**: allow notifications so the monitoring notification is visible.

## Documentation

- `docs/requirements.md` — product requirements
- `docs/decisions/` — architecture and behavior decisions
