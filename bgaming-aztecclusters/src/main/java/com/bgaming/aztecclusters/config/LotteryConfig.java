package com.bgaming.aztecclusters.config;


import com.alibaba.fastjson.JSONObject;
import com.game.base.common.util.RandomUtil;

import java.util.*;

public class LotteryConfig {
    public static final String PURCHASED_FEATURE = "purchased_feature";
    public static final String PURCHASED_FREE_SPIN_CHANCE = "bonus_buy";
    public static final String PURCHASED_FREE_SPIN_BUY = "freespin_buy";
    public static final String REQUEST_TYPE = "requestType";
    public static final String BGAMING_COMMAND_FREE_SPIN = "freespins";
    public static final int REQUEST_TYPE_NOR = 0;
    public static final int REQUEST_TYPE_WILD_CHANCE = 1;
    public static final int REQUEST_TYPE_FREE_0 = 2;
    public static final int REQUEST_TYPE_FREE_1 = 3;
    public static final int REQUEST_TYPE_FREE_2 = 4;
    public static final int REQUEST_TYPE_FREE_3 = 5;
    public static final int ROWS = 8;
    public static final int COLUMNS = 6;
    public static final int SUB_UNITS = 100;
    public static final int WILD = 0;
    public static final int SCATTER = 8;
    public static final int BASE_LINE = 100;
    public static final int[] FREE_NUM = {10, 12, 15, 20};
    public static final double NO_WIN_PRO = 0.235;
    public static final double LONG_LINE_WIN_PRO = 0.07;
    public static final double DESTROY_PRO = 0.05;
    public static final double MULTI_PRO = 0.1;
    public static final double DROP_SCATTER_PRO = 0.01;
    public static final int[] BET_TYPE_MUL = {1, 20, 100, 200, 400, 800};
    public static final double[] BET_TYPE_FACTOR = {1, 20, 100, 200, 400, 800};
    public static final double[] SCATTER_PRO = {0.2, 0.05, 0.005, 0.003, 0.001, 0};
    public static final double[] DROP_WILD_PRO = {0.05, 0.01};
    static final double[] MULTI_ICON_SIZE_PRO = {0.11, 0.05, 0.01};
    static final double[] WILD_CHANCE_SIZE_PRO = {0.19, 0.45, 0.25, 0.01};
    static final double[] ICON_PRO = {0.10, 0.12, 0.13, 0.15, 0.15, 0.15, 0.20};
    public static final int[] ICONS_WITH_MULTIPLE = {1, 2, 3, 4, 5, 6, 7};
    public static final int[][] ICON_MULTIPLE = new int[][]
            {
                    //1 紫头
                    {80, 120, 140, 160, 200, 400, 600, 1200, 2800, 5600, 12000},
                    //2 红头
                    {60, 80, 100, 120, 160, 320, 480, 1000, 2400, 4800, 8000},
                    //3 绿头
                    {40, 60, 80, 100, 120, 240, 360, 800, 1600, 3200, 4800},
                    //4 粉色
                    {32, 40, 60, 80, 100, 160, 240, 400, 800, 1600, 3200},
                    //5 红色
                    {24, 32, 40, 60, 80, 120, 200, 280, 640, 1200, 2400},
                    //6 绿色
                    {20, 24, 32, 40, 60, 100, 160, 240, 480, 960, 2000},
                    //7 深紫色
                    {16, 20, 24, 32, 40, 80, 120, 200, 400, 800, 1600},
            };
    public static Map<Integer, int[]> PAYTABLE = new HashMap<>();
    public static List<List<String>> SCREEN = new ArrayList<>();
    public static List<JSONObject> SPECIAL_SYMBOLS = new ArrayList<>();
    public static JSONObject FEATURE_OPTIONS = new JSONObject();
    public static JSONObject DIGIT_OPTIONS = new JSONObject();

    static {
        initOptions();
    }

