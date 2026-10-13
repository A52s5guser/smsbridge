# SMSBridge

I had a spare phone with no SIM and wanted it to work like a second phone. SMSBridge links two Android phones over Bluetooth so the one without a SIM can send and receive texts and calls through the one that has a SIM. No internet, no accounts, no servers.

## How I use it

One phone holds the SIM (I call it the sender). The other phone has no SIM (the receiver). They talk to each other over Bluetooth.

- Send texts from the SIM-less phone. They go out through the sender's SIM.
- Incoming texts show up on both phones.
- Incoming calls ring on both phones. You can answer or reject from either side.
- Contacts sync from the sender so names show up properly.

I built it around a Galaxy S25 Plus (no SIM) and a Galaxy A52s (has the SIM).

## Getting it running

You need both phones paired over Bluetooth first. Do that in the system Bluetooth settings.

1. Install the APK on both phones (see Releases).
2. Open the app on both and allow the permissions it asks for.
3. On each phone, go to Settings in the app. Pick this phone's role: Sender if it has the SIM, Receiver if it doesn't.
4. Still in Settings, tap your other phone under Paired phone, then hit Start on both phones.

The sender needs to stay within Bluetooth range of the receiver. That is the whole trick and also the main limitation.

## Notes

- The receiver runs without a persistent notification. If you swipe the app away from recents it stops, so leave it running.
- Message history and contacts are stored on each device only. Nothing leaves the phones except over your own Bluetooth link.
- Built with plain Android Views and Material 3 components. Min SDK 33.

## Build it yourself

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs the Android SDK with compileSdk 36. The Material library pulls in from Google Maven on first build.
