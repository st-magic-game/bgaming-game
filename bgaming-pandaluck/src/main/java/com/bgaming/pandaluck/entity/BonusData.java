package com.bgaming.pandaluck.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class BonusData implements Serializable {
    private int multiplier;
    private List<List<Object>> new_values = new ArrayList<>();// [y,x]
    private int[][] coins_screen; // [[1列][2列][3列]]
    private int respins_issued = 3;
    private int respins_left;
    private List<List<Object>> wins = new ArrayList<>();
}
