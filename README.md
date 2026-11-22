# Openmacro

A free, open-source and ad-free automation app for Android, in the spirit of MacroDroid.

A macro consists of a **trigger** and one or more **actions**. Currently supported:

| Triggers | Actions |
|---|---|
| Bluetooth device connected | Turn on Wi‑Fi hotspot *(Shizuku)* |
| | Turn on mobile data *(Shizuku)* |

## Shizuku

Toggling the Wi‑Fi hotspot and mobile data needs privileges a normal app doesn't have.
Openmacro gets them through [Shizuku](https://shizuku.rikka.app/): install Shizuku, start it
(wireless debugging or root), and grant Openmacro access when asked.

## How it works

- Macros are stored as JSON in the app's private storage.
- While at least one macro is enabled, a foreground service (with a low-priority
  notification) listens for triggers. It is restarted after reboot.
- Privileged actions call the system's `tethering` and `phone` services through Shizuku,
  running with the shell user's permissions.

## Building

```
./gradlew assembleDebug
```

## License

[Mozilla Public License 2.0](LICENSE)
