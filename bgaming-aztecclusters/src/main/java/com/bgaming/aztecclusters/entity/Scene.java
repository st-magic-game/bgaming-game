package com.bgaming.aztecclusters.entity;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.aztecclusters.entity.dto.StorageData;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class Scene {
    private double gold;
    private int openFreeNum;
    private double betScore;
    private double betScoreServer;
    private int[][] rotary;
    private int[][] finalRotary;
    private int freeNum;
    private int freeType;
    private int totalFreeNum;
    private int doubleMul = 1;
    private int type;
    private String order;
    private String pOrder;
    private double beforeScore;
    private double afterScore;
    private int number;
    private int betType;
    private long time;
    private double historyWinScore;
    private double freeTotalScore;
    private boolean multiBooster;
    private boolean destroy;
    private List<Integer> holdWildIndexes = new ArrayList<>();
    private List<Integer> newWildIndexes = new ArrayList<>();
    private List<Integer> scatterIndexes = new ArrayList<>();
    // 展示最后一幅图的wild和scatter位置  [x,y]
    private JSONObject special_symbols = new JSONObject();
    private List<List<PrizeIcon>> prizeDetailList = new ArrayList<>();
    private StorageData storage = new StorageData();
}
