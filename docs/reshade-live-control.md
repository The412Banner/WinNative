# ReShade live control — patched vkBasalt layer + pre-launch tuning

Follow-up to the app-side ReShade PR. Swaps the **stock** `libvkbasalt.so` for our
**live-reload patched** build so a `.fx` effect's parameters actually apply, and adds
**pre-launch typed controls** (sliders / toggles / dropdowns / color) to the ReShade settings
section. Pure Vulkan-layer + app-settings work — the in-game drawer live toggle/sliders land in a
later follow-up (see "Not in this PR").

## ⚠️ Update / migration behavior — **no imagefs reinstall, updates cleanly**

This PR ships a new binary, so the important question is what existing users have to do. Answer:
**nothing — the app updates in place and containers self-heal.**

- The patched `.so` lives in **`assets/graphics_driver/extra_libs.tzst`** — an **app asset**, not
  `imagefs.tzst`. A normal signed app update delivers it. The big imagefs blob is **not touched**, so
  there is **no container/imagefs reinstall prompt**.
- **Version-aware extraction** (this PR): `EXTRA_LIBS_VERSION` + a `usr/lib/.extra_libs_version`
  marker. On the next launch after updating, an existing container whose marker doesn't match
  re-extracts **only `extra_libs.tzst`** (usr/lib `.so`s + vulkan layer manifests), swapping stock →
  patched. One-time, ~18 MB, automatic, logged. Extraction was moved out of the `firstTimeBoot`-only
  gate, so **fresh containers are covered too**.
- `extra_libs.tzst` contains **no `home/` or `drive_c/`** → game saves, installed games, and the wine
  prefix are **untouched**.
- Bump `EXTRA_LIBS_VERSION` on any future repack of `extra_libs.tzst` and existing installs re-heal
  the same way.

## What it changes

- **`assets/graphics_driver/extra_libs.tzst`**: stock `libvkbasalt.so` → patched (arm64, stripped,
  X11-free). Only the `.so` is swapped; the `vkBasalt.json` manifest and every other lib are
  byte-identical. tzst 21 MB → 18.8 MB.
- **`.github/workflows/build-vkbasalt.yml`** + `patches/`: reproducible build of the patched `.so`
  from upstream **DadSchoorse/vkBasalt** @`4f97f09` + `vkbasalt-reshade-livereload.patch`
  (NDK r27c / meson cross-build; standalone clone, no submodule). The patch = Part A config
  mtime-watch → live on/off, Part B UBO uniforms → live sliders, plus the Android X11-free switch
  (and a dormant per-effect enable-gate).
- **`XServerDisplayActivity.java`**: `EXTRA_LIBS_VERSION` + the version-aware extraction block.
- **`GameSettings.kt`** (+ both settings dialogs): pre-launch typed controls for the selected
  effect's `ui_*` params, persisted to the `reshadeParams` JSON extra (the launch conf writer already
  consumes it — no writer change).

## Binary delivery — maintainer call

The patched `.so` is ~1.85 MB inside the 18.8 MB `extra_libs.tzst`, committed in-tree today. Happy to
move it to a **release asset** or **Git LFS** instead — your preference.

## Not in this PR (next follow-up)

The **in-game drawer** live toggle/sliders + the pause/pulse preview UX. That's the part that touches
the drawer being refactored upstream, so it lands afterward as its own `XServerDrawerReshadePane.kt`
("own section, before Display", per review). The patched `.so` here already supports live reload; this
PR just exposes it pre-launch.

## Credits / license

vkBasalt = **DadSchoorse/vkBasalt** (zlib), the original engine; bundled into the Winlator/Cmod
lineage by **Pipetto-crypto**; **StevenMXZ** (Ludashi). Our live-reload patch sits on top of
DadSchoorse's source.

## Verification

- Patched `.so` CI-built (fork run — meson cross-build, all symbols present).
- Full-branch compile + on-device pre-launch tuning: pending.
