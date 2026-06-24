package com.bgaming.alienfruits2.logic;


import com.alibaba.fastjson.JSONObject;
import com.bgaming.alienfruits2.config.PayTableConfig;
import com.bgaming.alienfruits2.entity.PayTable;
import com.bgaming.alienfruits2.entity.Symbol;
import com.bgaming.alienfruits2.entity.client.*;
import com.bgaming.alienfruits2.entity.log.RoundDetailDto;
import com.bgaming.alienfruits2.config.GlobalConfig;
import com.bgaming.alienfruits2.config.SymbolConfig;
import com.bgaming.alienfruits2.utils.DateTimeUtil;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.RandomUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Component
public class AlienFruits2Context {

    private static SymbolConfig SYMBOL_CONFIG;

    private static PayTableConfig PAY_TABLE_CONFIG;

    public static GlobalConfig GLOBAL_CONFIG;

    private static List<Symbol> NORMAL_SYMBOLS;

    private static Symbol SCATTER;

    private static Symbol BONUS;

    public static final int SUB_UNITS = 100;


    public AlienFruits2Context(SymbolConfig config, PayTableConfig payTableConfig, GlobalConfig globalConfig) {
        SYMBOL_CONFIG = config;
        PAY_TABLE_CONFIG = payTableConfig;
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
        SCATTER = symbols.stream().filter(s-> s.getType() == 1).findFirst().get();
        BONUS = symbols.stream().filter(s-> s.getType() == 2).findFirst().get();
    }



    private static Symbol randomNormalSymbol(List<Integer> excludesSymbol) {
        double random = RandomUtil.nextDouble();
        List<Symbol> symbols = new ArrayList<>();
        for (Symbol symbol : NORMAL_SYMBOLS) {
            if(excludesSymbol.contains(symbol.getIndex())) {
                continue;
            }
            if(random <= symbol.getWeight()) {
                return symbol;
            }
            symbols.add(symbol);
        }
        return symbols.get(RandomUtil.nextInt(symbols.size()));
    }


