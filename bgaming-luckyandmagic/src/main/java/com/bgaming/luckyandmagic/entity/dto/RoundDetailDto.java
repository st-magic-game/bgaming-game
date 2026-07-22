package com.bgaming.luckyandmagic.entity.dto;

import com.bgaming.luckyandmagic.entity.BonusData;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoundDetailDto {
    private BonusData bonusData;
    private String time;

    private BigDecimal bet;

    private BigDecimal scatterWin;

    private BigDecimal totalWin;

    private BigDecimal profit;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String betText;

    private String totalWinText;

    private String profitText;

    private String scatterWinText;

    private String balanceBeforeText;

    private String balanceAfterText;

    private String baseBetText;

    private String currency;

    private boolean usedFeature;

    private String featureName;

    private List<String> symbols;
    // [[3x,l2,Line 10 - 4],[5x,l3,Line 8 - 5]]
    private List<List<String>> winLines;
}