package com.bgaming.aztecclusters.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Set;

@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PrizeIcon {
    private int icon;
    private int line;
    /** 总中奖金额 */
    private BigDecimal totalGold;
    private BigDecimal gold;
    private int mul;
    private int extMul = 1;
    private Set<Integer> prizeIndex;

}
