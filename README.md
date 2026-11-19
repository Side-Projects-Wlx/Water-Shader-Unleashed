# Water Shader Unleashed

A native Babric / StationAPI (`b1.7.3`) port and modernization of **Water Shader v4c**, built for **Beta Unleashed 2.0**.

## Features (`1.0.0`)
- **Multi-Pass Planar Water Pipeline**: Supersampled main scene capture (`colorMap`), normal-encoded water stencil pass (`stencilMap`), and exact-height oblique-clipped planar reflection pass (`reflectedColorMap`).
- **Classic Surface Aesthetics & Smooth Flow Transitions**: Preserves Water Shader v4c's animated two-layer sine-wave ripples, clear shallow seabed visibility, and crisp horizon reflections while smoothly blending horizontal and vertical flowing-to-static boundaries with watertight fluid edges.
- **Isolated `1×1` Reflection Suppression & View-Weighted Pond Detection**: Suppresses planar reflections on isolated single-block puddles (requiring at least 2 connected open-still water blocks) and smoothly transitions active planar reflection height toward ponds in view.
- **Modern Cave & Enclosed Water**: Replaces blocky vanilla water textures in caves and enclosed rooms with a smooth, pixel-free aqua-teal surface, animated multi-octave liquid ripples, and viewing-angle Fresnel depth glazing—strictly lit by torches/lava with zero artificial cave glow and matching cave flowing water.
- **Rain-Aware Reflections, Weather & Particles**: Isolates modern rain streaks and water-harmonized splash/bubble particles from surface refraction, adds organic raindrop ripples and rain-softened reflections outdoors, and preserves un-distorted held items.
- **Underwater Tint & Wobble**: Smooth submersion transition with distance-aware underwater fog, gentle refraction wobble, and first-person hand protection.
- **Performance & Compatibility**: Skips stencil/reflection passes on dry land (and reflection passes in ceilinged dimensions), forces smooth water interpolation even in Fast Graphics / Smooth Lighting OFF, and captures Layer 1 geometry alongside Amphetamine and StationAPI.

## Configuration (`config/watershader.txt`)
Generated automatically on first launch and reloaded on restart:

| Option | Default | Description |
|---|---:|---|
| `water_mode` | `2` | `0` = off, `1` = refraction only, `2` = reflection and refraction |
| `anti_aliasing` | `2` | Internal supersampling multiplier (`>= 1`) |
| `reflection_resolution` | `1` | Reflection FBO divisor (`1` = Full 1/1, `2` = Half 1/2, `4` = Quarter 1/4; also toggleable live in ModMenu) |
| `reflective_items` | `1` | Reflect entities/items/mobs (`1` / `0`) |
| `reflective_clouds` | `0` | Reflect clouds (`1` / `0`) |
| `reflect_player` | `1` | Reflect local player (`1` / `0`) |
| `render_v3` | `1` | Enable v3 open-sky brightness lift (`1` / `0`) |
| `water_surface_transparency` | `0.1` | Still-water transparency (`0.0..1.0`) |
| `waterfall_transparency` | `0.35` | Waterfall/flowing slope transparency (`0.0..1.0`) |
| `water_color` | `0.0/0.5/0.35` | Deep water / palette harmonization RGB tint |
| `waterfall_color` | `1.0/1.0/1.0` | Multiplicative waterfall RGB tint |
| `waterfall_fog` | `1` | Enable waterfall & cave-water fog handling (`1` / `0`) |
| `exclude_list` | *(empty)* | Comma- or space-separated block IDs or translation keys to exclude |
| `fresnel_strength` | `1.0` | Reflection & Fresnel curve multiplier (`>= 0.0`) |
| `ripple_strength` | `1.0` | Surface wave distortion multiplier (`>= 0.0`) |
| `underwater_effect` | `1` | Enable shader underwater tint and wobble (`1` / `0`) |

## ModMenu & Keybinds
- **ModMenu Config Screen**: Click **Configure** on **Water Shader Unleashed** in ModMenu to toggle **Reflection Resolution** (`Full (1/1)`, `Half (1/2)`, `Quarter (1/4)`) live—changes apply immediately and save to `config/watershader.txt`.
- **`F9` (`Toggle Water Shader`)**: Toggles Water Shader rendering ON/OFF live in-game (when enabled in `config/watershader.txt`).
- **`Water Stencil Debug`** *(unbound by default — assignable in Controls)*: Toggles the raw `stencilMap` visualization overlay in-game.

## Installation
- **Requirements**: Minecraft Beta `1.7.3`, Babric / Fabric Loader `>= 0.15.0`, StationAPI `2.0.0-alpha.6.4` (optional: ModMenu Babric `1.1.1+` for the in-game Reflection Resolution screen).
- Drop `watershader-unleashed-1.0.0.jar` into your instance's `mods/` folder.
- *(Beta Unleashed 2.0)*: Disable any conflicting shader pipeline mod (e.g., `Macula`) if present.

## Building from Source
Requires JDK 17+ (produces Java 17 bytecode):
```bash
./gradlew clean check build
```
The remapped release jar is generated at `build/libs/watershader-unleashed-1.0.0.jar`.

## License & Credits
- Licensed under the [MIT License](LICENSE).
- Ported and modernized by **Wlx**; inspired by **Necro's** original Minecraft Beta 1.7.3 **Water Shader v4c**.
