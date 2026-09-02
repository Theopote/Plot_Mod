from pathlib import Path

path = Path('src/main/java/com/plot/plugin/earthwork/manager/EarthworkUIManager.java')
text = path.read_text(encoding='utf-8')
text = text.replace('ctx.setPendingDeleteRegionId("";', 'ctx.setPendingDeleteRegionId("");')
text = text.replace(
    'ctx.setSelectedRegionId(ids[regionIndex.get()];',
    'ctx.setSelectedRegionId(ids[regionIndex.get()]);',
)
path.write_text(text, encoding='utf-8')
print('fixed')
