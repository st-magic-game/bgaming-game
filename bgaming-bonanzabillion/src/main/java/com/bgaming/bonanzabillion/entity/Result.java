package com.bgaming.bonanzabillion.entity;

import lombok.Data;

import java.util.List;

/**
 * 返回给前端的数据
 */
@Data
public class Result {

    /** 所有场景 */
    private List<Scene> scenes;

    /** 总金币 */
    private double gold;

    public Result(List<Scene> scenes, double gold) {
        this.scenes = scenes;
        this.gold = gold;
    }
}
