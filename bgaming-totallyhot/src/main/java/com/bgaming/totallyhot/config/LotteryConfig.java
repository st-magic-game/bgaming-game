package com.bgaming.totallyhot.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String BET_TYPE = "betType";
    /**
     * 行数
     */
    public static final int ROWS = 3;

    /**
     * 列数
     */
    public static final int COLUMNS = 5;

    public static final int SUB_UNITS = 100;
    /**
     * 百搭符号单轴不重复
     */
    public static final int WILD = 0;


    public static final double SMALL_WIN_PRO = 0.12697;
    /**
     * 中长线的概率
     */
    public static final double LONG_LINES_PRO = 0.001;

    /**
     * 金框个数概率 1, 2, 3, 4
     */
    static final double[] WILD_PRO = {0.101, 0.06, 0.015};

    static final double[] ICON_PRO = {0.070062003, 0.069753073, 0.077278421, 0.072926849, 0.069948568, 0.163841644, 0.167787746, 0.153417662, 0.154984035};

    public static final int[] ICONS_WITH_MULTIPLE = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    /**
     * 各图标对应的倍数 {图标索引,3线倍数,4线倍数,5线倍数}
     */
    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    // HOT
                    {1, 3, 10}, {1, 4, 50}, {1, 5, 250},
                    // 7
                    {2, 3, 6}, {2, 4, 20}, {2, 5, 50},
                    // 铃铛
                    {3, 3, 5}, {3, 4, 10}, {3, 5, 40},
                    // 五星
                    {4, 3, 5}, {4, 4, 10}, {4, 5, 40},
                    // 西瓜
                    {5, 3, 2}, {5, 4, 5}, {5, 5, 20},
                    // 葡萄
                    {6, 3, 2}, {6, 4, 5}, {6, 5, 20},
                    // 西梅
                    {7, 3, 1}, {7, 4, 3}, {7, 5, 15},
                    // 橙子
                    {8, 3, 1}, {8, 4, 3}, {8, 5, 15},
                    // 樱桃
                    {9, 3, 1}, {9, 4, 3}, {9, 5, 15},
            };
    public static Map<Integer, List<Integer>> PAYTABLE = new HashMap<>();
    public static Map<Integer, Map<String, List<Integer>>> PAYTABLES = new HashMap<>();
    public static Map<String, List<List<String>>> REELS = new HashMap<>();
    public static List<List<String>> SCREEN = new ArrayList<>();
    public static List<JSONObject> SPECIAL_SYMBOLS = new ArrayList<>();

    static {
        initOptions();
    }

    private static void initOptions() {
        for (int icon : ICONS_WITH_MULTIPLE) {
            List<Integer> mulList = getMulList(icon);
            PAYTABLE.put(icon, mulList);

            Map<String, List<Integer>> defaultMul = new HashMap<>();
            defaultMul.put("default", mulList);
            PAYTABLES.put(icon, defaultMul);
        }

        List<List<String>> reelList = new ArrayList<>();
        reelList.add(Arrays.asList("1", "9", "9", "2", "2", "2", "9", "9", "9", "7", "2", "2", "7", "7", "6", "4", "8", "8", "8", "2", "2", "9", "9", "9", "5", "5", "2", "8", "5", "4", "7", "7", "7", "8", "8", "8", "1", "5", "1", "4", "4", "4", "5", "5", "5", "3", "3", "3", "6", "6", "6", "2", "7", "7"));
        reelList.add(Arrays.asList("1", "8", "1", "7", "7", "4", "9", "9", "9", "7", "4", "4", "6", "6", "2", "6", "3", "3", "9", "9", "3", "5", "5", "5", "2", "2", "2", "6", "6", "6", "7", "4", "4", "1", "9", "1", "7", "7", "7", "4", "4", "4", "5", "5", "5", "4", "7", "7", "2", "2", "3", "9", "9", "9", "8", "8"));
        reelList.add(Arrays.asList("1", "9", "1", "8", "8", "6", "7", "7", "7", "6", "6", "9", "9", "9", "1", "6", "3", "3", "3", "5", "5", "5", "8", "8", "8", "4", "4", "4", "9", "9", "3", "8", "6", "6", "3", "3", "9", "7", "7", "7", "2", "2", "2", "6", "6", "6", "3", "3", "5", "8", "8", "8", "3", "5", "5"));
        reelList.add(Arrays.asList("1", "7", "1", "8", "8", "7", "7", "7", "4", "4", "4", "8", "8", "8", "9", "9", "9", "3", "3", "3", "8", "8", "9", "5", "5", "5", "1", "6", "1", "9", "9", "8", "5", "5", "5", "2", "2", "2", "7", "7", "4", "8", "8", "6", "4", "4", "6", "6", "8", "9", "9", "9"));
        reelList.add(Arrays.asList("1", "9", "1", "8", "8", "8", "5", "5", "5", "2", "2", "2", "7", "7", "2", "9", "6", "6", "4", "4", "4", "9", "9", "9", "9", "5", "5", "5", "7", "7", "3", "8", "8", "8", "2", "7", "7", "7", "3", "3", "6", "6", "6", "7", "7", "8", "9", "3", "7", "2", "9", "9", "6", "7", "7", "7"));
        REELS.put("main", reelList);

        SCREEN.add(Arrays.asList("6", "5", "4"));
        SCREEN.add(Arrays.asList("1", "7", "3"));
        SCREEN.add(Arrays.asList("6", "3", "8"));
        SCREEN.add(Arrays.asList("7", "5", "6"));
        SCREEN.add(Arrays.asList("7", "6", "3"));

        JSONObject special = new JSONObject();
        special.put("kind", "wild");
        special.put("symbol", "0");
        SPECIAL_SYMBOLS.add(special);
    }

    private static List<Integer> getMulList(int icon) {
        List<Integer> mulList = new ArrayList<>();
        for (int i = 1; i <= COLUMNS; i++) {
            int mul = getMul(icon, i);
            mulList.add(mul);
        }
        return mulList;
    }

    /**
     * 中奖线路
     * 0  1  2  3  4
     * 5  6  7  8  9
     * 10 11 12 13 14
     */
    public static final int[][] PRIZE_LINE = {
            {5, 6, 7, 8, 9}, {0, 1, 2, 3, 4}, {10, 11, 12, 13, 14}, {0, 6, 12, 8, 4}, {10, 6, 2, 8, 14},
            {5, 1, 2, 3, 9}, {5, 11, 12, 13, 9}, {0, 1, 7, 13, 14}, {10, 11, 7, 3, 4}, {10, 1, 12, 3, 14}
    };

    public static final int[][] PRIZE_LINE_OUT = {
            {1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2}, {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2},
            {1, 0, 0, 0, 1}, {1, 2, 2, 2, 1}, {0, 0, 1, 2, 2}, {2, 2, 1, 0, 0}, {2, 0, 2, 0, 2}
    };

    public static final int BASE_LINE = 1;

    public static int getRandomNormalIcon() {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = ICON_PRO.length - 1; i >= 0; i--) {
            pro += ICON_PRO[i];
            if (ran < pro) return ICONS_WITH_MULTIPLE[i];
        }
        return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
    }

    public static int getWildSize(double factor) {
        factor = factor > 1 ? factor : Math.pow(factor, 3);
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = WILD_PRO.length - 1; i >= 0; i--) {
            tmp += WILD_PRO[i];
            if (random <= tmp * factor) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 获取中奖线路的中奖倍数
     *
     * @param icon 中奖图标
     * @param line 中奖线
     * @return 中奖倍数
     */
    public static int getMul(int icon, int line) {
        for (int[] multiple : LotteryConfig.ICON_MULTIPLE) {
            if (multiple[0] == icon && multiple[1] == line) {
                return multiple[2];
            }
        }
        return 0;
    }
}