    private static void initOptions() {
        for (int icon : ICONS_WITH_MULTIPLE) {
            PAYTABLE.put(icon, getMulList(icon));
        }

        SCREEN.add(Arrays.asList("4", "6", "6", "6", "2", "2", "6", "6"));
        SCREEN.add(Arrays.asList("7", "5", "5", "2", "2", "5", "5", "1"));
        SCREEN.add(Arrays.asList("6", "2", "2", "3", "3", "6", "4", "4"));
        SCREEN.add(Arrays.asList("7", "7", "3", "3", "5", "7", "7", "3"));
        SCREEN.add(Arrays.asList("6", "4", "2", "2", "2", "6", "6", "2"));
        SCREEN.add(Arrays.asList("5", "6", "6", "5", "1", "1", "8", "1"));

        JSONObject scatter = new JSONObject();
        scatter.put("kind", "scatter");
        scatter.put("symbol", String.valueOf(SCATTER));
        SPECIAL_SYMBOLS.add(scatter);

        JSONObject wild = new JSONObject();
        wild.put("kind", "wild");
        wild.put("symbol", String.valueOf(WILD));
        SPECIAL_SYMBOLS.add(wild);
        FEATURE_OPTIONS = JSONObject.parseObject("{\"feature_multipliers\":{\"bonus_buy\":2000,\"freespin_buy\":{\"0\":10000,\"1\":20000,\"2\":40000,\"3\":80000}},\"disabled_features\":[]}");
        DIGIT_OPTIONS = JSONObject.parseObject("{\"bet\":2,\"paytable\":2,\"balance\":2}");
    }

    private static int[] getMulList(int icon) {
        int[] ints = ICON_MULTIPLE[icon - 1];
        int[] ints1 = Arrays.copyOf(ints, ints.length);
        for (int i = 0; i < ints1.length; i++) {
            ints1[i] /= 4;
        }
        return ints1;
    }

    public static int getRandomNormalIcon(double factor) {
        double ran = RandomUtil.nextDouble();
        double pro = 0d;
        for (int i = ICON_PRO.length - 1; i >= 0; i--) {
            pro += ICON_PRO[i];
            if (ran < pro) return ICONS_WITH_MULTIPLE[i];
        }
        return ICONS_WITH_MULTIPLE[ICONS_WITH_MULTIPLE.length - 1];
    }

    /**
     * 获取中奖线路的中奖倍数
     *
     * @param icon 中奖图标
     * @param line 中奖线
     * @return 中奖倍数
     */
    public static int getMul(Integer icon, int line) {
        int len = Math.min(line, 15);
        return LotteryConfig.ICON_MULTIPLE[icon - 1][len - 5];
    }

    public static int getScatterSize(double fixedValue) {
        double random = RandomUtil.nextDouble();
        for (int i = SCATTER_PRO.length - 1; i >= 0; i--) {
            if (random > SCATTER_PRO[i] * fixedValue) continue;

            return i + 1;
        }
        return 0;
    }

    public static int getDropWildSize() {
        double random = RandomUtil.nextDouble();
        for (int i = DROP_WILD_PRO.length - 1; i >= 0; i--) {
            if (random > DROP_WILD_PRO[i]) continue;

            return i + 1;
        }
        return 0;
    }

    public static int getWildSize(int betType, double factor) {
        if (betType > REQUEST_TYPE_WILD_CHANCE) return 0;

        int wildSize = 0;
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = MULTI_ICON_SIZE_PRO.length - 1; i >= 0; i--) {
            tmp += MULTI_ICON_SIZE_PRO[i];
            if (random <= tmp * factor) {
                wildSize = i + 1;
                break;
            }
        }
        if (betType == REQUEST_TYPE_WILD_CHANCE) {
            wildSize = getWildChanceSize(factor);
        }
        return wildSize;
    }

    private static int getWildChanceSize(double factor) {
        int wildSize = 1;
        double random = RandomUtil.nextDouble();
        double tmp = 0;
        for (int i = WILD_CHANCE_SIZE_PRO.length - 1; i >= 0; i--) {
            tmp += WILD_CHANCE_SIZE_PRO[i];
            if (random <= tmp * factor) {
                wildSize = i + 1;
                break;
            }
        }

        return wildSize;
    }
}
