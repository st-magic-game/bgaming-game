package com.bgaming.candymonsta.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String REQUEST_TYPE = "requestType";

    public static final String BGAMING_COMMAND_FREE_SPIN = "freespins";

    public static final int ROWS = 3;

    public static final int COLUMNS = 5;

    public static final int SUB_UNITS = 100;

    public static final int WILD = 13;

    public static final int SCATTER = 12;

    public static final int BASE_LINE = 20;

    public static final int FREE_NUM = 10;

    public static final double SMALL_WIN_PRO = 0.397;

    public static final double FREE_SMALL_WIN_PRO = 0.197;

    public static final double LONG_LINES_PRO = 0.07;

    public static final double[] SCATTER_PRO = {0.2, 0.05, 0.01, 0.008};

    static final double[] WILD_PRO = {0.151, 0.20, 0.05, 0.001};

    static final double[] ICON_PRO = {0.05, 0.070062003, 0.069753073, 0.077278421, 0.072926849, 0.079948568, 0.08, 0.0852, 0.093841644, 0.1015787746, 0.1013417662, 0.1104984035};

    public static final int[] ICONS_WITH_MULTIPLE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    // Monsta
                    {0, 3, 50}, {0, 4, 500}, {0, 5, 1000},
                    // 红桃心
                    {1, 3, 40}, {1, 4, 100}, {1, 5, 400},
                    // 熊
                    {2, 3, 40}, {2, 4, 100}, {2, 5, 400},
                    // 黄五角星
                    {3, 3, 30}, {3, 4, 50}, {3, 5, 300},
                    // 蓝方形
                    {4, 3, 30}, {4, 4, 50}, {4, 5, 300},
                    // 紫贝壳
                    {5, 3, 20}, {5, 4, 40}, {5, 5, 200},
                    // 绿豌豆
                    {6, 3, 20}, {6, 4, 40}, {6, 5, 200},
                    // A
                    {7, 3, 15}, {7, 4, 25}, {7, 5, 100},
                    // K
                    {8, 3, 15}, {8, 4, 25}, {8, 5, 100},
                    // Q
                    {9, 3, 10}, {9, 4, 20}, {9, 5, 80},
                    // J
                    {10, 3, 10}, {10, 4, 20}, {10, 5, 80},
                    // 10
                    {11, 3, 10}, {11, 4, 20}, {11, 5, 80},
                    // SCATTER 倍数不除 BASE_LINE
                    {SCATTER, 3, 2}, {SCATTER, 4, 10}, {SCATTER, 5, 100},
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
        reelList.add(Arrays.asList("8", "5", "5", "5", "8", "10", "0", "1", "1", "8", "6", "6", "6", "7", "10", "9", "3", "3", "3", "8", "10", "4", "4", "4", "9", "11", "7", "12", "7", "11", "9", "11", "7", "2", "2", "10", "10", "9"));
        reelList.add(Arrays.asList("3", "3", "0", "1", "1", "1", "4", "4", "13", "11", "7", "9", "5", "5", "5", "13", "10", "9", "11", "12", "11", "8", "2", "2", "8", "10", "11", "7", "9", "6", "6", "6", "10", "9", "0", "10", "7", "13"));
        reelList.add(Arrays.asList("2", "2", "1", "11", "0", "7", "9", "4", "4", "8", "12", "10", "3", "3", "3", "8", "11", "11", "7", "8", "5", "5", "11", "8", "0", "9", "6", "6", "10", "9", "11", "13", "13", "13", "9", "6", "6", "6", "13", "5", "5", "5"));
        reelList.add(Arrays.asList("2", "2", "3", "3", "12", "4", "4", "10", "13", "8", "8", "9", "7", "7", "0", "9", "8", "10", "1", "1", "10", "8", "7", "9", "8", "6", "6", "6", "7", "7", "9", "11", "11", "5", "5", "5", "9", "10"));
        reelList.add(Arrays.asList("2", "8", "10", "0", "3", "3", "4", "4", "8", "11", "12", "9", "11", "13", "2", "1", "1", "7", "0", "11", "5", "5", "0", "9", "10", "6", "6", "6", "9", "7", "10", "9", "10", "9", "12", "11", "10", "11", "11", "3", "3", "3"));
        REELS.put("main", reelList);

        List<List<String>> freeReelList = new ArrayList<>();
        freeReelList.add(Arrays.asList("8", "5", "5", "5", "8", "10", "0", "2", "2", "8", "6", "6", "6", "7", "10", "9", "3", "3", "3", "8", "4", "4", "4", "9", "11", "7", "12", "7", "11", "9", "11", "1", "1", "1", "10", "10", "9"));
        freeReelList.add(Arrays.asList("3", "3", "0", "2", "2", "2", "4", "4", "13", "11", "7", "9", "5", "5", "13", "10", "9", "11", "12", "11", "8", "1", "1", "8", "10", "11", "7", "9", "6", "6", "6", "10", "9", "0", "10", "7", "13"));
        freeReelList.add(Arrays.asList("1", "1", "2", "2", "0", "7", "9", "4", "4", "8", "12", "10", "3", "3", "8", "11", "11", "7", "8", "5", "5", "11", "8", "0", "9", "6", "6", "10", "9", "11", "13", "9", "6", "6", "6", "13", "5", "5", "5"));
        freeReelList.add(Arrays.asList("1", "1", "3", "3", "12", "4", "4", "5", "13", "8", "8", "9", "7", "7", "0", "9", "8", "10", "2", "2", "10", "8", "7", "9", "8", "6", "6", "6", "7", "7", "9", "11", "11", "13", "5", "5", "5", "9", "0"));
        freeReelList.add(Arrays.asList("1", "8", "10", "0", "3", "3", "4", "4", "8", "11", "12", "9", "11", "1", "2", "2", "7", "5", "5", "0", "9", "10", "6", "6", "6", "9", "7", "10", "9", "10", "9", "12", "11", "10", "11", "11", "3", "3", "3"));
        REELS.put("freespins", freeReelList);

        SCREEN.add(Arrays.asList("8", "5", "5"));
        SCREEN.add(Arrays.asList("3", "3", "0"));
        SCREEN.add(Arrays.asList("2", "2", "1"));
        SCREEN.add(Arrays.asList("2", "2", "3"));
        SCREEN.add(Arrays.asList("2", "8", "10"));

        JSONObject wild = new JSONObject();
        wild.put("kind", "wild");
        wild.put("symbol", "13");
        SPECIAL_SYMBOLS.add(wild);

        JSONObject scatter = new JSONObject();
        scatter.put("kind", "scatter");
        scatter.put("symbol", "12");
        SPECIAL_SYMBOLS.add(scatter);
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
            {5, 6, 7, 8, 9},      // Line 1
            {0, 1, 2, 3, 4},      // Line 2
            {10, 11, 12, 13, 14}, // Line 3
            {0, 6, 12, 8, 4},     // Line 4
            {10, 6, 2, 8, 14},    // Line 5
            {0, 1, 7, 3, 4},    // Line 6
            {10, 11, 7, 13, 14},    // Line 7
            {5, 1, 2, 3, 9},      // Line 8
            {5, 11, 12, 13, 9},   // Line 9
            {0, 6, 7, 8, 4},     // Line 10
            {10, 6, 7, 8, 14},     // Line 11
            {0, 6, 2, 8, 4},    // Line 12
            {10, 6, 12, 8, 14},     // Line 13
            {5, 1, 7, 3, 9},      // Line 14
            {5, 11, 7, 13, 9},     // Line 15
            {5, 6, 2, 8, 9},      // Line 16
            {5, 6, 12, 8, 9},   // Line 17
            {0, 11, 2, 13, 4},     // Line 18
            {10, 1, 12, 3, 14},     // Line 19
            {5, 1, 12, 3, 9}     // Line 20
    };

    public static final int[][] PRIZE_LINE_OUT = {
            {1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2}, {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2},
            {0, 0, 1, 0, 0}, {2, 2, 1, 2, 2}, {1, 0, 0, 0, 1}, {1, 2, 2, 2, 1}, {0, 1, 1, 1, 0},
            {2, 1, 1, 1, 2}, {0, 1, 0, 1, 0}, {2, 1, 2, 1, 2}, {1, 0, 1, 0, 1}, {1, 2, 1, 2, 1},
            {1, 1, 0, 1, 1}, {1, 1, 2, 1, 1}, {0, 2, 0, 2, 0}, {2, 0, 2, 0, 2}, {1, 0, 2, 0, 1}
    };

    public static int getRandomNormalIcon() {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = ICON_PRO.length - 1; i >= 0; i--) {
            pro += ICON_PRO[i];
            if (ran < pro) return ICONS_WITH_MULTIPLE[i];
        }
        return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
    }

    public static int getWildSize(int type) {
        double fixed = type == 1 ? 0.606 : 1;
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = WILD_PRO.length - 1; i >= 0; i--) {
            tmp += WILD_PRO[i];
            if (random <= tmp * fixed) {
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

    public static int getScatterSize(double fixedValue) {
        double random = RandomUtil.nextDouble();
        for (int i = SCATTER_PRO.length - 1; i >= 0; i--) {
            if (random > SCATTER_PRO[i] * fixedValue) continue;

            return i + 1;
        }
        return 0;
    }
}
