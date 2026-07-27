package com.bgaming.burningchillix.logic;


import com.alibaba.fastjson.JSONObject;

import com.bgaming.burningchillix.entity.Symbol;
import com.bgaming.burningchillix.entity.client.*;
import com.bgaming.burningchillix.entity.log.RoundDetailDto;
import com.bgaming.burningchillix.service.SlotGameService;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Component
public class BurningChilliXContext {

    private static SlotGameService service;

    public static final int SUB_UNITS = 100;

    public BurningChilliXContext(SlotGameService service) {
        BurningChilliXContext.service = service;
    }


    public static ApiClientResult generateApiResult(Player player, double stake, double v,int mode, String pOrder, double realStake) {
        int round_num = 1;
        ApiClientResult clientResult = new ApiClientResult();

        Outcome outcome = service.spin(v,BigDecimal.valueOf(stake),mode);
        clientResult.setOutcome(outcome);
        Flow flow = new Flow();
        clientResult.setFlow(flow);

        flow.setRound_id(pOrder).setLast_action_id(pOrder + "_" + round_num);
        clientResult.setBalance(new Balance(DecimalUtil.getBigDecimal2(clientResult.getOutcome().getWin().doubleValue()),DecimalUtil.getBigDecimal2((player.getUser().getScore()  - realStake) * BurningChilliXContext.SUB_UNITS)));
        return clientResult;
    }

    public static RoundDetailDto generateRoundDetail(ApiClientResult client, double beforeScore, Player player, double betScore) {

        double totalWin = client.getOutcome().getWin().doubleValue();
        BigDecimal realBet = DecimalUtil.getBigDecimal2(betScore);
        String usedFeature = "No";
        String betTextBuy = realBet.toPlainString();


        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(TimeUtil.getLocaleString() + " UTC+00:00");

        BigDecimal realWin = DecimalUtil.getBigDecimal2(totalWin / SUB_UNITS);
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(totalWin / SUB_UNITS - betScore);
        roundDetailDto.setBetText(realBet.toPlainString());
        roundDetailDto.setBetTextBuy(betTextBuy);

        roundDetailDto.setUsedFeature(usedFeature);
        roundDetailDto.setStake(DecimalUtil.getBigDecimal2(betScore / SUB_UNITS).toPlainString());
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
        roundDetailDto.setClientResult(client);
        return roundDetailDto;
    }



    public String getSymbolName(int index) {
        Optional<Symbol> first = service.symbolConfig.getSymbols().stream().filter(s -> s.getIndex() == index).findFirst();
        return first.map(Symbol::getName).orElse("");
    }

    public int getLine(List<Object> win) {
        List<Integer> o = (List<Integer>) win.get(2);
        return o.size();
    }

    public String lineSymbol(ApiClientResult clientResult,List<Object> win) {
        List<List<String>> screen = clientResult.getOutcome().getScreen();
        List<Integer> o = (List<Integer>) win.get(2);
        for (int i = 0; i < o.size(); i++) {
            if (!Objects.equals(screen.get(i).get(o.get(i)), "0")) {
                return getSymbolName(Integer.parseInt(screen.get(i).get(o.get(i))));
            }
        }
        return getSymbolName(0);
    }

    public String getLineWin(List<Object> win) {
        BigDecimal o = (BigDecimal) win.get(1);
        return DecimalUtil.getBigDecimal2(o.doubleValue() / SUB_UNITS).toPlainString();
    }

    public boolean showLine(List<List<Object>> wins) {
        return wins.stream().anyMatch(w -> w.get(0).equals("line"));
    }

    public boolean showScatter(List<List<Object>> wins) {
        return wins.stream().anyMatch(w -> w.get(0).equals("scatter"));
    }

    public String getScatterWin(List<List<Object>> wins) {
        Optional<List<Object>> scatter = wins.stream().filter(w -> w.get(0).equals("scatter")).findFirst();
        if (scatter.isPresent()) {
            List<Object> list = scatter.get();
            return getLineWin(list);
        }
        return "0";
    }
    public List<List<Object>> getLineObj(List<List<Object>> wins) {
        return wins.stream().filter(w -> w.get(0).equals("line")).collect(Collectors.toList());
    }













}
