package com.bgaming.bonanzabillion.entity;

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
    private int hitLine;
    private int line;

    /** 总中奖金额 */
    private BigDecimal gold;

    private int mul;

    private Set<Integer> prizeIndex;


    public PrizeIcon(int icon, int line,int num, Set<Integer> prizeIndex) {
        this.icon = icon;
        this.hitLine = line;
        this.line = num;
        this.prizeIndex = prizeIndex;
    }
}
