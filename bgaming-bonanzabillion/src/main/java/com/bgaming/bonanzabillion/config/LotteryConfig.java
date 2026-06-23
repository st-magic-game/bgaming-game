package com.bgaming.bonanzabillion.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String PURCHASED_FEATURE = "purchased_feature";
    public static final String PURCHASED_FREE_SPIN_CHANCE = "freespin_chance";
    public static final String PURCHASED_FREE_SPIN_BUY = "freespin_buy";
    public static final String REQUEST_TYPE = "requestType";
    public static final String BGAMING_COMMAND_FREE_SPIN = "freespins";
    public static final int REQUEST_TYPE_NOR = 0;
    public static final int REQUEST_TYPE_FREE = 1;
    public static final int REQUEST_TYPE_CHANCEX2 = 2;
    public static final int REQUEST_TYPE_BUY_FREE = 3;
    public static final int ROWS = 5;

    public static final int COLUMNS = 6;

    public static final int SUB_UNITS = 100;

    public static final int MULTI_ICON = 10;

    public static final int SCATTER = 9;

    public static final int BASE_LINE = 20;

    public static final int[] FREE_NUM = {10, 20, 30};

    public static final int FREE_ADD = 5;

    public static final double SMALL_WIN_PRO = 0.305;

    public static final double[] BET_TYPE_MUL = {1, 1, 1.25, 100};

    public static final double[] SCATTER_PRO = {0.2, 0.05, 0.01, 0.008, 0.005, 0};

    static final double[] MULTI_ICON_SIZE_PRO = {0.21, 0.10, 0.01};

    static final int[] MULTI_ICON_MUL = {2, 3, 5, 6, 8, 10, 15, 20, 25, 50, 100};

    static final double[] MULTI_ICON_PRO = {0.010722846, 0.011417023, 0.0103040372, 0.033087371, 0.027682474, 0.047483668, 0.039211825, 0.032255957, 0.0130432992, 0.01000939982, 0.00000139982};

    static final double[] ICON7_PRO = {0.1, 0.12, 0.13, 0.10, 0.10, 0.10, 0.35};

    static final double[] ICON_PRO = {0.05, 0.080062003, 0.109753073, 0.117278421, 0.112926849, 0.119948568, 0.12, 0.1252, 0.163841644};

    public static final int[] ICONS_WITH_MULTIPLE = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    public static final Map<Integer, int[]> PAY_TABLE_LEVELS = new TreeMap<>();

    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    // 绿钻石
                    {0, 8, 200}, {0, 9, 200}, {0, 10, 500}, {0, 11, 500}, {0, 12, 1000},
                    // 蓝宝石
                    {1, 8, 50}, {1, 9, 50}, {1, 10, 200}, {1, 11, 200}, {1, 12, 500},
                    // 黄色五角星
                    {2, 8, 40}, {2, 9, 40}, {2, 10, 100}, {2, 11, 100}, {2, 12, 300},
                    // 红色桃心
                    {3, 8, 30}, {3, 9, 30}, {3, 10, 40}, {3, 11, 40}, {3, 12, 240},
                    // 西瓜
                    {4, 8, 20}, {4, 9, 20}, {4, 10, 30}, {4, 11, 30}, {4, 12, 200},
                    // 西梅
                    {5, 8, 16}, {5, 9, 16}, {5, 10, 24}, {5, 11, 24}, {5, 12, 160},
                    // 柠檬
                    {6, 8, 10}, {6, 9, 10}, {6, 10, 20}, {6, 11, 20}, {6, 12, 100},
                    // 橙子
                    {7, 8, 8}, {7, 9, 8}, {7, 10, 18}, {7, 11, 18}, {7, 12, 80},
                    // 樱桃
                    {8, 8, 5}, {8, 9, 5}, {8, 10, 15}, {8, 11, 15}, {8, 12, 40},
                    // SCATTER 倍数不除 BASE_LINE
                    {SCATTER, 4, 3}, {SCATTER, 5, 5}, {SCATTER, 6, 100},
            };
    public static Map<Integer, List<Integer>> PAYTABLE = new HashMap<>();
    public static List<List<String>> SCREEN = new ArrayList<>();
    public static List<JSONObject> SPECIAL_SYMBOLS = new ArrayList<>();
    public static JSONObject SPECIAL_PAY_TABLE = new JSONObject();
    public static JSONObject FEATURE_OPTIONS = new JSONObject();

    static {
        int[] level1 = {8, 9};
        int[] level2 = {10, 11};
        int[] level3 = {12, 30};
        PAY_TABLE_LEVELS.put(1, level1);
        PAY_TABLE_LEVELS.put(2, level2);
        PAY_TABLE_LEVELS.put(3, level3);
        initOptions();
    }

    private static void initOptions() {
        for (int icon : ICONS_WITH_MULTIPLE) {
            List<Integer> mulList = getMulList(icon);
            PAYTABLE.put(icon, mulList);
        }

        SCREEN.add(Arrays.asList("2", "5", "5", "8", "8"));
        SCREEN.add(Arrays.asList("6", "6", "5", "5", "2"));
        SCREEN.add(Arrays.asList("6", "6", "5", "5", "3"));
        SCREEN.add(Arrays.asList("0", "0", "2", "2", "3"));
        SCREEN.add(Arrays.asList("3", "3", "2", "2", "4"));
        SCREEN.add(Arrays.asList("7", "3", "3", "4", "4"));

        JSONObject scatter = new JSONObject();
        scatter.put("kind", "scatter");
        scatter.put("symbol", "9");
        SPECIAL_SYMBOLS.add(scatter);

        SPECIAL_PAY_TABLE = JSONObject.parseObject("{\"[:scatter, \\\"9\\\"]\":{\"4\":3,\"5\":5,\"6\":100}}");
        FEATURE_OPTIONS = JSONObject.parseObject("{\"feature_multipliers\":{\"freespin_chance\":125,\"freespin_buy\":10000},\"disabled_features\":[]}");
    }

    private static List<Integer> getMulList(int icon) {
        List<Integer> mulList = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int mul = getMul(icon, PAY_TABLE_LEVELS.get(i)[0]);
            mulList.add(mul);
        }
        return mulList;
    }

    public static int getRandomNormalIcon(double factor) {
        if (factor < 1) {
            double ran = RandomUtil.nextDouble();
            double pro = 0d;
            for (int i = ICON_PRO.length - 1; i >= 0; i--) {
                pro += ICON_PRO[i];
                if (ran < pro) return ICONS_WITH_MULTIPLE[i];
            }
            return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
        }
        double[] pros = ICON7_PRO;
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = pros.length - 1; i >= 0; i--) {
            pro += pros[i];
            if (ran < pro) return ICONS_WITH_MULTIPLE[i + 1];
        }
        return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
    }

    public static int getMuliIconMul(double fixedValue) {
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = MULTI_ICON_PRO.length - 1; i >= 0; i--) {
            tmp += MULTI_ICON_PRO[i];
            if (random <= tmp * fixedValue) {
                return MULTI_ICON_MUL[i];
            }
        }
        return 2;
    }

    public static int getMultiIconSize() {
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = MULTI_ICON_SIZE_PRO.length - 1; i >= 0; i--) {
            tmp += MULTI_ICON_SIZE_PRO[i];
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

    public static int getScatterSize(double fixedValue) {
        double random = RandomUtil.nextDouble();
        for (int i = SCATTER_PRO.length - 1; i >= 0; i--) {
            if (random > SCATTER_PRO[i] * fixedValue) continue;

            return i + 1;
        }
        return 0;
    }
}
