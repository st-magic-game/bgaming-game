package com.bgaming.catdiana.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String BGAMING_COMMAND_FREE_SPIN = "freespin";
    public static final String BGAMING_COMMAND_RE_SPIN = "respin";

    public static final int ROWS = 3;

    public static final int COLUMNS = 5;

    public static final int SUB_UNITS = 100;

    public static final int WILD = 8;

    public static final int SCATTER = 9;

    public static final int COIN = 10;

    public static final int BASE_LINE = 25;

    public static final int FREE_NUM = 5;

    public static final int FREE_TO_FREE_NUM = 3;

    public static final double SMALL_WIN_PRO = 0.1397;

    public static final double FREE_SMALL_WIN_PRO = 0.197;

    public static final double LONG_LINES_PRO = 0.005;

    public static final double[] SCATTER_PRO = {0.2, 0.02, 0.00121};

    public static final double[] COINS_PRO = {0.1, 0.1, 0.1, 0.05, 0.005, 0.003};

    static final double[] WILD_PRO = {0.1051, 0.05, 0.01, 0.001};

    static final double[] ICON_PRO = {0.15504984035, 0.1563417662, 0.1565787746, 0.1383841644, 0.1252, 0.11, 0.099948568, 0.0555926849};

    public static final int[] ICONS_WITH_MULTIPLE = {0, 1, 2, 3, 4, 5, 6, 7};

    public static final int[] COINS_VALUE = {1, 2, 3, 4, 5, 6, 7, 8, 10, 14, 16, 18, 20, 24, 30, 100};

    public static final double[] COINS_VALUE_PRO = {
            0.34,   // 1
            0.24,   // 2
            0.14,   // 3
            0.09,   // 4
            0.06,   // 5
            0.04,   // 6
            0.025,  // 7
            0.018,  // 8
            0.012,  // 10
            0.008,  // 14
            0.006,  // 16
            0.005,  // 18
            0.004,  // 20
            0.003,  // 24
            0.002,   // 30
            0, // 100
    };

    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    // J
                    {0, 3, 5}, {0, 4, 20}, {0, 5, 50},
                    // Q
                    {1, 3, 5}, {1, 4, 20}, {1, 5, 50},
                    // K
                    {2, 3, 5}, {2, 4, 20}, {2, 5, 50},
                    // A
                    {3, 3, 5}, {3, 4, 20}, {3, 5, 50},
                    // 帽子
                    {4, 3, 10}, {4, 4, 50}, {4, 5, 200},
                    // 绿宝石
                    {5, 3, 15}, {5, 4, 100}, {5, 5, 300},
                    // 猫
                    {6, 3, 20}, {6, 4, 150}, {6, 5, 400},
                    // 紫宝石
                    {7, 3, 25}, {7, 4, 250}, {7, 5, 500},
                    // WILD
                    {WILD, 3, 25}, {WILD, 4, 250}, {WILD, 5, 500},
                    // SCATTER 倍数不除 BASE_LINE   star
                    {SCATTER, 3, 1}, {SCATTER, 4, 1}, {SCATTER, 5, 1},
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
            {5, 6, 7, 8, 9},        // Line 1
            {0, 1, 2, 3, 4},        // Line 2
            {10, 11, 12, 13, 14},   // Line 3
            {0, 6, 12, 8, 4},       // Line 4

            {10, 6, 2, 8, 14},      // Line 5
            {5, 1, 2, 3, 9},        // Line 6
            {5, 11, 12, 13, 9},     // Line 7
            {0, 1, 7, 13, 14},      // Line 8

            {10, 11, 7, 3, 4},      // Line 9
            {5, 11, 7, 3, 9},       // Line 10
            {5, 1, 7, 13, 9},       // Line 11
            {0, 6, 7, 8, 4},        // Line 12

            {10, 6, 7, 8, 14},      // Line 13
            {0, 6, 2, 8, 4},        // Line 14
            {10, 6, 12, 8, 14},     // Line 15
            {5, 6, 2, 8, 9},        // Line 16

            {5, 6, 12, 8, 9},       // Line 17
            {0, 1, 12, 3, 4},        // Line 18
            {10, 11, 2, 13, 14},    // Line 19
            {0, 11, 12, 13, 4},     // Line 20

            {10, 1, 2, 3, 14},      // Line 21
            {5, 11, 2, 13, 9},      // Line 22
            {5, 1, 12, 3, 9},       // Line 23
            {0, 11, 2, 13, 4},      // Line 24

            {10, 1, 12, 3, 14}      // Line 25
    };

    public static final int[][] PRIZE_LINE_OUT = {
            {1, 1, 1, 1, 1},
            {0, 0, 0, 0, 0},
            {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0},

            {2, 1, 0, 1, 2},
            {1, 0, 0, 0, 1},
            {1, 2, 2, 2, 1},
            {0, 0, 1, 2, 2},

            {2, 2, 1, 0, 0},
            {1, 2, 1, 0, 1},
            {1, 0, 1, 2, 1},
            {0, 1, 1, 1, 0},

            {2, 1, 1, 1, 2},
            {0, 1, 0, 1, 0},
            {2, 1, 2, 1, 2},
            {1, 1, 0, 1, 1},

            {1, 1, 2, 1, 1},
            {0, 0, 2, 0, 0},
            {2, 2, 0, 2, 2},
            {0, 2, 2, 2, 0},

            {2, 0, 0, 0, 2},
            {1, 2, 0, 2, 1},
            {1, 0, 2, 0, 1},
            {0, 2, 0, 2, 0},

            {2, 0, 2, 0, 2}
    };

    public static int getRandomNormalIcon(double fixed) {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = ICON_PRO.length - 1; i >= 0; i--) {
            pro += ICON_PRO[i];
            if (ran < pro * fixed) return ICONS_WITH_MULTIPLE[i];
        }
        return ICONS_WITH_MULTIPLE[0];
    }

    public static int getWildSize(int type) {
        double fixed = type == 1 ? 0.1 : 1;
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

    public static int getCoinsSize() {
        double random = RandomUtil.nextDouble();
        for (int i = COINS_PRO.length - 1; i >= 0; i--) {
            if (random > COINS_PRO[i]) continue;

            return i + 1;
        }
        return 0;
    }

    public static int getCoinMul() {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = COINS_VALUE_PRO.length - 1; i >= 0; i--) {
            pro += COINS_VALUE_PRO[i];
            if (ran < pro) return COINS_VALUE[i];
        }
        return COINS_VALUE[0];
    }
}
