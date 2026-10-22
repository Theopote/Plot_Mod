package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;

/** 编辑会话/working copy 中标记纵断面曲线变更。 */
public final class ProfileSourceEdits {

    private ProfileSourceEdits() {
    }

    /** 工作副本中编辑 PVI 后调用；确认时再写入几何签名。 */
    public static void markWorkingCopyEdited(Road road) {
        if (road == null) {
            return;
        }
        if (road.getVerticalMode() != RoadVerticalMode.FLAT) {
            road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        }
        road.setProfileSource(ProfileSource.EDITED);
    }
}
