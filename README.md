# SMSBridge

Turn a spare phone into a free second phone — send, receive, and answer SMS and calls on a SIM-less phone, fully offline over Bluetooth.

## What it does

SMSBridge links two Android phones with a direct Bluetooth connection and no internet:

- **Sender** — the phone with the SIM card and cellular service
- **Receiver** — any old phone with no SIM (this project was built around an S25 Plus + A52s)

The receiver becomes a real messaging/calling device: it sends texts that go out on the sender's SIM, rings when someone calls the sender's number, and shows you every conversation.

## Features

- **Offline-only** — no servers, no accounts, no internet; works via a Bluetooth Classic RFCOMM link
- **Compose & send SMS from the SIM-less phone** — messages are forwarded to the sender and sent through its SIM, with delivery-status tracking
- **Receive SMS** — incoming texts ring/notify on the receiver and appear in per-person chats
- **Contacts sync** — the sender's contact book is mirrored over the link in real time
- **Call mirroring & control** — incoming calls ring on the receiver (sound mode = rings, vibrate mode = vibrates, silent = quiet); answer or reject remotely from the receiver
- **Per-person conversations** — Chats, Contacts, and Calls tabs backed by local stores, with thread view, history, and one-tap call
- **Pure background receiver** — the receiver can run silently in the background with no persistent notification
- **Liquid-glass UI** — day/night theming, translucent glass panels, no app dependencies

## How it works

```
Sender (has SIM) ──< Bluetooth Classic RFCOMM >── Receiver (no SIM)
     │      │     JSON frames over SPP            │
   calls   SMS            ▲                                 
     │       │            │
   CallMonitor       IncomingSMS/reply → sent on sender's SIM
   (rings ↔ receiver)
```

Both devices run a JSON-frame protocol (`Protocol.java`) over a pairing-less RFCOMM socket (`BtController`). The sender monitors its cellular events with `CallMonitor` and forwards them as frames; the receiver acts on them (`CallRinger`, notifications, stores). Calls are answered/rejected from the receiver by sending the matching command back over the link.

## Build

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # on both phones
```

Requires Android Studio / SDK 36 (compileSdk), minSdk 33.

## Setup

1. Install SMSBridge on both phones.
2. Grant the requested permissions on both devices.
3. On the sender, pair it as the default dialer if you want call control.
4. Pick the other phone in the peer spinner and tap **START** on each side.

## Permissions used

`RECEIVE_SMS` / `SEND_SMS` (forwarding), `BLUETOOTH_CONNECT` (the link), `READ_PHONE_STATE` / `READ_CALL_LOG` / `CALL_PHONE` (call events & control), `READ_CONTACTS` (contact sync), `RECORD_AUDIO` (ring + call audio). The bootloader-locked / sim-locked hardware notes are in the code repository history only — no secrets or identifiers ship in the app.

## Reference material

`references/` contains public Android sample code (Bluetooth Chat, SMS receiver) used for initial wire-protocol research. Google sample code is Apache 2.0.