package com.bgaming.totallyhot.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoundDetailDto {

    private String time;

    private BigDecimal bet;

    private BigDecimal totalWin;

    private BigDecimal profit;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String betText;

    private String totalWinText;

    private String profitText;

    private String balanceBeforeText;

    private String balanceAfterText;

    private String currency;

    private boolean usedFeature;

    private List<String> symbols;
    // [[3x,l2,Line 10 - 4],[5x,l3,Line 8 - 5]]
    private List<List<String>> winLines;
}