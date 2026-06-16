package com.bgaming.candymonsta.entity;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bgaming.candymonsta.config.LotteryConfig.COLUMNS;
import static com.bgaming.candymonsta.config.LotteryConfig.ROWS;

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

    private int[] collectMul = new int[ROWS * COLUMNS];

    private int openFreeNum;

    private int prizeType;

    private boolean drop;

    private double prizePerRound;

    private List<Integer> holdWildIndexes = new ArrayList<>();

    private int isSend;

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

    private int historyMulti;
    private boolean newCollect;

    /**
     * 倍数
     */
    private int doubleMul;

    /**
     * 当前场景类型（0。正常；1。奖金重转）
     */
    private int type;

    /**
     * 中奖倍数 (未加上scatter特殊倍数, 当前展示的是betType对应的基础投注赢分)
     */
    private int mul;

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

    /**
     * 中奖的详细信息
     */
    private List<PrizeIcon> prizeDetail = new ArrayList<>();

}
