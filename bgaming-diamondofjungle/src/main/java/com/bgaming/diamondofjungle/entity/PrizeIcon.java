package com.bgaming.diamondofjungle.entity;

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
    private int num;
    private int line;

    /** 总中奖金额 */
    private BigDecimal gold;

    private int mul;

    private Set<Integer> prizeIndex;


    public PrizeIcon(int icon, int line,int num, Set<Integer> prizeIndex) {
        this.icon = icon;
        this.num = num;
        this.line = line;
        this.prizeIndex = prizeIndex;
    }
}
