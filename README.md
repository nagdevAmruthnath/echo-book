# echo-book

## Audiobook App - Stuck Generation Fix

This project fixes the issue where users cannot cancel, stop, or delete stuck/in-progress audiobook generation.

### Changes Made

- **OpenRouterClient.kt**: Fixed readTimeout from 0→60s, added callTimeout 20min, rewritten `streamChat`/`completeChat` with `suspendCancellableCoroutine` + `call.enqueue` + `invokeOnCancellation { call.cancel() }` so coroutine cancel now aborts the HTTP call instantly.

- **GenerationService.kt**: Separate `cleanupScope` (SupervisorJob + Dispatchers.IO) so cleanup survives service teardown; `fail()` now calls `cleanup()`; notification now has "Cancel & delete progress" action via PendingIntent; `cleanup()` sets `bookId=0L` immediately and deletes chapters/bookmarks/book-row/`app_books/<id>` dir.

- **LibraryScreen.kt**: Added `onDelete` parameter to `BookCard` with red Delete `GlassIconButton` top-left (`contentDescription = "Delete <title>"`); wired confirmation dialog via `LibraryViewModel.delete(book)`; verified stale Book 9 ("The Mysterious Library") deletion removes DB row + audio dir.

- **HomeScreen.kt / LibraryScreen.kt `GenerationBanner`**: Added Close `GlassIconButton` (red "Discard failed generation" in Error phase, neutral "Cancel generation" otherwise); both screens call `GenerationService.cancel(context)`.

- **Build**: `assembleDebug` green (EXIT=0) after all fixes; installed on emulator version 1.2.0; 4 bottom tabs, share sheet, ZIP export verified.

- **Delete verification**: Book 9 "The Mysterious Library" stale entry (completed=1, chapterCount=3 but 0 chapters) successfully deleted via new UI: removes DB row, chapters, and `app_books/9` directory.

- **Error handling**: 429 rate-limit error card correctly displays provider rate-limit JSON; Back button at (540.5, 1970.5) navigates home; no orphaned rows when `fail()` fires mid-run (bookId=0→cleanup no-op).

### How to Use

- Long-press any book card in Library → red Delete button appears top-left → confirm dialog → book and all associated files permanently removed.
- During generation, tap the Cancel button in GeneratingScreen, or use the "Cancel & delete progress" notification action, or tap the banner Close button to abort and clean up.

### Screenshots (from emulator)

1. **Library screen with delete buttons**: Each book card shows a red Delete `GlassIconButton` in the top-left corner with content description "Delete <title>". Book 9 "The Mysterious Library" appears with 3 chapters but 0 audio - this is the stale entry that can now be deleted.

2. **Delete confirmation dialog**: Tapping the Delete button shows an AlertDialog with title "Delete this book?" and text '"The Mysterious Library" and all its chapters and audio will be permanently removed.' Buttons: Delete (red) and Cancel.

3. **GeneratingScreen - Cancel button**: During active generation, a Cancel button appears in the error/action bar. Tapping it calls `generationVm.cancel()` which triggers `GenerationService.cancel()` → `job.cancel()` + cleanup.

4. **Error state banner**: Home/Library screen shows GenerationBanner with Close button. In Error phase (red), reads "Generation failed — tap to discard the partial book." In active phase (neutral), reads "Cancel generation."

5. **429 rate-limit error card**: GeneratingScreen error card displays the provider rate-limit JSON: `OpenRouter returned 429: {"error":{"message":"Provider returned error","code":429,"metadata":{"raw":"sao10k/l3.3-euryale-70b is temporarily rate-limited upstream..."}}`