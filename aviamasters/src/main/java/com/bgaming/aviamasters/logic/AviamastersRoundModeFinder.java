package com.bgaming.aviamasters.logic;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AviamastersRoundModeFinder {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public enum WinMode {
        /**
         * win == 0
         */
        ZERO,

        /**
         * 0 < win < bet
         */
        SMALL_NOT_ZERO,

        /**
         * win > bet，并且 win <= bet * maxMultiplier
         */
        BIG
    }

    @Data
    public static class FindOptions {
        /**
         * 最大尝试次数
         */
        private int maxTryCount = 50_000;

        /**
         * 大奖最大倍率。
         * 例如 100 表示 win 最大不能超过 bet * 100。
         */
        private double maxMultiplier = 50;

        /**
         * 是否从随机 seed 开始顺序扫。
         * 推荐 true。
         */
        private boolean randomStartSeed = true;

        /**
         * 吃到的 events 数量必须 <= 这个值
         */
        private int maxCollectedEventCount = 15;
    }

    @Data
    public static class RoundProcessResult implements Serializable{

        public boolean success;

        public WinMode mode;

        public int seed;

        public long bet;

        public long win;

        public boolean landed;

        public double multiplier;

        public int frames;

        public int collectedEventCount;

        public int tryCount;

        public double maxMultiplier;

        public List<EventDTO> events = new ArrayList<>();

        public String message;

        private String pOrder;
    }

    @Data
    public static class EventDTO implements Serializable {

        @JSONField(serialize = false)
        public int frame;

        @JSONField(serialize = false)
        public int bonusId;

        @JSONField(serialize = false)
        public String kind;

        @JSONField(serialize = false)
        public String text;

        @JSONField(serialize = false)
        public double bonusY;

        public boolean collected;

        public double planeY;

        /**
         * NUMBER：吃到数字奖励，例如 +5
         * MULTIPLIER：吃到倍数奖励，例如 x3
         * DECREASE：吃到扣减奖励，例如 half / rocket
         */
        public String eatType;

        /**
         * NUMBER 时是加的数字，例如 5
         * MULTIPLIER 时是倍数，例如 3
         * DECREASE 时是扣减比例，例如 0.5
         */
        public double eatValue;

        /**
         * 给前端直接展示用，例如 +5、x3、half、rocket
         */
        public String eatDisplay;

        /**
         * 吃到前，按照当前倍率和 bet 计算出来的分数
         */
        public long beforeScore;

        /**
         * 吃到后，按照当前倍率和 bet 计算出来的分数
         */
        public long afterScore;

        /**
         * afterScore - beforeScore
         */
        public long scoreChange;

        public double beforeMultiplier;

        public double afterMultiplier;

    }

    /**
     * 核心方法：
     * 只指定 win 类型，不指定精确 win。
     */
    public static RoundProcessResult findByMode(
            long bet,
            WinMode mode,
            FindOptions options
    ) {
        if (bet <= 0) {
            throw new IllegalArgumentException("bet 必须大于 0");
        }

        if (options == null) {
            options = new FindOptions();
        }

        if (mode == WinMode.BIG && options.maxMultiplier <= 1) {
            throw new IllegalArgumentException("BIG 模式下 maxMultiplier 必须大于 1");
        }

        int startSeed = options.randomStartSeed
                ? generateSeed()
                : 1;

        for (int i = 0; i < options.maxTryCount; i++) {
            int seed = normalizeSeed(startSeed + i);

            // 搜索阶段：不记录过程，只判断 win 是否符合范围
            AviamastersMath.Result fastResult =
                    AviamastersMath.play(seed, bet, false);

            if (match(
                    fastResult,
                    bet,
                    mode,
                    options.maxMultiplier,
                    options.maxCollectedEventCount
            )) {
                AviamastersMath.Result detailResult =
                        AviamastersMath.play(seed, bet, true);

                return toProcessResult(
                        detailResult,
                        mode,
                        options.maxMultiplier,
                        i + 1
                );
            }
        }

        RoundProcessResult fail = new RoundProcessResult();
        fail.success = false;
        fail.mode = mode;
        fail.bet = bet;
        fail.tryCount = options.maxTryCount;
        fail.maxMultiplier = options.maxMultiplier;
        fail.message = "在最大尝试次数内没有找到符合条件的 seed";

        return fail;
    }

    private static boolean match(
            AviamastersMath.Result result,
            long bet,
            WinMode mode,
            double maxMultiplier,
            int maxCollectedEventCount
    ) {
        // 只限制吃到的事件数量
        if (result.collectedEventCount > maxCollectedEventCount) {
            return false;
        }

        long win = result.win;

        switch (mode) {
            case ZERO:
                return win == 0;

            case SMALL_NOT_ZERO:
                return win > 0 && win < bet;

            case BIG:
                double maxWinDouble = Math.floor(bet * maxMultiplier);
                return win > bet && win <= maxWinDouble;

            default:
                return false;
        }
    }

    private static RoundProcessResult toProcessResult(
            AviamastersMath.Result result,
            WinMode mode,
            double maxMultiplier,
            int tryCount
    ) {
        RoundProcessResult dto = new RoundProcessResult();

        dto.success = true;
        dto.mode = mode;
        dto.seed = result.seed;
        dto.bet = result.bet;
        dto.win = result.win;
        dto.landed = result.landed;
        dto.multiplier = round4(result.multiplier);
        dto.frames = result.frames;
        dto.collectedEventCount = result.collectedEventCount;
        dto.tryCount = tryCount;
        dto.maxMultiplier = maxMultiplier;
        dto.message = "找到符合条件的结果";

        if (result.events != null) {
            for (AviamastersMath.FlyEvent event : result.events) {
                EventDTO e = new EventDTO();

                e.frame = event.frame;
                e.bonusId = event.bonusId;
                e.kind = event.kind;
                e.collected = event.collected;
                e.bonusY = round2(event.bonusY);
                e.planeY = round2(event.planeY);
                e.eatType = event.eatType;
                e.eatValue = round4(event.eatValue);
                e.eatDisplay = event.kind;
                e.beforeMultiplier = round4(event.beforeMultiplier);
                e.afterMultiplier = round4(event.afterMultiplier);
                e.beforeScore = AviamastersMath.calcWin(event.beforeMultiplier, result.bet);
                e.afterScore = AviamastersMath.calcWin(event.afterMultiplier, result.bet);
                e.scoreChange = e.afterScore - e.beforeScore;
                e.text = buildText(e);

                dto.events.add(e);
            }
        }

        return dto;
    }

    private static String buildText(EventDTO e) {
        return "frame " + e.frame
                + " : 吃到" + getEatTypeName(e.eatType)
                + " " + e.eatDisplay
                + "，分数 " + e.beforeScore
                + " -> " + e.afterScore
                + "（" + formatScoreChange(e.scoreChange) + "）"
                + "，倍率 " + e.beforeMultiplier
                + " -> " + e.afterMultiplier;
    }

    private static String getEatTypeName(String eatType) {
        if ("NUMBER".equals(eatType)) {
            return "数字";
        }

        if ("MULTIPLIER".equals(eatType)) {
            return "倍数";
        }

        if ("DECREASE".equals(eatType)) {
            return "扣减";
        }

        return "奖励";
    }

    private static String formatScoreChange(long scoreChange) {
        if (scoreChange > 0) {
            return "+" + scoreChange;
        }

        return String.valueOf(scoreChange);
    }

    private static int generateSeed() {
        return SECURE_RANDOM.nextInt(Integer.MAX_VALUE - 1) + 1;
    }

    private static int normalizeSeed(int seed) {
        if (seed <= 0) {
            seed = Math.abs(seed);
        }

        if (seed == 0) {
            seed = 1;
        }

        if (seed >= Integer.MAX_VALUE) {
            seed = seed % (Integer.MAX_VALUE - 1) + 1;
        }

        return seed;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

//    public static void main(String[] args) {
//        long start = new Date().getTime();
//        RoundProcessResult byMode = findByMode(50, WinMode.BIG, null);
//        System.out.println(JSONObject.toJSONString(byMode));
//        System.out.println(byMode.getEvents().size());
//        System.out.println(byMode.seed);
//        System.out.println(new Date().getTime() - start);
//    }
}
