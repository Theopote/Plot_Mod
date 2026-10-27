# Road Editor Manual Checklist

Use this list when validating floating editors and profile authority after code changes.

## Resolutions and DPI

- [ ] 1280×720: all six floating windows open without clipping footer buttons
- [ ] 1920×1080: centerline segment list scrolls independently; footer remains visible
- [ ] 150% display scaling: conflict banner and reload confirm text wrap cleanly (EN + ZH)

## Floating editor lifecycle

- [ ] Cross section / variable XS / centerline / station facilities / batch / diagnostics open on selected road
- [ ] Dirty window: scope change shows close confirm; choosing **Continue editing** keeps selection on bound road
- [ ] Version conflict on Apply: banner appears; **Reload from live network** shows second confirm about discarding edits
- [ ] After reload: working copy matches live; new edits can be applied without overwriting unrelated roads

## Profile and build consistency (smoke)

- [ ] MANUAL_PROFILE road: green design line, gray block line, and preview build align within one block
- [ ] AUTO road with dormant stored VA: generation preview does not follow old curve until EDITED is re-confirmed

## Locale

- [ ] English: `reload_from_live_confirm` reads clearly next to reload button
- [ ] Chinese: same flow; no truncated warning on narrow panels
