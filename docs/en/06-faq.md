# FAQ

## Operations

### Why doesn't Esc close Plot?

`Esc` priority:
1. Cancel current tool operation
2. Clear shape selection
3. Clear ghost block previews

To exit Plot, click the **close button (✕)** in the top-right corner.

### Clicks on the canvas don't work

Mouse input is captured by panels when hovering over them. Move the cursor to the **central canvas area** between panels.

### Line to Block button is grayed out

You must **select** at least one shape first (`Space` → box-select or click).

### Project Blocks has no effect

You need ghost block previews first:
1. Select shapes
2. Line to Block (preview)
3. Project Blocks (place)

### How to delete shapes?

- Select and press `Delete`
- Use the **Eraser** tool
- All deletions are undoable with `Ctrl+Z`

---

## Shortcuts

### Shortcuts don't work

1. Ensure no text input has focus
2. Check for conflicts with other mods
3. Verify bindings in **Settings & Help → Shortcuts**
4. Try **Reset defaults**

### What does the P key do?

`P` is the in-Plot **Free Draw** shortcut. Opening Plot uses **`0`** by default (Minecraft Controls → Plot).

### Changing the "Open Plot" key

Modify it in Minecraft **Settings → Controls → Plot**, not in Plot's shortcut settings.

---

## View

### Zoom and pan

| Action | Function |
|--------|----------|
| Scroll wheel | Zoom |
| Middle-mouse drag | Pan |
| View range slider | Adjust visible range |

### View is locked

Click the **Lock View** button to unlock.

### Return to normal gameplay camera

Toggle **Camera** to perspective mode, or close Plot (auto-restores).

---

## Block Building

### What are ghost blocks?

Semi-transparent preview blocks — not real, non-solid. Clear with Esc or by closing Plot.

### Can projected blocks be undone?

Yes — `Ctrl+Z` undoes projection.

### Palette limit?

Maximum **14** slots.

### Full vs simplified conversion?

| Mode | Use case |
|------|----------|
| Full | Precise detail |
| Simplified | Large structures; fewer blocks; adjustable ratio |

---

## Limitations

### Can I save projects?

No unified save/load UI yet. Auto-persisted: keybindings, tool configs, plugin configs, plugin project data, and **gallery entries**. Canvas layer content is **not** saved as a project file when Plot closes. Use the [Gallery](07-gallery.md) to save and reuse shapes.

### Where are the extension plugins?

Switch to the **Extension** tab on the right, click a plugin icon, and check **Enable plugin**. See [Extension Plugins](08-plugins.md). Built-in: **Road System**, **Earthwork Balance**, **Building Generator**.

### Tools in lang files but not available?

**Fill** and **Stretch** appear in language files but are not registered in the tool panel yet.

---

## Performance & Compatibility

### Conflicts with other ImGui mods?

Plot uses imgui-java 1.86.12 with context isolation fixes. If issues persist, check for multiple ImGui mods.

### Slow block icons?

Common blocks are preloaded after entering a world. First open of Block Config may be slower.

### Does the game pause?

No. Mobs continue moving. Use Plot in a safe area.

---

## Getting Help

- In-game: **Plot logo → Settings & Help → Help & Tutorials**
- Online: [docs/README.md](../README.md)
- Issues: [GitHub Issues](https://github.com/Theopote/Plot_Mod/issues)

When reporting bugs, include Minecraft/Fabric/Plot versions, reproduction steps, and screenshots or logs.
