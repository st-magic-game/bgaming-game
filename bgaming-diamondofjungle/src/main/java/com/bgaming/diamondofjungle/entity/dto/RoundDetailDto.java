package com.bgaming.diamondofjungle.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoundDetailDto {

    private String time;

    private BigDecimal bet;

    private int type;

    private BigDecimal totalWin;

    private BigDecimal profit;

    private int doubleMul;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private BigDecimal scatterWin;

    private String scatterSize;

    private String betText;

    private String baseBetText;

    private Integer openFreeNum;

    private String totalWinText;

    private String profitText;

    private String scatterWinText;

    private String balanceBeforeText;

    private String balanceAfterText;

    private String currency;

    private boolean usedFeature;

    private String featureName;

    private String volatility;

    private List<String> symbols;
    // [[3x,l2,Line 10 - 4],[5x,l3,Line 8 - 5]]
    private List<List<String>> winLines;
}