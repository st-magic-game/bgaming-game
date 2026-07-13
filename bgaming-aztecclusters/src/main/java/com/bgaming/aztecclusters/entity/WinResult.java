package com.bgaming.aztecclusters.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WinResult {
    private List<PrizeIcon> prizeIcons = new ArrayList<>();
    private Set<Integer> prizeIndex = new HashSet<>();
    private double winAmount;
}