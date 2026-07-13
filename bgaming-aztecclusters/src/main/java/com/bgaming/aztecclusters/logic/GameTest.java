package com.bgaming.aztecclusters.logic;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.aztecclusters.entity.Scene;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;

import java.util.*;

import static com.bgaming.aztecclusters.config.LotteryConfig.*;

public class GameTest {

    public static void main(String[] args) {
        double factor = 1.006821056d;
        for (int i = 0; i < 10; i++) {
            testPro(factor);
//            factor -= 0.05;
        }
    }

    private static void testPro(double factor) {
        double betScore = 1;
        int betCount = 10000;
        int bingoFreeCount = 0;
        int winCount = 0;
        int finalWinCount = 0;
        int betType = 0;
        int mul = BET_TYPE_MUL[betType];
        double totalBet = betCount * betScore * mul;
        double totalWin = 0d;
        double normalWin = 0d;
        double freeWin = 0d;
        int multiCount = 0;
        int destroyCount = 0;
        Map<Integer, Integer> wildSize = new TreeMap<>();
        int mul5 = 0, mul10 = 0, mul20 = 0, mul50 = 0, mul100 = 0, mul200 = 0, mul500 = 0;

        Player player = new Player();
        User account = new User();
        account.setScore(100000);
        account.setNickname("Asd丶Zzz");
        player.setUser(account);
        player.getExtendJson().put(REQUEST_TYPE, betType);
        GameTable table = new GameTable(null, null);
        long startTime = System.currentTimeMillis();
        Map<Integer, Integer> dropCount = new HashMap<>();
        Map<Integer, Integer> wsm = new HashMap<>();
        for (int i = 0; i < betCount; i++) {
            long now = TimeUtil.getNow();
            try {
                player.getExtendJson().remove("scene");
                JSONObject result;
                do {
                    result = table.codeResultData(player, betScore, factor);
                } while (result == null);

                List<Scene> scenes = getScenes(player);
                boolean hasFree = scenes.get(0).getOpenFreeNum() > 0;
//                SpinResponse response = table.generateResponse(scenes, true, player.getUser().getScore());
                double winTemp = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
                if (hasFree) {
                    freeWin += winTemp;
                    bingoFreeCount++;
                } else {
                    normalWin += winTemp;
                }

                for (Scene scene : scenes) {
                    int size = scene.getStorage().getSaved_screens().size() - 1;
                    if (scene.isDestroy()) {
                        destroyCount++;
                    }
                    if (scene.isMultiBooster()) {
                        multiCount++;
                    }
                    Integer count = dropCount.getOrDefault(size, 0);
                    dropCount.put(size, count + 1);
                    if (scene.getGold() > 50) {
//                        System.err.println(scene.getGold() + "_" + count);
                    }
                }
                if (scenes.get(0).getType() < 10) {
                    int[][] finalRotary = scenes.get(scenes.size() - 1).getFinalRotary();
                    int ws = getIconSize(finalRotary, WILD);
                    Integer orDefault = wsm.getOrDefault(ws, 0);
                    wsm.put(ws, orDefault + 1);
                }
                if (winTemp > 0) {
                    totalWin += winTemp;
                    winCount++;
                    if (winTemp > betScore * mul) {
                        finalWinCount++;
                    }
                    double v = winTemp / (betScore * mul);
                    if (v >= 500) {
                        mul500++;
                    } else if (v >= 200) {
                        mul200++;
                    } else if (v >= 100) {
                        mul100++;
                    } else if (v >= 50) {
                        mul50++;
                    } else if (v >= 20) {
                        mul20++;
                    } else if (v >= 10) {
                        mul10++;
                    } else if (v >= 5) {
                        mul5++;
                    }
                }
//                System.out.println(TimeUtil.getNow() - now);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        long endTime = System.currentTimeMillis();
        System.out.println("################### [[[ factor = " + factor + " ]]] ################## 耗时: " + (endTime - startTime));
        System.out.println("总场次: " + betCount + ", 中奖场次: " + winCount + ", 中奖概率: " + (winCount * 1.d / betCount) + ", 赢钱场次: " + finalWinCount + " , 赢钱概率: " + (finalWinCount * 1.d / betCount));
        System.out.println("总场次: " + betCount + ", 中免费次数: " + bingoFreeCount + ", 中免费概率: " + (bingoFreeCount * 1.d / betCount));
        System.out.println("总投注: " + totalBet + ", 总赢出: " + totalWin + ", 中奖概率: " + (totalWin * 1.d / totalBet) + ", 普通赢出: " + normalWin + " , 普通赢出占比: " + (normalWin * 1.d / totalWin));
        System.out.println("总投注: " + totalBet + ", 总赢出: " + totalWin + ", 中奖概率: " + (totalWin * 1.d / totalBet) + ", 免费赢出: " + freeWin + " , 免费赢出占比: " + (freeWin * 1.d / totalWin));
        System.out.println("五倍场次: " + mul5 + " , 十倍场次: " + mul10 + " , 二十倍场次: " + mul20 + " , 五十倍场次: " + mul50 + " , 一百倍场次: " + mul100 + " , 两百倍场次: " + mul200 + " , 五百倍场次: " + mul500);
        System.out.println(dropCount);
        System.out.println("destroyL " + destroyCount + ", multi:" + multiCount);
        System.out.println("wsm: " + wsm);
    }

    private static List<Scene> getScenes(Player player) {
        List<Scene> scenes = null;
        if (player.getExtendJson().containsKey("scene")) {
            scenes = (List<Scene>) player.getExtendJson().get("scene");
        }
        return scenes;
    }

    private static int getIconSize(int[][] rotary, int icon) {
        List<Integer> iconIndexes = getIconIndexes(rotary, icon);
        return iconIndexes.size();
    }

    private static List<Integer> getIconIndexes(int[][] rotary, int icon) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == icon) {
                    indexes.add(i * COLUMNS + i1);
                }
            }
        }
        return indexes;
    }
}
