package com.bgaming.chickenrush.entity.client;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data@NoArgsConstructor@AllArgsConstructor@Accessors(chain = true)
public class Storage implements Serializable {


    private List<int[]> wild_multipliers;

    private List<Integer> accumulated_bonus_multipliers;

    private List<int[]> bonus_multipliers;

    private List<int[]> final_bonus_multipliers;

    private List<int[]> previous_sticky_symbols;

    private List<int[]> sticky_symbols;


}
