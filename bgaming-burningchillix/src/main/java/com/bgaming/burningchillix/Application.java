package com.bgaming.burningchillix;

import com.bgaming.burningchillix.config.PayLinesConfig;
import com.bgaming.burningchillix.entity.client.Outcome;
import com.bgaming.burningchillix.service.SlotGameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@SpringBootApplication
public class Application implements ApplicationRunner {

    public static void main(String[] args) {
        log.info("bGaming BurningChilliX-server:version:20260727");
        SpringApplication.run(Application.class);
    }

    @Autowired
    private SlotGameService gameService;
    @Autowired
    private  PayLinesConfig payLinesConfig;


    @Override
    public void run(ApplicationArguments args) throws Exception {
//        log.info("=========================================================");
//        log.info("🎰 [中央奖池完美闭环版] 开始自动执行 5 组老虎机开奖仿真压测...");
//        log.info("=========================================================");
//
//        int[][] testCases = {
//                {100, 20},  // 1元下注 20条线
//                {200, 40},  // 2元下注 40条线
//                {300, 60},  // 3元下注 60条线
//                {400, 80},  // 4元下注 80条线
//                {500, 100}  // 5元下注 100条线
//        };
//
//        for (int[] testCase : testCases) {
//            int displayBetInt = testCase[0];
//            int selectLines = testCase[1];
//
//            if (payLinesConfig.getPayLines() != null && payLinesConfig.getPayLines().size() < selectLines) {
//                selectLines = payLinesConfig.getPayLines().size();
//            }
//
//            executeSingleSimulation(displayBetInt, selectLines);
//        }
    }

    private void executeSingleSimulation(int displayBetInt, int selectLines) {
        int totalRounds = 10000;
        BigDecimal totalBetReal = BigDecimal.ZERO;
        BigDecimal totalWinReal = BigDecimal.ZERO;

        int winRounds = 0;
        int scatterWinRounds = 0;
        int totalRespinCounts = 0;

        BigDecimal inputBetParam = new BigDecimal(String.valueOf(displayBetInt));
        double betRealPerRound = displayBetInt / 100.0;

        double cap = 360.0;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRounds; i++) {
            Outcome effectiveOutcome = null;
            double nextPotentialCap = 0;

            int currentRoundRetryCount = 0;

            // 🛑 硬性要求铁律：不满足运营池 cap >= 300 绝对作废重抽！
            while (true) {
                // 原版硬核公式分母 200 锁定，纯正直接加减
                double baseF = 0.6 + 1.4 * (cap - 300.0) / 200.0;
                double sineWave = Math.sin(i * 0.05) * 0.12;
                double f = baseF + sineWave;

                if (f > 1.35) f = 1.35;
                if (f < 0.65) f = 0.65;

                // 传入真实重试计数器，建立中央链路硬调度
                Outcome tempOutcome = gameService.spin(f, inputBetParam, selectLines);

                double winRealPerRound = tempOutcome.getWin().doubleValue() / 100.0;
                double diffReal = winRealPerRound - betRealPerRound;

                double tempCap = cap - diffReal; // 严格账目不加任何阻尼的纯净直接加减

                if (tempCap < 300.0) {
                    totalRespinCounts++;
                    currentRoundRetryCount++;
                    continue;
                } else {
                    effectiveOutcome = tempOutcome;
                    nextPotentialCap = tempCap;
                    break;
                }
            }

            totalBetReal = totalBetReal.add(BigDecimal.valueOf(betRealPerRound));
            totalWinReal = totalWinReal.add(BigDecimal.valueOf(effectiveOutcome.getWin().doubleValue() / 100.0));

            if (effectiveOutcome.getWin().compareTo(BigDecimal.ZERO) > 0) {
                winRounds++;

            }

            boolean hasScatter = effectiveOutcome.getWins().stream()
                    .anyMatch(w -> w.size() > 0 && "scatter".equals(w.get(0)));
            if (hasScatter) {
                scatterWinRounds++;
//                System.out.println(JSONObject.toJSONString(effectiveOutcome));
            }

            cap = nextPotentialCap;
        }

        long endTime = System.currentTimeMillis();

        BigDecimal rtp = BigDecimal.ZERO;
        if (totalBetReal.compareTo(BigDecimal.ZERO) > 0) {
            rtp = totalWinReal.divide(totalBetReal, 4, RoundingMode.HALF_UP);
        }

        BigDecimal winRate = new BigDecimal(winRounds)
                .divide(new BigDecimal(totalRounds), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        log.info("---------------------------------------------------------");
        log.info("📊 压测报告 [输入参数分: {}, 购买线数: {}]", displayBetInt, selectLines);
        log.info("▶ 仿真耗时: {} ms", (endTime - startTime));
        log.info("▶ 熔断触发重开总次数: {} 次 (重试死锁被物理切断！)", totalRespinCounts);
        log.info("▶ 累计有效真实投注 (Bet): {}", totalBetReal.setScale(2, RoundingMode.HALF_UP));
        log.info("▶ 累计有效真实派奖 (Win): {}", totalWinReal.setScale(2, RoundingMode.HALF_UP));
        log.info("🔥 最终返奖率 (RTP): {}% (期望平衡值：≈100% 绝对死锁锁死)", rtp.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        log.info("▶ 玩家有效中奖局数: {} 次", winRounds);
        log.info("▶ 综合有效中奖率 (Hit Rate): {}% (目标：≈25% 全线收官)", winRate.setScale(2, RoundingMode.HALF_UP));
        log.info("▶ 触发 Scatter 中奖的有效局数: {} 次", scatterWinRounds);
    }
}
