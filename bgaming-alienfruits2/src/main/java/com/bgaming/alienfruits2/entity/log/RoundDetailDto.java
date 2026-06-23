package com.bgaming.alienfruits2.entity.log;

import com.bgaming.alienfruits2.entity.client.ApiClientResult;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    private String betTextBuy;

    private String totalWinText;

    private String profitText;

    private String balanceBeforeText;

    private String balanceAfterText;

    private String currency;

    private String usedFeature;

    private List<String> symbols;
    // [[3x,l2,Line 10 - 4],[5x,l3,Line 8 - 5]]
    private List<String> winLines;

    private String scatterWin;

    private BigDecimal bonusPayout;

    private int bonusMultiplier;

    private String stake;

    private List<ApiClientResult> clientResults  = new ArrayList<>();
}