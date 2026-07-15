# ReShade (.fx) drop-in support — PR 1 (effect selection)

Adds per-game / per-container **ReShade effect selection**, applied to Vulkan-backed (DXVK/VKD3D)
games via the **vkBasalt layer this project already bundles** (`assets/graphics_driver/extra_libs.tzst`
→ `usr/lib/libvkbasalt.so` + `usr/share/vulkan/implicit_layer.d/vkBasalt.json`). No native/binary
change — it wires up a layer that already ships.

Continuation of **DadSchoorse/vkBasalt** (zlib, original engine) → bundled into the Winlator/Cmod
lineage by **Pipetto-crypto**. The reflection/config format is kept forward-compatible with a
live-reload patched `libvkbasalt.so` (PR 2) so no rework is needed later.

> **This is PR 1 of 2.** It is intentionally **app-code only** and rides the stock vkBasalt you
> already ship (effect applies at its `.fx` default look). A follow-up **PR 2** brings our
> **live-reload patched `libvkbasalt.so`** (+ its build workflow) for the in-game on/off toggle and
> live parameter sliders, landing the in-game UI as its own `XServerDrawerReshadePane.kt`. Splitting
> them keeps this PR free of any binary change so the app-side design can be reviewed on its own; the
> binary swap into `extra_libs.tzst` is PR 2, to be coordinated with you (in-tree vs. release asset
> vs. LFS). See "Not in this PR" below.

## What a user gets
- A **ReShade** section in the per-game and per-container settings (its own sidebar entry, not the
  packed FX/graphics tab).
- Drop effects into `Android/data/<package>/files/ReShade/`, **one self-contained folder per effect**
  (the `.fx` plus any `.fxh` includes and textures). The picker lists every folder that contains a
  `.fx`; choose one (or *None*).
- At launch, the effect compiles to SPIR-V on-device (vkBasalt's embedded `reshadefx`) and applies to
  the game's Vulkan swapchain — color/tone/sharpen/grain/CRT-style effects.

## Honest scope of PR 1 (stock layer)
The **currently bundled** `libvkbasalt.so` reads its config once at swapchain create, has no path to
override a `.fx` uniform from config, and its HOME toggle is a no-op. So PR 1 delivers **effect
selection applied at the effect's default look** — no in-game toggle, no live parameter sliders.
Depth-based effects (SSAO/DOF) are out (no depth capture) — a later step.

The conf writer still emits per-uniform lines in the patched-layer key scheme (`<effectKey>_<uniform>`)
so they light up unchanged once PR 2 lands.

## Files
**New** (`com.winlator.cmod.runtime.reshade`):
- `runtime/reshade/ReshadeManager.java` — drop-in folder scan + `.fx` `ui_*` param reflection.
- `runtime/reshade/ReshadeConfigWriter.java` — stages effect files into
  `<home>/.config/vkBasalt/`, writes `vkBasalt.conf`, sets `ENABLE_VKBASALT=1` +
  `VKBASALT_CONFIG_FILE`. Host-absolute paths, matching how `HOME`/`WINEPREFIX` are already set.

**Wired (minimal):**
- `runtime/display/XServerDisplayActivity.java` — one import + one `applyReshadeEnv(envVars)` call in
  the launch env block + one private helper. Gated on a Vulkan DX wrapper; fully swallowed (never
  breaks a launch). *Kept deliberately tiny because this file is being refactored upstream.*
- `feature/library/GameSettings.kt` — new `SEC_RESHADE` sidebar section + `ReshadeSection` composable
  + two state fields. Uses the existing `SettingGroup`/`SettingDropdown` widgets.
- `feature/shortcuts/ShortcutSettingsComposeDialog.kt` — load + save of the `reshadeEffect` extra.
- `feature/settings/containers/ContainerSettingsComposeDialog.kt` — same, container default.
- `res/values/strings.xml` — 5 strings.

Persistence uses the existing `reshadeEffect` string extra on Container / Shortcut (`None` clears it).

## Not in this PR (follow-ups)
- **PR 2 — live control:** swap in the live-reload patched `libvkbasalt.so` (+ its build workflow) for
  in-game on/off and live parameter sliders. Per maintainer guidance, the in-game UI lands as its own
  **`XServerDrawerReshadePane.kt`** (own drawer file, separate "ReShade" section) on top of the drawer
  refactor — not stuffed into the FX pane.
- **PR 3 — download catalog + multi-effect loadouts.** Catalog host must be WinNative's, not baked in.
- **Create-time** container default: `reshadeEffect` persists via the container *edit* path, but not
  the new-container-creation path. This is **parity with `swapRB`** — `Container.loadData()` has no
  `default` case and ignores unknown `data.put(...)` keys, so surface-effect has the same limitation
  today. Fixing it properly means a generic extras passthrough in `Container.loadData()` (which would
  also fix `swapRB`) — out of scope here; worth raising separately.

## Notes for review
- Base on the post-refactor `main` (the `XServerDrawerMenu.kt` / `XServerDisplayActivity.java` split)
  to avoid conflicts.
- **Include contract (the key on-device gate):** most real `.fx` do `#include "ReShade.fxh"` /
  `"ReShadeUI.fxh"`. vkBasalt predefines the macros (`BUFFER_WIDTH`, …) but does **not** ship those
  headers, so **each drop-in effect folder must be self-contained** — the `.fx` plus its `.fxh`
  includes and textures co-located. `reshadefx` resolves includes relative to the `.fx`, so
  `reshadeIncludePath` = the effect folder is correct. This is the one thing to prove with a real `.fx`
  on-device (a green CI won't); the same pattern is device-proven on the same-lineage stock `libvkbasalt.so`.
- Compile-plausible, written against the current APIs; **not yet CI-built or device-tested** here.
- User-override safety: if `ENABLE_VKBASALT` is already set via custom env / launch options, the writer
  backs off and leaves ReShade untouched.
