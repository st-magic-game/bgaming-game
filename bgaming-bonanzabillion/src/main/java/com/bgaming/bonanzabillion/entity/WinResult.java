package com.bgaming.bonanzabillion.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WinResult {
    private List<PrizeIcon> prizeIcons;
    private Set<Integer> prizeIndex;
    private double winAmount;
}