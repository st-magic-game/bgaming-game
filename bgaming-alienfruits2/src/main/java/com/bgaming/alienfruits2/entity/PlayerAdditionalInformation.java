package com.bgaming.alienfruits2.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 玩家附加信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Accessors(chain = true)
public class PlayerAdditionalInformation {

    /** 玩家id */
    private int userId;

    /** 玩家最后一幅场景 */
    private String lastUi;

    /** 玩家投注金额 */
    private double betScore;

    private int freeNum;

    private int totalFreeNum;

    private String usedFeature;

    /** 上一次更新时间 */
    private long updateTime;
}
