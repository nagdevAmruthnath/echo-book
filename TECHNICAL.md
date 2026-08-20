# EchoBooks — Technical Documentation

EchoBooks is a native Android (Kotlin + Jetpack Compose) app that generates a
complete audiobook — story text **and** narrated audio — from a short idea.
Generation pipelines call an LLM over the network (OpenRouter), while narration
is produced **on-device** by sherpa-onnx (Piper VITS) so playback never needs a
connection. Everything is stored locally in Room + app-private storage.

---

## 1. High-level architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                        UI layer (Compose)                          │
│  LibraryScreen │ CreateScreen │ GeneratingScreen │ ImportScreen    │
│  PlayerScreen │ SettingsScreen │ MiniPlayerView                     │
│  Components: GlassBackground/Card/Button/ActionButton/Slider/...    │
└───────────────┬───────────────────────────────────┬────────────────┘
                │ nav (NavHost)                      │
┌───────────────▼──────────────────────────────┐    │
│            ViewModel layer                   │    │
│  Library │ Generation │ Import │ Player      │    │
│  MiniPlayer │ Settings                       │    │
└───────┬──────────────────┬──────────────────┘    │
        │                  │ settings (DataStore)  │
┌───────▼────────┐ ┌───────▼─────────┐ ┌──────────▼───────────┐
│ Domain layer   │ │ TTS layer       │ │ Audio layer          │
│ ChapterGenerator│ │ TtsEngine       │ │ PlayerController     │
│ OpenRouterClient│ │ AudioConverter  │ │ PlaybackService      │
│ EbookParser     │ │ TtsVoice(s)     │ │ (Media3 session)    │
│ SpeechToText    │ │ sherpa-onnx JNI │ └──────────┬───────────┘
└───────┬────────┘ └───────┬─────────┘            │
        │                  │ m4a/wav segments on disk (app_books/{bookId}/)
┌───────▼──────────────────▼──────────────────────▼───────────────┐
│                        Data layer                                 │
│  Room DB (books, chapters, bookmarks)  ·  DataStore (settings)    │
└───────────────────────────────────────────────────────────────────┘
```

### Threading model
- **GenerationViewModel**: outline + chapter writing run as a coroutine;
  chapter text flows through a `Channel` to a parallel TTS consumer so writing
  and narration overlap (`Channel(2)` back-pressure, capacity 2).
- **OpenRouterClient**: all network I/O on `Dispatchers.IO`.
- **TtsEngine**: synthesis on `Dispatchers.Default`; a `Mutex` guards the
  single sherpa-onnx engine instance.
- **AudioConverter**: WAV/FFmpeg-style transforms are plain blocking file I/O.

---

## 2. Module / package map

| Package | Responsibility |
|---|---|
| `com.echobooks.app.core` (`EchoBooksApp.kt`) | `Application`; owns singletons: `AppDatabase`, `SettingsStore`, `OpenRouterClient`, `ChapterGenerator`, `TtsEngine` |
| `com.echobooks.app.audio` | `PlaybackService` (Media3 `MediaSessionService`), `PlayerController` (`MediaController`) |
| `com.echobooks.app.data` | Room entities + DAOs, `EbookParser`, `SettingsStore` (DataStore), `ModelsCatalog` |
| `com.echobooks.app.llm` | `OpenRouterClient` (streaming SSE), `ChapterGenerator` (outline + chapter drafting) |
| `com.echobooks.app.speech` | `SpeechToText` (idea dictation) |
| `com.echobooks.app.tts` | `TtsEngine`, `TtsVoice`/`TtsVoices`, `AudioConverter` |
| `com.echobooks.app.ui` | All Compose screens + `AppNavHost` routes + theming |
| `com.echobooks.app.ui.components` | Reusable glassmorphism Compose primitives |
| `com.echobooks.app.ui.viewmodel` | One ViewModel per screen |
| `com.k2fsa.sherpa.onnx` | Bundled sherpa-onnx JNI bindings (MIT-licensed vendor code) |

---

## 3. Data model (Room)

Database `echobooks.db`, version 1. All entities in `data/Models.kt`.

### `books`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK auto | |
| `title`, `author`, `genre`, `brief` | String | `brief` = user's story idea |
| `lengthMin` | Int | requested target duration |
| `createdAt` | Long | epoch ms |
| `coverHue` | Int | HSL hue used by `GradientCover` |
| `chapterCount` | Int | |
| `progressItem` | Int | last media item index |
| `progressMs` | Long | position within item |
| `progressFraction` | Float | 0..1 of whole book |
| `durationMs` | Long | total audio duration |
| `completed` | Boolean | false while generating |

### `chapters`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK auto | |
| `bookId` | Long FK-indexed | |
| `chapter_index` | Int | 0-based |
| `title` | String | from outline |
| `text` | String | full LLM-written prose |
| `segments` | String | JSON array `[{file, d}]` for audio files |
| `durationMs` | Long | sum of segment durations |

### `bookmarks`
| Column | Type |
|---|---|
| `id` Long PK, `bookId` Long FK-indexed, `itemIndex` Int, `positionMs` Long |
| `chapterTitle`, `label` String, `createdAt` Long |

### Settings (DataStore — `echobooks_settings`)
`api_key`, `model`, `speed` (Float), `pitch` (Float), `voice`, `nsfw`
(Boolean), `onboarded`. Default model is `meta-llama/llama-3.3-70b-instruct`.

### Storage layout
```
getDir("books")/{bookId}/
    ch1_s0.m4a, ch1_s1.m4a …        # per-chapter audio segments
