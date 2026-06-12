package com.bgaming.giftrush.logic;


import com.alibaba.fastjson.JSONObject;
import com.bgaming.giftrush.config.GlobalConfig;
import com.bgaming.giftrush.config.PayLinesConfig;
import com.bgaming.giftrush.config.SymbolConfig;
import com.bgaming.giftrush.entity.PayLines;
import com.bgaming.giftrush.entity.Symbol;
import com.bgaming.giftrush.entity.client.*;
import com.bgaming.giftrush.entity.log.RoundDetailDto;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.JsonUtil;
import com.game.base.common.util.RandomUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Component
public class GiftRushContext {

    private static SymbolConfig SYMBOL_CONFIG;

    private static PayLinesConfig PAY_LINE_CONFIG;

    public static GlobalConfig GLOBAL_CONFIG;

    private static List<Symbol> NORMAL_SYMBOLS;

    private static Symbol DIAMOND;

    public static final int SUB_UNITS = 100;


    public GiftRushContext(SymbolConfig config, PayLinesConfig payLinesConfig, GlobalConfig globalConfig) {
        SYMBOL_CONFIG = config;
        PAY_LINE_CONFIG = payLinesConfig;
        GLOBAL_CONFIG = globalConfig;
        initNormalSymbols();
        initDiamondSymbol();
    }

    private void initNormalSymbols() {
        List<Symbol> symbols = SYMBOL_CONFIG.getSymbols();
        NORMAL_SYMBOLS = symbols.stream().filter(s-> s.getType() == 0).collect(Collectors.toList());
    }

    private void initDiamondSymbol() {
        List<Symbol> symbols = SYMBOL_CONFIG.getSymbols();
        DIAMOND = symbols.stream().filter(s-> s.getType() == 1).findFirst().get();
    }



    private static Symbol randomNormalSymbol(int excludesSymbol) {
        double random = RandomUtil.nextDouble();
        for (Symbol symbol : NORMAL_SYMBOLS) {
            if(symbol.getIndex() == excludesSymbol) {
                continue;
            }
            if(random <= symbol.getWeight()) {
                return symbol;
            }
        }
        return NORMAL_SYMBOLS.get(RandomUtil.nextInt(NORMAL_SYMBOLS.size()));
    }


    private static List<PayLines> getPayLines(int index) {
        return PAY_LINE_CONFIG.getPayLines().stream().filter(p ->p.getPositions().contains(index)).collect(Collectors.toList());
    }

    private static Symbol getSymbol(int index) {
        Optional<Symbol> first = SYMBOL_CONFIG.getSymbols().stream().filter(s -> s.getIndex() == index).findFirst();
        return first.orElse(null);
    }

    private static List<WinLine> getWinLine(List<List<Integer>> reelTable, int index, double stake) {
        List<PayLines> payLines = getPayLines(index);
        List<WinLine> winLines = new ArrayList<>();
        payLines.forEach(p -> {
            List<Integer> positions = p.getPositions();
            int symbol1 = reelTable.get(1).get(positions.get(1));
            int symbol2 = reelTable.get(2).get(positions.get(2));
            if (symbol1 == symbol2 && symbol1 < DIAMOND.getIndex()) {
                Symbol symbol = getSymbol(symbol1);
                WinLine winLine = new WinLine();
                winLine.setSymbol(symbol1).setOdds((symbol.getMultiplier().multiply(BigDecimal.valueOf(5))).intValue())
                        .setCount(3).setWinPatternId(p.getId()).setWinPositions(positions).setPayout(DecimalUtil.getBigDecimal2(symbol.getMultiplier().doubleValue() * stake));
                winLines.add(winLine);
            }
        });
        return winLines;
    }

    private static boolean freeMode() {
        return RandomUtil.nextDouble() <= GLOBAL_CONFIG.getFreePro().doubleValue();
    }


