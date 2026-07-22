package com.bgaming.luckyandmagic.entity;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 5行5列的图标场景
 */
@Data
@ToString
public class Scene {
    /** 中奖的总金额 betScore / 100 * mul * (1 + freeMul) */
    private double gold;
    private BonusData bonusData;
    /** 中奖坐标*/
    private Set<Integer> prizeIndex = new HashSet<>();
    private BigDecimal bonusWin;
    private double betScore;
    private double betScoreServer;
    /** 图标分布 */
    private int[][] rotary;

    /** 免费类型*/
    private int freeType;

    /** 倍数 */
    private int doubleMul;

    /** 当前场景类型（0。正常；1。奖金重转） */
    private int type;

    /** 订单号*/
    private String order;

    private String pOrder;

    private double afterScore;

    private int number;

    private long time;

    /** 中奖的详细信息 */
    private List<PrizeIcon> prizeDetail = new ArrayList<>();

}