    private static Symbol getSymbol(int index) {
        Optional<Symbol> first = SYMBOL_CONFIG.getSymbols().stream().filter(s -> s.getIndex() == index).findFirst();
        return first.orElse(null);
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

    private static int diamondSymbolNum(boolean isFree) {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getDiamondSymbolPro();
        int max = 6;
        if (isFree) {
            diamondSymbolPro = GLOBAL_CONFIG.getFreeDiamondSymbolPro();
            max = 3;
        }
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue()) {
                return max - i;
            }
        }
        return 0;
    }

    private static int getMultiplier() {
        double random = RandomUtil.nextDouble();
        if (random <= GLOBAL_CONFIG.getMultiplierPro().get(2).doubleValue()) {
            return RandomUtil.nextInt(101,501);
        } else if (random <= GLOBAL_CONFIG.getMultiplierPro().get(1).doubleValue()) {
            return RandomUtil.nextInt(31,101);
        } else if (random <= GLOBAL_CONFIG.getMultiplierPro().get(0).doubleValue()) {
            return RandomUtil.nextInt(6,31);
        }
        return RandomUtil.nextInt(2,6);
    }

    private static List<int[]> usePoint(List<List<Integer>> reelTable) {
        List<int[]> usePoint = new ArrayList<>();
        for (int i = 0; i < reelTable.size(); i++) {
            for (int i1 = 0; i1 < reelTable.get(i).size(); i1++) {
                if (reelTable.get(i).get(i1) == -1) {
                    usePoint.add(new int[]{i,i1});
                }
            }
        }
        Collections.shuffle(usePoint);
        return usePoint;
    }

    private static void setSymbols(List<List<Integer>> reelTable,List<int[]> usePoint,double v,boolean spin) {
        Map<Integer,Integer> map = new HashMap<>();
        for (List<Integer> integers : reelTable) {
            for (int symbol : integers) {
                if (symbol != -1) {
                    if (map.containsKey(symbol)) {
                        map.put(symbol, map.get(symbol) + 1);
                    } else {
                        map.put(symbol, 1);
                    }
                }

            }
        }
        List<Integer> excludesSymbol = new ArrayList<>();
        if (RandomUtil.nextDouble() <= GLOBAL_CONFIG.getPrizePro().doubleValue() * Math.pow(v,3) && spin) {
            Symbol symbol = randomNormalSymbol(new ArrayList<>());
            map.put(symbol.getIndex(),8);
            for (int i = 0; i < 8; i++) {
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1],symbol.getIndex());
            }
        }
        int size = usePoint.size();
        for (int i = 0; i < size; i++) {
            int[] position = usePoint.remove(0);
            map.forEach((k,value) -> {
                if (value >= 9 && RandomUtil.nextDouble() <= (0.7 / Math.pow(v,3))) {
                    excludesSymbol.add(k);
                }
            });
            Symbol symbol = randomNormalSymbol(excludesSymbol);
            if (map.containsKey(symbol.getIndex())) {
                map.put(symbol.getIndex(),map.get(symbol.getIndex()) + 1);
            } else {
                map.put(symbol.getIndex(),1);
            }
            reelTable.get(position[0]).set(position[1],symbol.getIndex());
        }
    }

    private static void setBonus(Outcome outcome,List<List<Integer>> reelTable,List<int[]> usePoint,int round) {
        if (RandomUtil.nextDouble() <= GLOBAL_CONFIG.getBonusPro().doubleValue()) {
            int num = 1;
            if (RandomUtil.nextDouble() <= 0.1) {
                num = 2;
            }
            num = Math.min(num,usePoint.size());
            for (int i = 0; i < num; i++) {
                int multiplier = getMultiplier();
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1], BONUS.getIndex());
                List<Object> cascade = new ArrayList<>();
                cascade.add("cascade_" + round);
                cascade.add(multiplier);
                cascade.add(position);
                outcome.getStorage().getBombs().add(cascade);
                outcome.setMultiplier(outcome.getMultiplier() + multiplier);
            }
        }
    }


    private static void setScatter(Outcome outcome,boolean bonus_buy,List<int[]> usePoint,boolean isFree) {
        List<List<Integer>> reelTable = outcome.getScreen();
        int num = diamondSymbolNum(isFree);
        if (bonus_buy) {
            num = 4;
        }
        if(num > 0) {
            for (int j = 0; j < num; j++) {
                int icon = SCATTER.getIndex();
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1],icon);
            }
        }
        outcome.setScatterNum(num);
    }

    private static BigDecimal getOdd(int symbol,int num) {
        List<PayTable> payTables = PAY_TABLE_CONFIG.getPayTables();
        for (PayTable payTable : payTables) {
            if (payTable.getType() == symbol) {
                Map<String, BigDecimal> multiplierMap = payTable.getMultiplierMap();
                int key = Math.min(12,num / 2 * 2);
                return multiplierMap.get(String.valueOf(key));
            }
        }
        return BigDecimal.ZERO;
    }

    private static void settlementMain(Outcome outcome, double stake,int round,boolean isFree) {
        outcome.setDrop(false);
        outcome.getDropPoint().clear();
        List<List<Integer>> reelTable = outcome.getStorage().getSaved_screens().get(round);
        Map<Integer,List<int[]>> map = new HashMap<>();
        for (int i = 0; i < reelTable.size(); i++) {
            for (int i1 = 0; i1 < reelTable.get(i).size(); i1++) {
                Integer symbol = reelTable.get(i).get(i1);
                if (map.containsKey(symbol)) {
                    List<int[]> list = map.get(symbol);
                    list.add(new int[] {i,i1});
                } else {
                    List<int[]> list = new ArrayList<>();
                    list.add(new int[] {i,i1});
                    map.put(symbol,list);
                }
            }
        }
        map.forEach((k,v)-> {
            if (k != SCATTER.getIndex() && k != BONUS.getIndex() && v.size() >= 8) {
                BigDecimal odd = getOdd(k, v.size());
                int payout = DecimalUtil.getBigDecimal2(odd.doubleValue() * stake).intValue();
                List<Object> win = new ArrayList<>();
                win.add("cascade_" + round);
                win.add(payout);
                win.add(v);
                win.add(String.valueOf(k));
                outcome.getWins().add(win);
                outcome.setWin(DecimalUtil.getBigDecimal2(outcome.getWin().doubleValue() + payout));
                outcome.setDrop(true);
                v.forEach(p-> outcome.getDropPoint().add(p[0] * GLOBAL_CONFIG.getRotary() + p[1]));
            }
        });
        if (!outcome.isDrop()) {
            SpecialSymbols symbols = outcome.getSpecial_symbols();
            symbols.getScatter().put(String.valueOf(SCATTER.getIndex()),new ArrayList<>());
            if (map.containsKey(SCATTER.getIndex())) {
                List<int[]> list = map.get(SCATTER.getIndex());
                symbols.getScatter().get(String.valueOf(SCATTER.getIndex())).addAll(list);
                if (list.size() >= 4) {
                    BigDecimal odd = getOdd(SCATTER.getIndex(), list.size());
                    List<Object> win = new ArrayList<>();
                    win.add("scatter");
                    win.add(DecimalUtil.getBigDecimal2(odd.doubleValue() * stake));
                    win.add(list);
                    outcome.getWins().add(win);
                    outcome.setWin(DecimalUtil.getBigDecimal2(outcome.getWin().doubleValue() + odd.doubleValue() * stake));
                    outcome.setFreespins_issued(15);
                }
                if (isFree && list.size() == 3) {
                    outcome.setFreespins_issued(5);
                }
            }
            if (outcome.getWin().doubleValue() > 0 && outcome.getMultiplier() > 0) {
                int totalPayout = outcome.getWin().intValue();
                outcome.getStorage().setRound_multiplier(outcome.getMultiplier());
                outcome.getStorage().setTotal_multiplier(outcome.getStorage().getTotal_multiplier() + outcome.getMultiplier());
                int bombsWin = totalPayout * (outcome.getStorage().getTotal_multiplier());
                List<Object> win = new ArrayList<>();
                win.add("bombs_win");
                win.add(bombsWin - totalPayout);
                outcome.getWins().add(win);
                outcome.setWin(DecimalUtil.getBigDecimal2(bombsWin));
            }
        }
    }

    private static void generateOutcome(ApiClientResult clientResult,double stake,double v,boolean bonus_buy,boolean isFree,int totalMultiplier) {
        Outcome outcome = initOutCome(stake);
        outcome.getStorage().setTotal_multiplier(totalMultiplier);
        clientResult.setOutcome(outcome);
        int round = 0;
        List<List<Integer>> reelTable = outcome.getScreen();
        List<int[]> usePoint = usePoint(reelTable);
        setScatter(outcome,bonus_buy,usePoint,isFree);
        setBonus(outcome,reelTable,usePoint,round);
        setSymbols(reelTable,usePoint,v,true);
        outcome.getStorage().getSaved_screens().add(copyReelTable(reelTable));
        settlementMain(outcome,stake,round,isFree);

        while (outcome.isDrop()) {
            round++;
            reelTable = dropReelTable(reelTable, outcome.getDropPoint());
            usePoint = usePoint(reelTable);
            setDropScatter(outcome,reelTable,usePoint,isFree);
            setBonus(outcome,reelTable,usePoint,round);
            setSymbols(reelTable,usePoint,v,false);
            outcome.getStorage().getSaved_screens().add((copyReelTable(reelTable)));
            settlementMain(outcome,stake,round,isFree);
        }
    }

    private static List<List<Integer>> copyReelTable(List<List<Integer>> reelTable) {
        List<List<Integer>> lists = new ArrayList<>();
        for (List<Integer> integers : reelTable) {
            List<Integer> list = new ArrayList<>();
            lists.add(list);
            list.addAll(integers);
        }
        return lists;
    }

    private static void setDropScatter(Outcome outcome,List<List<Integer>> reelTable,List<int[]> usePoint,boolean inFree) {
        int scatterNum = outcome.getScatterNum();
        int num = 6 - scatterNum;
        if (inFree) {
            num = 3 - scatterNum;
        }
        if (num < 0) {
            num = 0;
        }
        for (int i = 0; i < num; i++) {
            if (RandomUtil.nextDouble() <= GLOBAL_CONFIG.getFreePro().doubleValue()) {
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1], SCATTER.getIndex());
                scatterNum++;
            }
        }
        outcome.setScatterNum(scatterNum);
    }

    private static List<List<Integer>> dropReelTable(List<List<Integer>> reelTable, List<Integer> dropList) {
        List<List<Integer>> dropReelTable = new ArrayList<>();
        for (int i = 0; i < reelTable.size(); i++) {
            List<Integer> rotary = new ArrayList<>();
            dropReelTable.add(rotary);
            int num = 0;
            for (int i1 = reelTable.get(i).size() - 1; i1 >= 0; i1--) {
                if (dropList.contains(i * GLOBAL_CONFIG.getRotary() + i1)) {
                    num++;
                } else {
                    rotary.add(0,reelTable.get(i).get(i1));
                }
            }
            for (int i1 = 0; i1 < num; i1++) {
                rotary.add(0,-1);
            }
        }
        return dropReelTable;
    }



    public static List<ApiClientResult> generateApiResult(Player player,double stake, double v,boolean bonus_buy,boolean freespin_chance,String pOrder) {
        boolean isFree = false;
        int freeNum = 0;
        int totalFreeNum = 0;
        String round = pOrder;
        int round_num = 1;
        int totalMultiplier = 0;
        List<ApiClientResult> apiClientResults = new ArrayList<>();
        if (player.extendDataContainsKey("totalFreeNum")) {
            totalFreeNum = player.getExtendData("totalFreeNum", Integer.class);
        }
        if (player.extendDataContainsKey("freeNum")) {
            freeNum = player.getExtendData("freeNum",Integer.class);
        }
        if (player.extendDataContainsKey("apiClient")) {
            apiClientResults = player.getExtendDataList("apiClient",ApiClientResult.class);
            if (!apiClientResults.isEmpty()) {
                round = apiClientResults.get(0).getFlow().getRound_id();
                round_num += apiClientResults.size();
                if (round_num > 1) {
                    totalMultiplier = apiClientResults.get(apiClientResults.size() - 1).getOutcome().getStorage().getTotal_multiplier();
                }
            }
        }
        if (totalFreeNum > 0) {
            isFree = true;
        }
        ApiClientResult clientResult = new ApiClientResult();
        apiClientResults.add(clientResult);
        generateOutcome(clientResult,stake,v,bonus_buy && totalFreeNum == 0,isFree,totalMultiplier);
        if (clientResult.getOutcome().getFreespins_issued() > 0) {
            totalFreeNum += clientResult.getOutcome().getFreespins_issued();
            freeNum += clientResult.getOutcome().getFreespins_issued();
        }
        Flow flow = new Flow();
        if (isFree) {
            freeNum--;
            flow.setCommand("freespin");
            if (freeNum > 0) {
                flow.setState("freespins");
            }
        }
        if (totalFreeNum > 0 && !isFree) {
            flow.setState("freespins");
        }

        clientResult.setFlow(flow);
        if (totalFreeNum > 0) {
            JSONObject features = new JSONObject();
            features.put("freespins_issued",totalFreeNum);
            features.put("freespins_left",freeNum);
            clientResult.setFeatures(features);
            if (!isFree) {
                flow.setAvailable_actions(new String[] {"init","freespin"});
            }
            if (isFree && freeNum == 0) {
                flow.setAvailable_actions(new String[] {"init","spin"});
            }
        }
        if (freespin_chance) {
            flow.getPurchased_feature().put("name","freespin_chance");
        }
        if (bonus_buy) {
            flow.getPurchased_feature().put("name","freespin_buy");
        }
        flow.getPurchased_feature().put("name",apiClientResults.get(0).getFlow().getPurchased_feature().get("name"));
        flow.setRound_id(round).setLast_action_id(round + "_" + round_num);
        player.getExtendJson().put("apiClient",apiClientResults);
        player.setExtendData("totalFreeNum",totalFreeNum);
        player.setExtendData("freeNum",freeNum);
        return apiClientResults;
    }

    public static RoundDetailDto generateRoundDetail(List<ApiClientResult> clientResults, double beforeScore, Player player, double betScore) {

        AtomicReference<Double> totalWin = new AtomicReference<>((double) 0);
        clientResults.forEach(c -> {
            totalWin.updateAndGet(v -> v + c.getOutcome().getWin().doubleValue());
        });
        BigDecimal realBet = DecimalUtil.getBigDecimal2(betScore);
        String usedFeature = "";
        String betTextBuy = realBet.toPlainString();
        if (player.extendDataContainsKey("bonusBuy")) {
            usedFeature = player.getExtendData("bonusBuy",String.class);
            if (usedFeature.equals("Freespin buy")) {
                betTextBuy = 100  + " * "  + DecimalUtil.getBigDecimal2(realBet.doubleValue() / 100) + "=" + realBet.toPlainString();
            } else if (usedFeature.equals("Freespin chance")){
                betTextBuy = 1.25  + " * "  + DecimalUtil.getBigDecimal2(realBet.doubleValue() / 1.25) + "=" + realBet.toPlainString();
            }
        }


        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));

        BigDecimal realWin = DecimalUtil.getBigDecimal2(totalWin.get() / SUB_UNITS);
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(totalWin.get() / SUB_UNITS - betScore);
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
        roundDetailDto.setClientResults(clientResults);
        return roundDetailDto;
    }

    public String getSymbolName(int index) {
        Optional<Symbol> first = SYMBOL_CONFIG.getSymbols().stream().filter(s -> s.getIndex() == index).findFirst();
        return first.map(Symbol::getName).orElse("");
    }

    public boolean hasCascadeWin(List<List<Object>> wins, String cascadeKey) {
        if (wins == null || wins.isEmpty()) return false;
        for (List<Object> win : wins) {
            if (win != null && win.get(0) != null && win.get(0).toString().equals(cascadeKey)) {
                return true;
            }
        }
        return false;
    }

    // 计算一次 spin 所有 cascade wins 的总和（不含 bombs_win）
    public BigDecimal sumCascadeWins(List<List<Object>> wins) {
        if (wins == null || wins.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (List<Object> win : wins) {
            if (win != null && win.get(0) != null && !win.get(0).toString().startsWith("bombs")) {
                sum = sum.add(new BigDecimal(win.get(1).toString()));
            }
        }
        return DecimalUtil.getBigDecimal2(sum.doubleValue() / 100);
    }

    // 计算 Total win = sum * total_multiplier / 100
    public BigDecimal calcTotalWin(List<List<Object>> wins, int totalMultiplier) {
        return DecimalUtil.getBigDecimal2(sumCascadeWins(wins).doubleValue() * totalMultiplier);
    }

    public BigDecimal getCascadeWin(List<List<Object>> wins, int cascadeIndex) {
        if (wins == null || wins.isEmpty()) return BigDecimal.ZERO;
        String cascadeKey = "cascade_" + cascadeIndex;
        double sum = 0;
        for (List<Object> win : wins) {
            if (win != null && win.get(0) != null && win.get(0).toString().equals(cascadeKey)) {
                sum += Double.parseDouble(String.valueOf(win.get(1)));
            }
        }
        return DecimalUtil.getBigDecimal2(sum / 100);
    }

    public BigDecimal getScatterWin(List<List<Object>> wins) {
        if (wins == null || wins.isEmpty()) return BigDecimal.ZERO;
        String cascadeKey = "scatter";
        double sum = 0;
        for (List<Object> win : wins) {
            if (win != null && win.get(0) != null && win.get(0).toString().equals(cascadeKey)) {
                sum += Double.parseDouble(String.valueOf(win.get(1)));
            }
        }
        return DecimalUtil.getBigDecimal2(sum / 100);
    }


    public static Map<Integer,int[]> getPayTable() {
        Map<Integer,int[]> map = new HashMap<>();
        map.put(1,new int[] {200,500,1000});
        map.put(2,new int[] {50,200,500});
        map.put(3,new int[] {40,100,300});
        map.put(4,new int[] {30,40,240});
        map.put(5,new int[] {20,30,200});
        map.put(6,new int[] {16,24,160});
        map.put(7,new int[] {10,20,100});
        map.put(8,new int[] {8,18,80});
        map.put(9,new int[] {5,15,40});
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
        options.put("freespin_chance",GLOBAL_CONFIG.getBonusBuy());
        options.put("freespin_buy",GLOBAL_CONFIG.getFreeSpinBuy());
        jsonObject.put("feature_multipliers",options);
        jsonObject.put("disabled_features",new ArrayList<>());
        return jsonObject;
    }
    public static List<JSONObject> getSpecialSymbols() {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("kind","scatter");
        jsonObject.put("symbol","0");
        List<JSONObject> list = new ArrayList<>();
        list.add(jsonObject);
       return list;
    }


}
