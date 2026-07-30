package com.bgaming.pandaluck.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String PURCHASED_FEATURE = "purchased_feature";
    public static final String PURCHASED_BONUS_SPIN = "bonus_buy";
    public static final String BET_TYPE = "betType";
    /**
     * 行数
     */
    public static final int ROWS = 3;

    /**
     * 列数
     */
    public static final int COLUMNS = 3;

    public static final int SUB_UNITS = 100;

    public static final int BASE_LINE = 10;
    /**
     * 夺宝单轴不重复
     */
    public static final int SCATTER = 0;

    public static final double SMALL_WIN_PRO = 0.697;

    public static final double LONG_LINES_PRO = 0.07;

    public static final int[] REQUEST_TYPE_MUL = {1, 60};

    static final double[] SCATTER_PRO = {0.25, 0.05, 0.008, 0.002};

    static final double[] ICON_PRO = {0.079753073, 0.087278421, 0.092926849, 0.099948568, 0.163841644, 0.167787746, 0.153417662, 0.154984035};

    public static final int[] ICONS_WITH_MULTIPLE = {1, 2, 3, 4, 5, 6, 7, 8};

    public static final double[] BASE_PRO = {
            0.30,  //3
            0.28,  //4
            0.20,  //5
            0.14,  //6
            0.06,  //7
            0.018, //8
            0.002  //9
    };

    public static final double[] LOW_PRO = {
            0.35,
            0.30,
            0.20,
            0.10,
            0.04,
            0.009,
            0.001
    };

    public static final double[] HIGH_PRO = {
            0.184,
            0.29,
            0.29,
            0.15,
            0.08,
            0.005,
            0.001
    };
    /**
     * 各图标对应的倍数 {图标索引,3线倍数,4线倍数,5线倍数}
     */
    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    // bonus
                    {0, 3, 3}, {0, 4, 10}, {0, 5, 20}, {0, 6, 50}, {0, 7, 100}, {0, 8, 500}, {0, 9, 2024},
                    // 锣鼓
                    {1, 3, 1000},
                    // 鞭炮
                    {2, 3, 500},
                    // 金币
                    {3, 3, 200},
                    // A
                    {4, 3, 40},
                    // K
                    {5, 3, 40},
                    // Q
                    {6, 3, 40},
                    // J
                    {7, 3, 40},
                    // 10
                    {8, 3, 10},
            };
    public static Map<Integer, List<Integer>> PAYTABLE = new HashMap<>();
    public static Map<Integer, Map<String, List<Integer>>> PAYTABLES = new HashMap<>();
    public static Map<String, List<List<String>>> REELS = new HashMap<>();
    public static List<List<String>> SCREEN = new ArrayList<>();
    public static List<JSONObject> SPECIAL_SYMBOLS = new ArrayList<>();
    public static JSONObject FEATURE_OPTIONS = new JSONObject();
    public static JSONObject DIGIT_OPTIONS = new JSONObject();

    static {
        initOptions();
    }

    private static void initOptions() {
        DIGIT_OPTIONS.put("balance", 2);
        DIGIT_OPTIONS.put("bet", 2);
        DIGIT_OPTIONS.put("paytable", 2);

        JSONObject feature_multipliers = new JSONObject();
        feature_multipliers.put("base_bet", BASE_LINE);
        feature_multipliers.put("bonus_buy", 600);
        FEATURE_OPTIONS.put("disabled_features", new ArrayList<>());
        FEATURE_OPTIONS.put("feature_multipliers", feature_multipliers);

        for (int icon : ICONS_WITH_MULTIPLE) {
            List<Integer> mulList = getMulList(icon);
            PAYTABLE.put(icon, mulList);

            Map<String, List<Integer>> defaultMul = new HashMap<>();
            defaultMul.put("default", mulList);
            PAYTABLES.put(icon, defaultMul);
        }

        List<List<String>> reelList = new ArrayList<>();
        reelList.add(Arrays.asList("2", "8", "8", "8", "8", "4", "3", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "1", "7", "7", "7", "7", "8", "2", "8", "8", "8", "8", "4", "3", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "1", "7", "7", "7", "7", "8", "8", "8", "8", "8"));
        reelList.add(Arrays.asList("2", "4", "6", "4", "6", "6", "6", "3", "7", "3", "7", "7", "7", "7", "8", "1", "8", "8", "8", "4", "5", "4", "5", "5", "5", "2", "4", "6", "4", "6", "6", "6", "3", "7", "3", "7", "7", "7", "7", "8", "1", "8", "8", "8", "4", "5", "4", "5", "5", "5", "8", "8", "8", "8"));
        reelList.add(Arrays.asList("2", "8", "8", "8", "8", "4", "4", "1", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "7", "7", "7", "7", "8", "2", "8", "8", "8", "8", "4", "4", "1", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "7", "7", "7", "7", "8", "8", "8", "8", "8"));
        REELS.put("main", reelList);

        List<List<String>> bonus_buy = new ArrayList<>();
        bonus_buy.add(Arrays.asList("3", "5", "5", "5", "5", "3", "3", "1", "7", "7", "7", "7", "3", "5", "5", "5", "5", "3", "3", "1", "7", "7", "7", "7"));
        bonus_buy.add(Arrays.asList("2", "4", "6", "4", "6", "6", "6", "8", "8", "8", "8", "4", "4", "2", "4", "6", "4", "6", "6", "6", "8", "8", "8", "8", "4", "4", "8", "8", "8", "8"));
        bonus_buy.add(Arrays.asList("2", "8", "8", "8", "8", "4", "4", "1", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "7", "7", "7", "7", "8", "2", "8", "8", "8", "8", "4", "4", "1", "5", "5", "5", "5", "4", "4", "6", "6", "6", "6", "3", "3", "7", "7", "7", "7", "8", "8", "8", "8", "8"));
        REELS.put("bonus_buy", bonus_buy);

        SCREEN.add(Arrays.asList("6", "3", "3"));
        SCREEN.add(Arrays.asList("0", "5", "5"));
        SCREEN.add(Arrays.asList("0", "8", "8"));

        JSONObject special = new JSONObject();
        special.put("kind", "scatter");
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
     * 0  1  2
     * 3  4  5
     * 6  7  8
     */
    public static final int[][] PRIZE_LINE = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 4, 8}, {6, 4, 2}
    };

    public static final int[][] PRIZE_LINE_OUT = {
            {0, 0, 0}, {1, 1, 1}, {2, 2, 2}, {0, 1, 2}, {2, 1, 0}
    };

    public static final List<Integer> INDEXES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    public static final List<Integer> ALL_ICONS = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);

    public static int getRandomNormalIcon() {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = ICON_PRO.length - 1; i >= 0; i--) {
            pro += ICON_PRO[i];
            if (ran < pro) return ICONS_WITH_MULTIPLE[i];
        }
        return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
    }

    public static int getScatterSize() {
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = SCATTER_PRO.length - 1; i >= 0; i--) {
            tmp += SCATTER_PRO[i];
            if (random <= tmp) {
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
