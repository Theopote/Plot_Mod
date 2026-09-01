# AUTO_SMOOTH v1 semantics

`RoadVerticalMode.AUTO_SMOOTH` is a persisted compatibility name. In the product UI, v1 is
presented as **Auto Grade / 自动控坡**.

## v1 contract

- Sample terrain and build a balanced guide line.
- Respect shared at-grade junction elevations as endpoint constraints.
- Limit generated target-height changes with the effective road grade limit.
- Produce Minecraft-oriented discrete elevations.
- Do not create or persist automatic PVIs or vertical curves.

The manual profile mode remains the only mode whose final design is a
`RoadVerticalAlignment` containing PVIs and optional vertical curves.

## Deferred work

Automatic PVI extraction, vertical-curve fitting, and a strict minimum automatic grade-run length
are deferred until visual testing shows that the v1 discrete solver produces objectionable grade
breaks. The current minimum grade-run design rule applies to authored manual profiles; it must not
be advertised as a strict AUTO_SMOOTH v1 guarantee.
