package com.bgaming.aztecclusters.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StorageData {
    // [["wild_drops",[1,4],1,0] // destroyer_drops  (drop categray ,[y,x], multi , 掉落场景0开始),["multiplier_drops",[2,1],10,0],["multiplier_drops",[5,7],10,0]]
    private List<List<Object>> features = new ArrayList<>();
    // first data equals outer rotary,  next is drop scene
    private List<List<List<String>>> saved_screens = new ArrayList<>();
    private List<int[][]> multipliers_board = new ArrayList<>();
}
