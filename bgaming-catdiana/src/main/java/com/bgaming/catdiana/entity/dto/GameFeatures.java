package com.bgaming.catdiana.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GameFeatures {
    private Integer freespins_issued;
    private Integer freespins_left;
    private List<List<Integer>> sticky_wilds = new ArrayList<>();
}
