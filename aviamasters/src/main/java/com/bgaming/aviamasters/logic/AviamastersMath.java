package com.bgaming.aviamasters.logic;

import java.util.*;

public class AviamastersMath {

    static final double LS = 8192.0;              // 2^13
    static final double WIN_LIMIT_RAW = 250 * LS; // 最大 250x
    static final double ROUND_EPS = 1e-4;

    static final double MIN_Y_SPEED = 60;
    static final double MAX_UP_SPEED = 110;
    static final double SPEED_ADD = 2;
    static final double SHIP_HALF_RANGE = 1350;
    static final double SHIP_RESET_LEFT = 2500;
    static final double SHIP_RESET_X = 4050;
    static final double ROCKET_Y_ADD = 20;

    public static class Result {
        public int seed;
        public long bet;
        public boolean landed;
        public double multiplier;
        public long win;
        public int frames;
        public List<FlyEvent> events = new ArrayList<>();
        /**
         * 这局真实模拟过程中，总共吃到了多少个 event
         */
        public int collectedEventCount;
    }

    public static class FlyEvent {
        public int frame;
        public int bonusId;
        public String kind;          // +1、+5、x3、rocket、half

        /**
         * NUMBER：吃到 +1 / +2 / +5 / +10 这种数字奖励
         * MULTIPLIER：吃到 x2 / x3 / x4 / x5 这种倍数奖励
         * DECREASE：吃到 half / rocket 这种扣减奖励
         */
        public String eatType;

        /**
         * NUMBER 时表示加的数字，例如 5
         * MULTIPLIER 时表示乘的倍数，例如 3
         * DECREASE 时表示扣减比例，例如 0.5
         */
        public double eatValue;

        public boolean collected;    // 是否吃到
        public double bonusY;
        public double planeY;
        public double beforeMultiplier;
        public double afterMultiplier;

        @Override
        public String toString() {
            return "FlyEvent{" +
                    "frame=" + frame +
                    ", bonusId=" + bonusId +
                    ", kind='" + kind + '\'' +
                    ", eatType='" + eatType + '\'' +
                    ", eatValue=" + eatValue +
                    ", collected=" + collected +
                    ", bonusY=" + bonusY +
                    ", planeY=" + planeY +
                    ", beforeMultiplier=" + beforeMultiplier +
                    ", afterMultiplier=" + afterMultiplier +
                    '}';
        }
    }

    /**
     * 模拟 JS 里的随机数。
     * 注意：不能直接用 Java Random，否则和客户端对不上。
     */
    static class JsRng {
        double state0;
        double state1;
        double state2;

        void seed(int seed) {
            state0 = seed;
            state1 = seed * 213947.0 + 1238971.0;
            state2 = seed * 7431.0 + 94823.0;

            // 客户端 seed() 里会先丢弃一次 random()
            random(Double.MAX_VALUE);
        }

        double random(double mod) {
            double tRaw = state0;
            double sRaw = state1;

            state0 = sRaw;

            int t = toInt32(tRaw);
            int s = toInt32(sRaw);

            t ^= (t << 23);
            t ^= (t >> 17);
            t ^= s;
            t ^= (s >> 26);

            state1 = t;
            state2 = (1103515245.0 * state2 + 12345.0) % 2147483648.0;

            return (state0 + state1 + state2) % mod;
        }

        /**
         * 模拟 JS ToInt32。
         * 这里很重要，不能直接 (int) value。
         */
        static int toInt32(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value == 0) {
                return 0;
            }

            double posInt = Math.copySign(Math.floor(Math.abs(value)), value);
            double int32Bit = posInt % 4294967296.0;

            if (int32Bit < 0) {
                int32Bit += 4294967296.0;
            }

            if (int32Bit >= 2147483648.0) {
                return (int) (int32Bit - 4294967296.0);
            }

