package com.bgaming.pandaluck.logic;

import com.bgaming.pandaluck.entity.dto.SpinResponse;
import com.bgaming.pandaluck.entity.Scene;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;

import java.util.List;

import static com.bgaming.pandaluck.config.LotteryConfig.*;

public class GameTest {

    public static void main(String[] args) {
        double factor = 1.006821056d;
        for (int i = 0; i < 10; i++) {
            testPro(factor);
            factor -= 0.05;
        }
    }

    private static void testPro(double factor) {
        double betScore = 1;
        int betCount = 100000;
        int bingoFreeCount = 0;
        int winCount = 0;
        int finalWinCount = 0;
        int mul = 60;
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
        player.getExtendJson().put("betType",1);
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
                int scatterSize  = 0;
                if (hasFree) {
                    Scene scene = scenes.get(scenes.size() - 1);
                    int[][] rotary = scene.getRotary();
                    for (int i1 = 0; i1 < ROWS; i1++) {
                        for (int i2 = 0; i2 < COLUMNS; i2++) {
                            if (rotary[i1][i2] == SCATTER) {
                                scatterSize++;
                            }
                        }
                    }
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

                    if (scatterSize >= 9) {
                        mul500++;
                    } else if (scatterSize >= 8) {
                        mul200++;
                    } else if (scatterSize >= 7) {
                        mul100++;
                    } else if (scatterSize >= 6) {
                        mul50++;
                    } else if (scatterSize >= 5) {
                        mul20++;
                    } else if (scatterSize >= 4) {
                        mul10++;
                    } else if (scatterSize >= 3) {
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
        System.out.println("3个scatter场次: " + mul5 + " , 4个scatter场次: " + mul10 + " , 5个scatter场次: " + mul20 + " , 6个scatter场次: " + mul50 + " , 7个scatter场次: " + mul100 + " , 8个scatter场次: " + mul200 + " , 9个scatter场次: " + mul500);
    }

    private static List<Scene> getScenes(Player player) {
        List<Scene> scenes = null;
        if (player.getExtendJson().containsKey("scene")) {
            scenes = (List<Scene>) player.getExtendJson().get("scene");
        }
        return scenes;
    }

}