    private static List<List<Integer>> initReelTale() {
        List<List<Integer>> reelTable = new ArrayList<>();
        for (int i = 0; i < GLOBAL_CONFIG.getRotary(); i++) {
            List<Integer> rotary = new ArrayList<>();
            for (int j = 0; j < GLOBAL_CONFIG.getRotaryNum(); j++) {
                rotary.add(-1);
            }
            reelTable.add(rotary);
        }
        return reelTable;
    }

    private static Outcome initOutCome(double stake) {
        Outcome outcome = new Outcome();
        outcome.setScreen(initReelTale()).setBet(DecimalUtil.getBigDecimal2(stake));
        return outcome;
    }

    private static int diamondSymbolNum() {
        double random = RandomUtil.nextDouble();
        for(int i = 1; i >= 0; i--) {
            if(random <= GLOBAL_CONFIG.getDiamondSymbolPro().get(i).doubleValue()) {
                return i + 1;
            }
        }
        return 0;
    }



    private static void setScatter(Outcome outcome,boolean freeMode) {
        List<List<Integer>> reelTable = outcome.getScreen();
        int num = diamondSymbolNum();
        if (freeMode) {
            num = 3;
        }
        SpecialSymbols symbols = new SpecialSymbols();
        Map<Integer, List<List<Integer>>> map = new HashMap<>();
        symbols.setScatter(map);
        List<List<Integer>> scatterList = new ArrayList<>();
        map.put(DIAMOND.getIndex(),scatterList);
        outcome.setSpecial_symbols(symbols);
        if(num > 0) {
            List<Integer> integers = rotaryIndex();
            for (int j = 0; j < num; j++) {
                int icon = DIAMOND.getIndex();
                int row = integers.remove(0);
                int col = RandomUtil.nextInt(GLOBAL_CONFIG.getRotaryNum());
                List<Integer> positions = new ArrayList<>();
                scatterList.add(positions);
                positions.add(row);
                positions.add(col);
                reelTable.get(row).set(col,icon);
            }
        }
    }

    private static void setAnotherSymbols(List<List<Integer>> reelTable) {
        for (int i = 1; i < GLOBAL_CONFIG.getRotary(); i++) {
            List<Integer> rotary = reelTable.get(i);
            for (int j = 0; j < rotary.size(); j++) {
                if(rotary.get(j) == -1) {
                    int icon = randomNormalSymbol(-1).getIndex();
                    rotary.set(j,icon);
                }
            }
        }
    }

    private static void setKeySymbols(boolean prize,List<List<Integer>> reelTable,double stake,double v) {
        for (int i = 0; i < GLOBAL_CONFIG.getRotaryNum(); i++) {
            if (reelTable.get(0).get(i) == -1) {
                List<WinLine> winLine = getWinLine(reelTable, i, stake);
                if (prize && !winLine.isEmpty() && RandomUtil.nextDouble() <= GLOBAL_CONFIG.getPrizePro().doubleValue() * Math.pow(v,2)) {
                    reelTable.get(0).set(i, winLine.get(0).getSymbol());
                } else {
                    int excludes = -1;
                    if (!winLine.isEmpty()) {
                        excludes = winLine.get(0).getSymbol();
                    }
                    reelTable.get(0).set(i, randomNormalSymbol(excludes).getIndex());
                }
            }
        }
    }

