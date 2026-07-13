package com.bgaming.aztecclusters.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoundDetailDto {

    private String time;

    private BigDecimal bet;

    private int multiple;

    private boolean spin;

    private int type;

    private int number;

    private int totalFreeNum;

    private int freeNum;

    private int openFreeNum;

    private BigDecimal totalWin;

    private BigDecimal profit;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private BigDecimal scatterWin;

    private String betText;

    private String totalWinText;

    private String profitText;

    private String totalRoundWinText;

    private String scatterWinText;

    private String balanceBeforeText;

    private String balanceAfterText;

    private String currency;

    private boolean usedFeature;

    private String featureName;

    private List<SymbolInfo> symbols;
    // [[3x,l2,Line 10 - 4],[5x,l3,Line 8 - 5]]
    private List<List<String>> winLines;

    private List<String> features;
}