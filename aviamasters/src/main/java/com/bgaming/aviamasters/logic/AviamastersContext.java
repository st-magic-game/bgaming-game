package com.bgaming.aviamasters.logic;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.aviamasters.entity.client.ApiClientResult;
import com.bgaming.aviamasters.entity.client.Balance;
import com.bgaming.aviamasters.entity.client.Flow;
import com.bgaming.aviamasters.entity.client.Outcome;
import com.bgaming.aviamasters.entity.log.RoundDetailDto;
import com.bgaming.aviamasters.utils.DateTimeUtil;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.RandomUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.bgaming.aviamasters.logic.AviamastersRoundModeFinder.findByMode;

public class AviamastersContext {

    public static final int SUB_UNITS = 100;

    public static AviamastersRoundModeFinder.RoundProcessResult roundProcessResult(long stake, double factor,String pOrder) {

        AviamastersRoundModeFinder.WinMode winMode = AviamastersRoundModeFinder.WinMode.ZERO;
        AviamastersRoundModeFinder.FindOptions options = new AviamastersRoundModeFinder.FindOptions();

        if (factor > 1.2) {
            factor = 1.2;
            options.setMaxMultiplier(100);
        }
        if (factor < 0.85) {
            factor *= 1.15;
        }
        double random = RandomUtil.nextDouble();
        if (random <= 0.085 * Math.pow(factor,3)) {
            winMode = AviamastersRoundModeFinder.WinMode.BIG;
        } else if (random <= 0.4 * Math.pow(factor,1)) {
            winMode = AviamastersRoundModeFinder.WinMode.SMALL_NOT_ZERO;
        }
        AviamastersRoundModeFinder.RoundProcessResult byMode = findByMode(stake, winMode, options);
        byMode.setPOrder(pOrder);
        return byMode;
    }


    public static ApiClientResult generateResult(Player player,AviamastersRoundModeFinder.RoundProcessResult roundProcessResult) {
        String pOrder = roundProcessResult.getPOrder();
        ApiClientResult apiClientResult = new ApiClientResult();
        Flow flow = new Flow();
        flow.setLast_action_id(pOrder + "_" + 1);
        flow.setRound_id(pOrder);
        apiClientResult.setFlow(flow);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("seed",roundProcessResult.getSeed());
        Outcome outcome = new Outcome();
        outcome.setBet(DecimalUtil.getBigDecimal2(roundProcessResult.getBet()))
                .setWin(DecimalUtil.getBigDecimal2(roundProcessResult.getWin()))
                .setStorage(jsonObject);
        apiClientResult.setOutcome(outcome);
        Balance balance = new Balance(outcome.getWin(),DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS - outcome.getBet().doubleValue()));
        apiClientResult.setBalance(balance);
        return apiClientResult;
    }

    public static RoundDetailDto generateRoundDetail(Player player,AviamastersRoundModeFinder.RoundProcessResult roundProcessResult) {


        double totalWin = roundProcessResult.getWin();
        BigDecimal realBet = DecimalUtil.getBigDecimal2((double) roundProcessResult.getBet() / SUB_UNITS);
        String usedFeature = "No";
        String betTextBuy = realBet.toPlainString();
        RoundDetailDto roundDetailDto = new RoundDetailDto();

                roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));

        BigDecimal realWin = DecimalUtil.getBigDecimal2(totalWin/ SUB_UNITS);

        double beforeScore = player.getUser().getScore() + realBet.doubleValue() - realWin.doubleValue();

                BigDecimal realProfit = DecimalUtil.getBigDecimal2(totalWin / SUB_UNITS - realBet.doubleValue());
        roundDetailDto.setBetText(realBet.toPlainString());
        roundDetailDto.setBetTextBuy(betTextBuy);

        roundDetailDto.setUsedFeature(usedFeature);
        roundDetailDto.setStake(realBet.toPlainString());
        roundDetailDto.setBet(realBet);
        roundDetailDto.setTotalWinText(realWin.toPlainString());
        roundDetailDto.setTotalWin(realWin);
        roundDetailDto.setProfitText(realProfit.toPlainString());
        roundDetailDto.setProfit(realProfit);
        roundDetailDto.setCurrency(player.getCoinsType());
        roundDetailDto.setBalanceBeforeText(DecimalUtil.getBigDecimal2(beforeScore).toPlainString());
        roundDetailDto.setBalanceBefore(DecimalUtil.getBigDecimal2(beforeScore));
        roundDetailDto.setBalanceAfterText(DecimalUtil.getBigDecimal2(player.getUser().getScore()).toPlainString());
        roundDetailDto.setBalanceAfter(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
        roundDetailDto.setRoundProcessResult(roundProcessResult);
        return roundDetailDto;
    }

}