getDir("tts")/{voiceId}/            # downloaded voice model (onnx + tokens + espeak data)
```

---

## 4. Generation pipeline

Orchestrated in `GenerationViewModel.start(spec)` (ui/viewmodel/GenerationViewModel.kt).

### 4.1 Outline (`ChapterGenerator.generateOutline`)
1. **Chapter count** = `max(1, round(lengthMin / 10.0))` → ~10 min of audio / chapter.
2. Single non-streaming `completeChat` requesting raw JSON
   `{title, author, chapters:[{title, summary}]}`.
3. If JSON parse fails, retry once with stricter instructions (`temperature` 0.8 → 0.6).
4. On second failure, synthesise a fallback outline:
   title from the idea (truncated to 40 chars), `"Chapter N"` entries.

### 4.2 Chapters (`ChapterGenerator.generateChapter`)
For each chapter index:
1. `streamChat` with a system prompt enforcing prose-only output, and a user
   prompt containing the full outline + the tail of the previous chapter
   (last 3000 chars) for continuity.
2. **Length enforcement** — target `1500–1750 words` (~10 min narration).
   A `while (wordCount(text) < 1500 && passes < 2)` loop requests an expansion:
   *"Continue the same scene… do NOT repeat earlier text"* with the last
   800 chars of the draft as the hook; the result is appended with
   `"\n\n"`. `maxToken = 8192` per pass.
3. `wordCount` counts whitespace-delimited tokens.

### 4.3 Writing ↔ narration overlap
Chapters are not needed in order for TTS, so generation writes ahead:
```
writer → channel.send(i, text)     (capacity 2)
tts    ← channel.receive() → synthesize → insert Chapter row → narrated++
```
Generation milestones are surfaced as `Phase { Idle, Outline, Writing,
Narrating, Done, Error }` and a per-chapter word counter in the UI detail line.

### 4.4 Clean-up
If generation is cancelled or errors, the partial `books` row, its chapters,
bookmarks, and audio directory are deleted (`cleanup()`).

---

## 5. LLM client (`llm/OpenRouterClient.kt`)

- Endpoint `https://openrouter.ai/api/v1/chat/completions`.
- **Streaming** (`streamChat`): `stream=true`, reads SSE `data:` lines, decodes
  either `delta.content` (streaming) or `message.content` (final), appends via
  `onDelta` while still building the whole string. Stops at `[DONE]` or EOF.
- **Non-streaming** (`completeChat`): `stream=false`, returns first choice.
- OkHttp timeouts: connect 30 s, read 0 (infinite, streaming), write 60 s.
- Errors surface as `OpenRouterException(code, message)`.

### Model routing
`ModelsCatalog` (`data/ModelsCatalog.kt`) provides a curated list of OpenRouter
models with `free`/`nsfw`/`context` metadata. `forFilter(nsfw)` returns the
safe list by default and the uncensored list when the Settings switch is on
(`nsfw` persisted in DataStore). A custom free-text model field overrides the
list. Safe catalog includes e.g. Llama 3.3 70B, GPT-4o-mini, Claude Haiku,
Mistral Small (see file for full 13 entries); NSFW list adds uncensored blends
(Venice, Euryale, MythoMax…).

---

## 6. Speech synthesis (`tts/`)

