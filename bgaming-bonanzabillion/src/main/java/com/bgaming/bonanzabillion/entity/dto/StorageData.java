package com.bgaming.bonanzabillion.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StorageData {
    // [["cascade_x",10,[4,4]],[]] // multi_icon_name x是第个画面出现的 , mul, index[x,y]
    private List<List<Object>> bombs = new ArrayList<>();
    // first data equals outer rotary,  next is drop scene
    private List<List<List<String>>> saved_screens = new ArrayList<>();
    private boolean super_spin;
}
