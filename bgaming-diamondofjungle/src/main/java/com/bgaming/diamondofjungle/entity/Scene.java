package com.bgaming.diamondofjungle.entity;

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
    /**
     * 中奖的总金额 betScore / 100 * mul * (1 + freeMul)
     */
    private double gold;
    private BigDecimal scatterWin;
    /**
     * 中奖坐标
     */
    private Set<Integer> prizeIndex = new HashSet<>();
    private int openFreeNum;
    private double betScore;
    private double betScoreServer;

    /**
     * 图标分布
     */
    private int[][] rotary;

    /**
     * 免费场次
     */
    private int freeNum;

    /**
     * 免费类型
     */
    private int freeType;

    private int totalFreeNum;

    /**
     * 倍数
     */
    private int doubleMul = 1;

    /**
     * 当前场景类型（0。正常；1。奖金重转）
     */
    private int type;

    /**
     * 订单号
     */
    private String order;

    private String pOrder;
    private double beforeScore;
    private double afterScore;

    private int number;

    private long time;

    /**
     * 历史分数
     */
    private double historyWinScore;

    private double freeTotalScore;

    private String volatility;

    private int scatterSize;

    /**
     * 中奖的详细信息
     */
    private List<PrizeIcon> prizeDetail = new ArrayList<>();

}
