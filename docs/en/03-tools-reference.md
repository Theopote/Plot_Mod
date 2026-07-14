# Tools Reference

All tools and their modes. Options appear in the **Tool Properties** section when a tool is active.

---

## Basic Tools

### Select
| Mode | Description |
|------|-------------|
| Normal | Box-select; **Ctrl+click/box** to add or remove from selection |
| Lasso | Freehand selection outline |

**Box direction:**

| Drag | Rule |
|------|------|
| Left->right | Window select: only fully contained shapes |
| Right->left | Cross select: shapes that intersect the box |

- Plain click on empty area clears selection
- Plain click on a shape selects only that shape
- Single shape selected: **control point editing** available
- Select tool does **not** move shapes — use Move tool

**Shortcut:** `Space`

### Eraser
| Parameter | Range |
|-----------|-------|
| Radius | 1–20 |

**Shortcut:** `D`

---

## Drawing Tools

### Line
| Mode | Description |
|------|-------------|
| Single | Two clicks: start and end |
| Multi | Parallel lines at once |

Multi mode: count (2–20), spacing (5–100). **Shortcut:** `L`

### Free Draw
Smoothness 0.0–1.0. Hold left mouse and drag. **Shortcut:** `P`

### Circle
| Mode | Description |
|------|-------------|
| Center-radius | Center, then radius |
| Two-point | Diameter endpoints |
| Three-point | Three points on circumference |

**Shortcut:** `C`

### Rectangle
| Mode | Description |
|------|-------------|
| Two-point | Diagonal corners |
| Three-point | One edge + width direction |
| Center-point | Expand from center |
| Rounded | Rectangle with rounded corners |

**Shortcut:** `R`

### Ellipse
Three-point axis / Three-point center / Two-point. **Shortcut:** `E`

### Semicircle
Two-point / Three-point. **Shortcut:** `S`

### Arc
Start-end-direction / Through-point / Center-start-end. **Shortcut:** `A`

### Polyline
| Mode | Description |
|------|-------------|
| Polyline | Click segments; double-click or Enter to finish |
| Pen | Anchors + curve handles (Bezier) |
| Edit | Adjust nodes and control points on existing lines |

### Polygon
Center-vertex / Center-radius. Sides adjustable via slider.

### Spline
Through-points (tension 0–1) / Control-point. Press **C** to close.

### Catenary
Standard (symmetric) / Spline interpolation (asymmetric).

### Star
Vertices 3–20; inner/outer twist angles.

### Spiral
Types: Linear, Logarithmic, Semicircle, Fermat, Fibonacci, Polygon. Options: sharp edges, sides, clockwise.

### Sine Wave
Phase 0–360° (preset buttons); amplitude/wavelength set by mouse.

---

## Edit Tools

### Move
Click base -> destination, or drag directly. Hold **Shift** for orthogonal constraint.

### Rotate
Angle step 1–90°; angle snap toggle; preset buttons. Ctrl for temporary copy.

### Scale
Center chosen during interaction.

### Mirror
Axis symmetry (2-point axis) / Central symmetry (180° about a point).

### Align
Point-to-point; optional allow-scaling checkbox.

### Array
| Mode | Parameters |
|------|------------|
| Rectangular | Rows, columns, spacing |
| Circular | Count, radius, angle step |
| Path | Along a path |

While active: **R** rectangular / **C** circular / **P** path; arrow keys adjust values.

### Offset
Optional multiple offset for parallel copies.

### Fillet (Round Corner)
Radius slider. Creates rounded corners at line intersections.

### Chamfer
Chamfer distance slider.

### Extend
Auto-extends to nearest boundary.

### Trim
| Mode | Description |
|------|-------------|
| Boundary | Trim against existing shapes |
| Fence | Custom fence area |

Fence types: polyline, rectangle, circle, ellipse, regular polygon (3–24 sides). Tolerance 1–20. Shift for continuous trim. **C** click-trim / **F** fence-trim.

### Break
Single-point / Two-point break.

### Transform
Optional corner rotation at polyline vertices.

---

## Annotation Tools

### Text
Font size, bold/italic, alignment, line height, dialog vs inline input, convert to shapes.

### Annotation
Modes: Distance, Angle, Radius, Area.

---

## General Tips

1. **Shift** — orthogonal constraints in most drawing tools
2. **Snap** — combine with endpoint/midpoint/intersection snap for precision
3. **Undo** — all operations support `Ctrl+Z`
