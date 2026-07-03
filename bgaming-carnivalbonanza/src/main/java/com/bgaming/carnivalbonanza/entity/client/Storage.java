package com.bgaming.carnivalbonanza.entity.client;

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

    private List<List<Object>> bombs = new ArrayList<>();

    private int round_multiplier;

    private List<List<List<Integer>>> saved_screens = new ArrayList<>();

    private boolean super_spin;

    private Integer fs_level = null;

    private List<Object[]> new_won_symbols;

}