    private static void settlementMain(ApiClientResult clientResult,Outcome outcome,double stake,double v,boolean bonus_buy) {
        List<List<Integer>> reelTable = outcome.getScreen();
        //结算free
        AtomicReference<Double> totalPayout = new AtomicReference<>((double) 0);
        if (!outcome.getSpecial_symbols().getScatter().isEmpty() && outcome.getSpecial_symbols().getScatter().get(DIAMOND.getIndex()).size() == 3) {
            int max = DecimalUtil.getBigDecimal2(v * 60).intValue();
            if (bonus_buy) {
                max = DecimalUtil.getBigDecimal2(Math.pow(v,2) * 80 * 1.7).intValue();
            }
            int multiplier = RandomUtil.nextInt(10,max);
            if (RandomUtil.nextDouble() <= 0.08 * v) {
                multiplier = RandomUtil.nextInt(10,300);
            }

            List<Object> win = new ArrayList<>();
            win.add("scatter");
            int finalMultiplier = multiplier;
            totalPayout.updateAndGet(v1 -> v1 + (stake * finalMultiplier));
            win.add(DecimalUtil.getBigDecimal2(stake * multiplier));
            win.add(outcome.getSpecial_symbols().getScatter().get(DIAMOND.getIndex()));
            outcome.getWins().add(win);
            Map<String,Map<String, BigDecimal>> features = new HashMap<>();
            Map<String,BigDecimal> featuresMap = new HashMap<>();
            features.put("bonus_data",featuresMap);
            featuresMap.put("multiplier",BigDecimal.valueOf(multiplier));
            clientResult.setFeatures(features);
        }
        //结算payout
        PAY_LINE_CONFIG.getPayLines().forEach(p-> {
            List<Integer> positions = p.getPositions();
            List<Integer> tmpSet = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                Integer index = reelTable.get(i).get(positions.get(i));
                if(index == 8) {
                    tmpSet.add(-1);
                    tmpSet.add(-2);
                    break;
                }
                if(!tmpSet.contains(index)) {
                    tmpSet.add(index);
                }
            }
            if(tmpSet.size() <= 1) {
                Symbol tempSymbol = getSymbol(tmpSet.get(0));
                double payout = DecimalUtil.getBigDecimal2(tempSymbol.getMultiplier().doubleValue() * (stake / GLOBAL_CONFIG.getBaseBet())).doubleValue();
                List<Object> win = new ArrayList<>();
                win.add("line");
                win.add(payout);
                win.add(positions);
                win.add(p.getId());
                outcome.getWins().add(win);
                totalPayout.updateAndGet(v1 -> v1 + payout);
            }
            tmpSet.clear();
        });
        outcome.setWin(DecimalUtil.getBigDecimal2(totalPayout.get()));
    }

    private static void generateOutcome(ApiClientResult clientResult,double stake,double v,boolean freeMode,boolean bonus_buy) {
        Outcome outcome = initOutCome(stake);
        clientResult.setOutcome(outcome);
        List<List<Integer>> reelTable = outcome.getScreen();
        setScatter(outcome,freeMode);
        setAnotherSymbols(reelTable);
        setKeySymbols(!outcome.getSpecial_symbols().getScatter().isEmpty(),reelTable,stake,v);
        settlementMain(clientResult,outcome,stake,v,bonus_buy);
    }



    public static ApiClientResult generateApiResult(double stake, double v,boolean bonus_buy) {
        ApiClientResult clientResult = new ApiClientResult();
        boolean freeMode = freeMode();
        if (bonus_buy) {
            freeMode = true;
            v *= 1.0001;
        }
        generateOutcome(clientResult,stake,v,freeMode,bonus_buy);
        return clientResult;
    }



    private static List<Integer> rotaryIndex() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < GLOBAL_CONFIG.getRotary(); i++) {
            list.add(i);
        }
        Collections.shuffle(list);
        return list;
    }

    public static RoundDetailDto generateRoundDetail(ApiClientResult clientResult, double beforeScore, Player player,double betScore) {
        List<List<Integer>> screen = clientResult.getOutcome().getScreen();

        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC+00:00");
        roundDetailDto.setUsedFeature(clientResult.getFlow().getPurchased_feature() instanceof BuyBonus);
        BigDecimal realBet = DecimalUtil.getBigDecimal2(betScore);
        BigDecimal realWin = DecimalUtil.getBigDecimal2(clientResult.getOutcome().getWin().doubleValue() / SUB_UNITS);
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(clientResult.getOutcome().getWin().doubleValue() / SUB_UNITS - betScore);
        roundDetailDto.setBetText(realBet.toPlainString());
        roundDetailDto.setStake(DecimalUtil.getBigDecimal2(clientResult.getOutcome().getBet().doubleValue() / SUB_UNITS).toPlainString());
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
        castDetailWinLine(roundDetailDto,clientResult);
        roundDetailDto.setSymbols(castDetailSymbol(screen));
        return roundDetailDto;
    }

    private static void castDetailWinLine(RoundDetailDto roundDetailDto,ApiClientResult clientResult) {
        List<List<Object>> prizeDetail = clientResult.getOutcome().getWins();
        List<String> result = new ArrayList<>();
        roundDetailDto.setWinLines(result);
        for (List<Object> object : prizeDetail) {
            if (object.size() == 4) {
                String winLine = "Line " + ((int)object.get(object.size() -1) + 1) + "th - " + DecimalUtil.getBigDecimal2(((double)object.get(1)) / SUB_UNITS).doubleValue();
                result.add(winLine);
            } else {
                String scatterWin = "scatter " + DecimalUtil.getBigDecimal2(((BigDecimal)object.get(1)).doubleValue() / SUB_UNITS).doubleValue();
                roundDetailDto.setScatterWin(scatterWin);
                roundDetailDto.setBonusMultiplier(clientResult.getFeatures().get("bonus_data").get("multiplier").intValue());
                roundDetailDto.setBonusPayout(DecimalUtil.getBigDecimal2(((BigDecimal)object.get(1)).doubleValue() / SUB_UNITS));
            }
        }
    }

    private static String getSymbolName(int index) {
        Optional<Symbol> first = SYMBOL_CONFIG.getSymbols().stream().filter(s -> s.getIndex() == index).findFirst();
        return first.map(Symbol::getName).orElse("");
    }
    private static List<String> castDetailSymbol(List<List<Integer>> rotary) {
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < GLOBAL_CONFIG.getRotary(); i++) {
            for (int i1 = 0; i1 < GLOBAL_CONFIG.getRotaryNum(); i1++) {
                int icon = rotary.get(i1).get(i);
                symbols.add(getSymbolName(icon));
            }
        }
        return symbols;
    }


    public static List<List<Integer>> getLines() {
        List<List<Integer>> lines = new ArrayList<>();
        List<PayLines> payLines = PAY_LINE_CONFIG.getPayLines();
        payLines.forEach(p -> {
            lines.add(p.getPositions());
        });
        return lines;
    }

    public static Map<Integer,List<Integer>> getPayTable() {
        Map<Integer,List<Integer>> map = new HashMap<>();
        NORMAL_SYMBOLS.forEach(n -> {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                list.add(0);
            }
            list.add(n.getMultiplier().intValue());
            map.put(n.getIndex(),list);
        });
        return map;
    }

    public static Map<Integer,Map<String,List<Integer>>> getPayTables() {
        Map<Integer,Map<String,List<Integer>>> map = new HashMap<>();
        NORMAL_SYMBOLS.forEach(n -> {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                list.add(0);
            }
            list.add(n.getMultiplier().intValue());
            Map<String,List<Integer>> listMap = new HashMap<>();
            listMap.put("default",list);
            map.put(n.getIndex(),listMap);
        });
        return map;
    }

    public static JSONObject getFeatureOptions() {
        JSONObject jsonObject = new JSONObject();
        JSONObject options = new JSONObject();
        options.put("base_bet",GLOBAL_CONFIG.getBaseBet());
        options.put("bonus_buy",GLOBAL_CONFIG.getBonusBuy());
        jsonObject.put("feature_multipliers",options);
        jsonObject.put("disabled_features",new ArrayList<>());
        return jsonObject;
    }
    public static List<JSONObject> getSpecialSymbols() {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("kind","scatter");
        jsonObject.put("symbol","8");
        List<JSONObject> list = new ArrayList<>();
        list.add(jsonObject);
       return list;
    }


}
