# YTMon — Android client

A native Android client for the **ytmon** YouTube monitor server. The server
runs separately (typically under Termux on the same phone); this app is only the
user interface. It does not download, monitor or store anything itself — every
action is an API call to the server.

## Requirements

- **JDK 17** (the build targets Java 17)
- **Android SDK** with platform **35** and build-tools 35
- Gradle is **not** required — use the bundled wrapper (`./gradlew`)

## Build

```bash
git clone https://github.com/ChrisJoseph85/ytmon-android.git
cd ytmon-android
./gradlew assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Other useful tasks:

```bash
./gradlew assembleRelease     # unsigned release build
./gradlew clean
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest   # wire-level checks for the models/parsers
```

If Gradle cannot find your SDK, create `local.properties` in the repo root:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Pointing it at a server

Open **Settings → Server address**.

| Field | Meaning |
|---|---|
| **Server URL** | e.g. `127.0.0.1:8765`. The scheme and `/api/v1` are added for you. |
| **Bearer token** | Optional. Only needed for a non-loopback address. |

Both are stored on the device, so you enter them once.

Defaults to `http://127.0.0.1:8765/api/v1/` — the server's own loopback
address, which it trusts without a token.

### Loopback vs LAN

The server treats `127.0.0.1` / `::1` as trusted and needs no credentials. Any
other address is a **remote** client and returns `401` unless a bearer token is
supplied. Create one from localhost on the server:

```bash
curl -X POST http://127.0.0.1:8765/api/v1/tokens
```

That endpoint refuses remote callers by design, which is why the token has to be
created in Termux rather than from this app.

## Screens

| Screen | What it does |
|---|---|
| **Library** | List, search, filter, sort. Swipe a row to mark watched, long-press for bulk pin/delete. |
| **Player** | Video and audio playback. Branches on `media_type`, never the file extension. Pin, share, export. |
| **Add link** | Preview first (queues nothing), then confirm to download, with live progress. |
| **Sources** | Playlists and Watch Later, per-source quality overrides, manual scan. |
| **Channels** | CRUD, per-channel quality, **auto/manual + schedule**, subscription import, export CSV+JSON. |
| **Browser** | Folder tree of the library with a working back stack. |
| **Downloads** | Queue with live progress, retry and cancel. |
| **Settings** | Quality, monitoring, retention, storage, server address. |

## Subscriptions and per-channel schedules

Every channel carries a **mode** (`auto` / `manual`) and an optional download
schedule (days of week + `HH:MM` local).

- **Auto** — uploads are downloaded as they are discovered.
- **Manual** — uploads are still *discovered* and land in the library with status
  `discovered`. Nothing is downloaded until you fetch one (Library → tap → download).
- **Schedule** — gates the *download* only. Discovery never stops: a channel
  scheduled for Monday still lists its Tuesday upload on Tuesday.
- `due_now` / `next_due` are computed by the server and shown as sent. The app
  never recalculates them.
- Long-press a channel to multi-select, then **Make auto** / **Make manual** /
  **Set schedule** applies to the whole selection at once.

**Import YouTube subscriptions** takes a Google Takeout `subscriptions.json`
(paste, clipboard or file picker) or the server's own feed. Takeout is the
default because it needs nothing configured on the server; the feed option is
only *enabled* when `GET /subscriptions/status` reports cookies, otherwise the
screen says the server needs `YTMON_COOKIES_FILE` instead of showing a raw
`import_failed`. Imported channels deliberately arrive in **manual** mode, so
importing 300 subscriptions does not start 300 downloads.

The server keeps a copy of every import, so the channel list survives the
YouTube cookies expiring — the "last imported …" line on the Channels screen is
telling the user exactly that.

## Notes on behaviour

Some things are deliberate and worth not "fixing" by accident:

- **The app never sends an `Origin` header.** The server answers
  `403 origin_not_allowed` to any request carrying one.
- **Thumbnails come from `GET /videos/{id}/thumbnail`**, never from
  `Video.thumbnail_url` — that field points at YouTube and fails offline.
- **Quality is a ceiling.** `720p` means 720p *or lower*, so the UI shows
  `quality_height` (what you actually got) and labels the picker "720p (max)".
- **`unavailable` items are never retried.** The content is gone; it is not the
  user's fault.
- **Deletion always previews first** with `dry_run` and shows the real file
  count. There is no trash and no undo.
- **A scan that reports `held` did not fail.** Held means the uploads were found
  but the channel was not due, so they were not downloaded. Reported as
  information, never as an error.
- **`/channels/{id}` and `POST /channels/policy/bulk` both address channels by
  `"UC…"` / `"@handle"`**, never by the numeric id.
- **Polling only runs while work is outstanding**, then stops.
- **`/videos` has no `media_type` or `pinned` query parameter** (unknown params
  are silently ignored), so Pinned uses `GET /videos/pinned` and media type is
  filtered client-side.

## Tech

Kotlin · Jetpack Compose (Material 3) · Retrofit + OkHttp ·
kotlinx.serialization · Media3 ExoPlayer · Coil · WorkManager

`minSdk 29` — the floor for scoped `MediaStore` writes, so saving to Movies/Music
needs no storage permission.