### 6.1 TTS engine (`TtsEngine.kt`)
- Wraps sherpa-onnx `OfflineTts` (VITS models).
- **ensureReady(voice, speed, pitch, allowDownload, onProgress)**: verifies
  `voiceDir` contains a `.onnx` model, `tokens.txt`, `espeak-ng-data/`; if not,
  downloads the voice `.tar.bz2` from the sherpa-onnx TTS releases and extracts
  it (bzip2 + tar streams, strips the archive's root prefix). Engines are
  single-instance and lazily re-created when the voice changes.
- **synthesizeToFiles(text, dir, prefix, gapMs=800)**:
  1. `splitSegments` chunks text at paragraph boundaries into ≤ 2400-char
     segments (splitting long paragraphs at sentence breaks).
  2. Per segment: `tts.generate(text, speakerId, speed)` → raw WAV.
  3. Adds **800 ms trailing silence** to the final segment of the chapter
     (`AudioConverter.appendSilence`) so chapters breathe apart.
  4. Encodes WAV → M4A via FFmpeg; on failure keeps WAV and deletes the m4a.
  5. Returns `SynthesisResult(files, durationMs, segmentsJson)` used to build
     the `chapters.segments` column.

### 6.2 Voices (`TtsVoice.kt`)
Four Piper voices, fetched from
`https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/`:

| id | label | form |
|---|---|---|
| `en_US-amy-medium` | Amy | American English · female (default) |
| `en_US-lessac-medium` | Lessac | American English · female narrator |
| `en_US-ryan-medium` | Ryan | American English · male |
| `en_GB-alan-medium` | Alan | British English · male |

`byId("")` falls back to Amy. Each tar.bz2 is ~20 MB.

### 6.3 AudioConverter (`tts/AudioConverter.kt`)
- WAV parsing/encoding and length queries, `wavToM4a` (FFmpeg), and
  `appendSilence(wav, extraMs)` which **appends the silence at the byte level**
  (sherpa-onnx cannot inject SSML pauses): it re-reads the WAV header, appends
  zeros (`sampleRate × channels × 2 × ms / 1000` bytes), and patches the RIFF
  chunk-size and `data` chunk-size fields so the file remains a valid WAV.

---

## 7. Audio playback (Media3)

### 7.1 `PlaybackService` (media/audio/PlaybackService.kt)
`MediaSessionService` exported with intent-filter
`androidx.media3.session.MediaSessionService`; declared in the manifest with
`foregroundServiceType="mediaPlayback"` so it keeps running in the background
with a media notification.

### 7.2 `PlayerController`
Binds a `MediaController` to the session (`SessionToken` + `ComponentName`),
then:
- `loadBook(book, chapters, bookDir)` flattens every chapter's segments into a
  linear list of `MediaItem`s (`Uri.fromFile`), records `chapterStarts` (first
  item of each chapter) and a cumulative ms table, then restores the saved
  `progressItem`/`progressMs` via `setMediaItems(items, item, pos)`.
- A 400 ms poller publishes `currentPosition` and book-wide position
  (`cumulative[item] + currentPosition`);
- `seekToBook(ms)`/`findItem` maps book ms → (item, intra-item ms);
- chapter navigation jumps to `chapterStarts[i]`;
- `prevChapter` restarts current chapter if > 5 s in, else previous chapter;
- speed 0.5x–3x via `setPlaybackSpeed`;
- the `Player.Listener` promotes `isPlaying`, item/chapter index, state and errors.

### 7.3 Mini-player
`MiniPlayerViewModel` mirrors the controller's flows in `LibraryScreen`; the
floating card shows title + play/pause (`GlassActionButton(active=true)`) and
navigates to the player on tap.

---

## 8. Import pipeline (`data/EbookParser.kt`, `ui/import/ImportScreen.kt`)

`EbookParser.parse(extension, inputStream, fallbackTitle)` dispatches by suffix:

| Extension | Parsing |
|---|---|
| `.epub` | unzip via `ZipArchiveInputStream`; read `META-INF/container.xml` → OPF → title/creator → manifest/spine in *spine order*; per-doc `<title>` + HTML→text. |
| `.mobi/.azw/.azw3/.prc` | PalmDB record table (`be16`/`be32` helpers); only compression 1 (uncompressed) or 2 (PalmDOC LZ77 — `palmDocDecompress`) supported; reads EXTH metadata (author=100, title=501/503) and the PalmDB name; charset auto (UTF-8 / windows-1252 / ISO-8859-1); strips HTML + PalmDOC markup. Unsupported compression raises a friendly error. |
| `.txt` (default) | UTF-8, BOM-stripped. |

Chapter splitting (`splitIntoChapters`): splits at lines matching
`^chapter|prologue|epilogue|preface|introduction|part|book|section` when ≥ 2
headings; otherwise chunks paragraphs into ≤ 3000-char chapters.

Import writes the same `books`/`chapters`/audio-segments shape as generation,
then reuses TTS + Media3 unchanged. `ImportScreen` uses the Storage Access
Framework picker (`GetContent`-style SAF flow).

---

## 9. UI layer

### 9.1 Navigation (`ui/AppNavHost.kt`)
Routes: `library`, `create`, `generating`, `import`, `player/{bookId}`,
`settings`. `generating`/`import` pop back to the library and deep-link to the
new book's player.

### 9.2 Glass components (`ui/components/Glass.kt`)
- `GlassBackground` — animated gradient backdrop (Compose shader animation).
- `GlassCard`, `GlassButton`, `GlassIconButton`, `GlassTextField`,
  `GlassSlider`, `GlassChip`, `GlassProgressBar`, `GlassActionButton`
  (circular icon button with optional text `badge`; `active` renders a
  Violet→Magenta fill, idle renders 6–18% white glass), `GradientCover`
  (HSL hue → book cover), `hslColor`, `formatTime`, `SectionTitle`.
- Player bottom controls (playback speed / sleep / bookmark / chapters) and
  read-mode (Prev, Next, A−, A+) were converted to `GlassActionButton` icon
  buttons; the floating mini-player's play button uses the `active` gradient
  for visibility.

### 9.3 Screens
- **LibraryScreen** — book cards, completed badge + chapter/duration, import
  entry, floating mini-player.
- **CreateScreen** — idea input (with `SpeechToText` dictation), length/genre
  pickers, cover preview, launch.
- **GeneratingScreen** — outline/writing/narration progress; cancel + cleanup.
- **ImportScreen** — SAF file picker, parser report, TTS progress.
- **PlayerScreen** — big play controls, circular action buttons, chapter
  progress, bookmarks + sleep timer, chapters sheet, **ReadModeContent**
  (LazyColumn of text with A−/A+ font size 14–30 sp, per-chapter Prev/Next).
- **SettingsScreen** — API key, model catalog (± NSFW toggle), voice picker,
  speed/pitch sliders.

---

## 10. Build & dependencies

- **Gradle** 9.x, Kotlin 2.x, KSP; JDK 17 toolchain; Compose + Material3 via BOM;
  `compileSdk/targetSdk = 35`, `minSdk = 26`, `versionCode 1 / "1.0.0"`.
- Key libraries:
  - `androidx.compose.*` (UI, Material3, extended icons)
  - `androidx.navigation:navigation-compose`
  - `androidx.room` (runtime + KTX, KSP compiler)
  - `androidx.datastore:datastore-preferences`
  - `androidx.media3:*` (exoplayer + session) for background playback
  - `com.squareup.okhttp3:okhttp` (LLM calls + voice downloads)
  - `org.apache.commons:commons-compress` (EPUB zip, MOBI tar.bz2)
  - `kotlinx-serialization-json`, `kotlinx-coroutines-android`

### Build commands
```powershell
$env:JAVA_HOME="C:\path\to\jdk-17"
.\gradlew.bat :app:assembleDebug     # fast dev build
.\gradlew.bat :app:assembleRelease   # unsigned; sign with zipalign + apksigner
```

Release signing (dev convenience key):
```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\zipalign.exe" -f 4 app-release-unsigned.apk aligned.apk
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" sign --ks ~/.android/debug.keystore `
  --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
  --out EchoBooks-v1.0.0-signed.apk aligned.apk
```

---

## 11. Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | OpenRouter API, voice-model downloads |
| `RECORD_AUDIO` | idea dictation (`SpeechToText`) |
| `POST_NOTIFICATIONS` | media session notification buttons |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | background playback |
| `WAKE_LOCK` | keep decoding while screen off |

---

## 12. Known limitations & extension points

- **MOBI**: only PalmDOC (compression 1/2). Kindle files with Huffdic
  (compression 17480) or fixed-width encodings are rejected with a hint to
  convert to EPUB/TXT.
- **Word-count enforcement** depends on the LLM; even with two expansion
  passes some models can return short chapters (audible effect ≈ chapters
  shorter than the requested 10 minutes). Mitigation: prefer large-context
  models; the safeguard loop lives in `ChapterGenerator.generateChapter`.
- **Offline TTS is single-shot per segment**; very long chapters are broken at
  sentence boundaries to avoid sherpa-onnx sentence-length limits.
- **Speech engine is a bundled JNI lib** — `com/k2fsa/sherpa/onnx` is vendored
  MIT code; upgrade by replacing that package + native lib under
  `jniLibs` (if not already present, add the arch `.so` files).
- To add a **new voice**: append a `TtsVoice` entry in `TtsVoice.kt`
  (id/label/detail/tarballName/modelFile/downloadBytes/speakerId).
- To tune **chapter length**: adjust `targetWords`, `passes` ceiling, and
  `maxTokens` in `ChapterGenerator.generateChapter`; the chapter **pause**
  can be tuned via the `gapMs` default in `TtsEngine.synthesizeToFiles`.