            return (int) int32Bit;
        }
    }

    static class FlightModel {
        double win = LS;
        double playerY = 0;
        double xSpeed = 0;
        double ySpeed = 0;
        double shipX = 0;
        double distance = 0;

        boolean isFinished = true;
        boolean landed = false;
        /**
         * 只统计吃到的事件数量
         */
        int collectedEventCount = 0;

        JsRng random = new JsRng();
        List<Bonus> bonuses = new ArrayList<>();

        FlightModel() {
            bonuses.add(new Bonus(0, this, random, -40, 1, 1, 1));
            bonuses.add(new Bonus(1, this, random, -40, 1, 1, 2));
            bonuses.add(new Bonus(2, this, random, -40, 1, 1, 3));
            bonuses.add(new Bonus(3, this, random, -40, 2, 1, 4));
            bonuses.add(new Bonus(4, this, random, -40, 2, 1, 5));
            bonuses.add(new Bonus(5, this, random, -40, 5, 1, 6));
            bonuses.add(new Bonus(6, this, random, -40, 10, 1, 7));
            bonuses.add(new Bonus(7, this, random, -40, 0, 2, 8));
            bonuses.add(new Bonus(8, this, random, -40, 0, 3, 9));
            bonuses.add(new Bonus(9, this, random, -40, 0, 4, 10));
            bonuses.add(new Bonus(10, this, random, -40, 0, 5, 11));
            bonuses.add(new Bonus(11, this, random, ROCKET_Y_ADD, 0, 0.5, 0));
        }

        void seed(int seed) {
            distance = 0;
            isFinished = false;
            win = LS;
            landed = false;
            ySpeed = -78;
            xSpeed = -80;
            playerY = 0;
            shipX = 0;
            collectedEventCount = 0;

            random.seed(seed);

            for (Bonus b : bonuses) {
                b.y = -1_000_000;
            }

            for (Bonus b : bonuses) {
                b.newRound();
            }
        }

        void update(
                int frame,
                List<FlyEvent> events,
                boolean collectEvents
        ) {
            shipX += xSpeed;
            distance -= xSpeed;

            if (shipX < -SHIP_RESET_LEFT) {
                shipX = SHIP_RESET_X;
            }

            if (landed) {
                if (shipX > -SHIP_HALF_RANGE) {
                    if (xSpeed != 0) {
                        xSpeed += SPEED_ADD;
                    }

                    if (xSpeed == 0) {
                        isFinished = true;
                    }
                } else {
                    landed = false;
                    isFinished = true;
                }
            } else {
                if (ySpeed < MIN_Y_SPEED) {
                    ySpeed++;
                }

                playerY += ySpeed;

                if (playerY >= 0) {
                    if (shipX > -SHIP_HALF_RANGE && shipX < SHIP_HALF_RANGE) {
                        landed = true;
                        ySpeed = 0;
                        playerY = 0;
                    } else {
                        isFinished = true;
                    }

                    if (xSpeed != 0) {
                        xSpeed += SPEED_ADD;
                    }
                }
            }

            for (Bonus b : bonuses) {
                b.update(frame, events, collectEvents);
            }
        }

        double getTotalWinMultiplier() {
            return landed ? win / LS : 0;
        }
    }

    static class Bonus {
        final int id;
        final FlightModel game;
        final JsRng random;

        final double add;
        final double multiply;
        final double rarity;
        final double yAdd;
        final double speed;
        final int reSpawnsToTurnRocket;

        double x;
        double y;

        int currentRespawn = 0;
        boolean isRocket = false;

        Bonus(
                int id,
                FlightModel game,
                JsRng random,
                double yAdd,
                double add,
                double multiply,
                int reSpawnsToTurnRocket
        ) {
            this.id = id;
            this.game = game;
            this.random = random;
            this.yAdd = yAdd;
            this.add = add;
            this.multiply = multiply;
            this.reSpawnsToTurnRocket = reSpawnsToTurnRocket;

            this.rarity = 2000 + add * 400 + multiply * 2000;
            this.speed = 0;
        }

        void newRound() {
            isRocket = false;
            currentRespawn = 0;
            respawn();
        }

        void respawn() {
            x = random.random(rarity) + 4000;
            y = -random.random(4000) - 700;

            while (hasNearBonus()) {
                y -= 200;
            }
        }

        boolean hasNearBonus() {
            for (Bonus b : game.bonuses) {
                if (b != this
                        && Math.abs(b.x - x) < 300
                        && Math.abs(b.y - y) < 450) {
                    return true;
                }
            }
            return false;
        }

        String kind() {
            if (isRocket) {
                return "rocket";
            }

            if (multiply < 1) {
                return "half";
            }

            if (multiply > 1) {
                return "x" + (int) multiply;
            }

            return "+" + (int) add;
        }

        String eatType() {
            if (isRocket || multiply < 1) {
                return "DECREASE";
            }

            if (multiply > 1) {
                return "MULTIPLIER";
            }

            return "NUMBER";
        }

        double eatValue() {
            if (isRocket || multiply < 1) {
                return 0.5;
            }

            if (multiply > 1) {
                return multiply;
            }

            return add;
        }

        void update(
                int frame,
                List<FlyEvent> events,
                boolean collectEvents
        ) {
            x += game.xSpeed + speed;

            if (x <= 0) {
                boolean collected = Math.abs(game.playerY - y) <= 220;
                double beforeWin = game.win;

                if (collected) {
                    if (isRocket || multiply < 1) {
                        double reduce = Math.max(1, Math.floor(game.win * 0.5));
                        game.win -= reduce;

                        if (game.win < 0) {
                            game.win = 0;
                        }
                    } else {
                        game.win += add * LS;
                        game.win *= multiply;
                    }

                    game.win = Math.min(WIN_LIMIT_RAW, game.win);

                    double t = Math.max(
                            -game.ySpeed + 20,
                            Math.floor((6000 + game.playerY + 0.5) / 64)
                    );

                    double s = isRocket ? ROCKET_Y_ADD : yAdd;

                    game.ySpeed = Math.max(
                            Math.max(-t, -MAX_UP_SPEED),
                            game.ySpeed + s
                    );

                    if (s < 0) {
                        game.ySpeed = Math.min(s, game.ySpeed);
                    }

                    // 只有吃到才计数
                    game.collectedEventCount++;

                    // 只有吃到才加入 events
                    if (collectEvents && events != null) {
                        FlyEvent event = new FlyEvent();
                        event.frame = frame;
                        event.bonusId = id;
                        event.kind = kind();
                        event.eatType = eatType();
                        event.eatValue = eatValue();
                        event.collected = true;
                        event.bonusY = y;
                        event.planeY = game.playerY;
                        event.beforeMultiplier = beforeWin / LS;
                        event.afterMultiplier = game.win / LS;

                        events.add(event);
                    }
                }

                currentRespawn++;

                if (currentRespawn == reSpawnsToTurnRocket) {
                    isRocket = true;
                }

                respawn();
            }
        }
    }

    public static Result play(int seed, long bet) {
        return play(seed, bet, true);
    }

    public static Result play(int seed, long bet, boolean collectEvents) {
        FlightModel model = new FlightModel();

        List<FlyEvent> events = collectEvents
                ? new ArrayList<>()
                : null;

        model.seed(seed);

        int frame = 0;

        while (!model.isFinished) {
            model.update(frame, events, collectEvents);
            frame++;

            if (frame > 100_000) {
                throw new IllegalStateException("simulation overflow");
            }
        }

        double multiplier = model.getTotalWinMultiplier();
        long win = calcWin(multiplier, bet);

        Result result = new Result();
        result.seed = seed;
        result.bet = bet;
        result.landed = model.landed;
        result.multiplier = multiplier;
        result.win = win;
        result.frames = frame;
        result.collectedEventCount = model.collectedEventCount;

        if (collectEvents) {
            result.events = events;
        }

        return result;
    }

    static long calcWin(double multiplier, long bet) {
        if (multiplier >= 0.95) {
            return (long) Math.ceil(multiplier * bet - ROUND_EPS);
        } else {
            return (long) Math.floor(multiplier * bet + ROUND_EPS);
        }
    }

//    public static void main(String[] args) {
//        Result result = play(1145263873, 8192, false);
//
//        System.out.println("seed = " + result.seed);
//        System.out.println("landed = " + result.landed);
//        System.out.println("multiplier = " + result.multiplier);
//        System.out.println("win = " + result.win);
//        System.out.println("frames = " + result.frames);
//
//        System.out.println("前 20 个飞行事件：");
//        result.events.stream().limit(20).forEach(System.out::println);
//    }
}
