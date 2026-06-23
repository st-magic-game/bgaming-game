package com.bgaming.bonanzabillion.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 玩家附加信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PlayerAdditionalInformation {

    /** 玩家id */
    private int userId;

    /** 玩家最后一幅场景 */
    private String lastUi;

    private String scenes;

    /** 玩家投注金额 */
    private double betScore;

    /** 上一次更新时间 */
    private long updateTime;
}
