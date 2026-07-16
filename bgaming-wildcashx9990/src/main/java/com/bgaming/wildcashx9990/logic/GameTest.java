package com.bgaming.wildcashx9990.logic;

import com.bgaming.wildcashx9990.entity.Scene;
import com.bgaming.wildcashx9990.entity.dto.SpinResponse;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;

import java.util.List;

public class GameTest {

    public static void main(String[] args) {
        double factor = 1.00d;
        for (int i = 0; i < 10; i++) {
            testPro(factor);
            factor += 0.05;
        }
    }

    private static void testPro(double factor) {
        double betScore = 1;
        int betCount = 100000;
        int bingoFreeCount = 0;
        int winCount = 0;
        int finalWinCount = 0;
        int mul = 1;
        double totalBet = betCount * betScore * mul;
        double totalWin = 0d;
        double normalWin = 0d;
        double freeWin = 0d;

        int mul5 = 0, mul10 = 0, mul20 = 0, mul50 = 0, mul100 = 0, mul200 = 0, mul500 = 0;

        Player player = new Player();
        User account = new User();
        account.setScore(100000);
        account.setNickname("Asd丶Zzz");
        player.setUser(account);
//        player.getExtendJson().put("betType",1);
        GameTable table = new GameTable(null, null);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < betCount; i++) {
            try {
                table.codeResultData(player, betScore, factor);
                List<Scene> scenes = getScenes(player);

//                SpinResponse response = table.generateResponse(scenes,   player.getUser().getScore());
                boolean hasFree = false;
                for (int i1 = 0; i1 < scenes.size(); i1++) {
                    Scene scene = scenes.get(i1);
                    if (scene.getType() == 1) {
                        hasFree = true;
                    }

                }

                double winTemp = table.getWinGold();
                if (hasFree) {
                    freeWin += winTemp;
                    bingoFreeCount++;
                } else {
                    normalWin += winTemp;
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
    }

    private static List<Scene> getScenes(Player player) {
        List<Scene> scenes = null;
        if (player.getExtendJson().containsKey("scene")) {
            scenes = (List<Scene>) player.getExtendJson().get("scene");
        }
        return scenes;
    }

}
