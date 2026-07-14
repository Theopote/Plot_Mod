# Extension Plugins

Plot includes several built-in plugins accessible from the **Extension** tab in the right sidebar.

## Opening the Extension Panel

1. Switch to the **Extension** tab on the right
2. Click a plugin icon in the top bar
3. Check **Enable plugin** to show that plugin's settings and controls

> Plugins may be disabled by default — enable them before use.

## Installed Plugins

| Plugin | Description |
|--------|-------------|
| **Earthwork Balance** | Cut/fill planning and grading optimization |
| **Road System** | Plan and generate roads along paths |
| **Building Generator** | Generate buildings from polygon or rectangle footprints |

---

## Road System

### Typical Workflow

1. Draw road centerlines with polyline, spline, or similar tools
2. Open **Extension** -> **Road System** -> enable the plugin
3. Adopt paths and set width, materials, etc.
4. Generate ghost block preview, then project to the world

### Features

- Presets (urban main/secondary roads, rural roads, highways)
- Width, materials, slope and bridge/tunnel thresholds
- Sidewalks, shoulders, drainage
- Cut/fill estimates and preview building

Config: `.minecraft/config/plugins/road_system.json`

---

## Earthwork Balance

### Typical Workflow

1. Draw or select a closed region for grading
2. Open **Extension** -> **Earthwork Balance** -> enable
3. Use the **Adopt** tab to pick/claim regions
4. Use **Edit** to set grading mode (flat, fixed slope, three-point, fit slope, etc.)
5. Use **Generate** to preview cut/fill and build

### Features

- Multiple grading surface modes
- Cut/fill materials and factors
- Region list with undo/redo
- Ghost preview (cut/fill color-coded)

---

## Building Generator

### Typical Workflow

1. Draw a building footprint (rectangle or polygon)
2. Open **Extension** -> **Building Generator** -> enable
3. Pick/adopt the footprint
4. Configure floors, height, etc.
5. Generate preview and project

### Features

- 2D footprint -> 3D building volume
- Multi-building project management
- Ghost preview and block placement

---

## Plugin Data

Each plugin saves project data under `.minecraft/plot/plugins/` (JSON). Plugin settings are in `config/plugins/`.

> This is separate from the main canvas project file. There is currently no unified save/load UI for canvas layers, but plugin data persists per session.

## FAQ

### Extension panel empty or unresponsive

Ensure **Enable plugin** is checked next to the plugin name.

### Region pick mode not responding

Pick mode temporarily captures canvas clicks. Press **Esc** to cancel.

### Conflicts with drawing tools

Like gallery placement, pick modes take priority over normal tool input. Watch the status bar.
