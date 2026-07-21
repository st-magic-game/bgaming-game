package com.bgaming.chickenrush.logic;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.chickenrush.config.GlobalConfig;
import com.bgaming.chickenrush.entity.PayTable;
import com.bgaming.chickenrush.entity.Symbol;
import com.bgaming.chickenrush.entity.client.*;
import com.bgaming.chickenrush.entity.log.RoundDetailDto;
import com.bgaming.chickenrush.config.PayTableConfig;
import com.bgaming.chickenrush.config.SymbolConfig;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.RandomUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Component
public class ChickenRushContext {

    private static SymbolConfig SYMBOL_CONFIG;

    private static PayTableConfig PAY_TABLE_CONFIG;

    public static GlobalConfig GLOBAL_CONFIG;

    private static List<Symbol> NORMAL_SYMBOLS;

    private static Symbol SCATTER;

    private static Symbol WILD;

    private static Symbol BONUS;

    private static Symbol MULTIPLIER;

    public static final int SUB_UNITS = 100;

    public static final int[] LEVEL_MULTIPLIER = {10,5,3,2};

    public static final double[] LEVEL_MULTIPLIER_PRO = {0.01,0.1,0.45,1};

    public static final int[] FREE_NUM = {5,7,10};

    public static final int[] FEATURE_SYMBOL = {1,2,3};


    public ChickenRushContext(SymbolConfig config, PayTableConfig payTableConfig, GlobalConfig globalConfig) {
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
        Optional<Symbol> scatter = symbols.stream().filter(s -> s.getType() == 1).findFirst();
        scatter.ifPresent(symbol -> SCATTER = symbol);


        Optional<Symbol> wild = symbols.stream().filter(s -> s.getType() == 2).findFirst();
        wild.ifPresent(symbol -> WILD = symbol);

        Optional<Symbol> bonus = symbols.stream().filter(s -> s.getType() == 3).findFirst();
        bonus.ifPresent(symbol -> BONUS = symbol);

        Optional<Symbol> multiplier = symbols.stream().filter(s -> s.getType() == 4).findFirst();
        multiplier.ifPresent(symbol -> MULTIPLIER = symbol);
    }



    private static Symbol randomNormalSymbol(List<String> excludesSymbol) {
        double random = RandomUtil.nextDouble();
        List<Symbol> symbols = new ArrayList<>();
        for (Symbol symbol : NORMAL_SYMBOLS) {
            if(excludesSymbol != null && excludesSymbol.contains(String.valueOf(symbol.getIndex()))) {
                continue;
            }
            if(random <= symbol.getWeight()) {
                return symbol;
            }
            symbols.add(symbol);
        }
        return symbols.get(RandomUtil.nextInt(symbols.size()));
    }

    private static List<List<String>> initReelTale() {
        List<List<String>> reelTable = new ArrayList<>();
        for (int i = 0; i < GLOBAL_CONFIG.getRotary(); i++) {
            List<String> rotary = new ArrayList<>();
            for (int j = 0; j < GLOBAL_CONFIG.getRotaryNum(); j++) {
                rotary.add("-1");
            }
            reelTable.add(rotary);
        }
        return reelTable;
    }

    private static Outcome initOutCome(double stake,boolean isFree,List<Integer> wonList,List<int[]> stick_symbol) {
        Outcome outcome = new Outcome();
        outcome.setScreen(initReelTale()).setBet(DecimalUtil.getBigDecimal2(stake));
        if (isFree) {
            outcome.getStorage().setAccumulated_bonus_multipliers(wonList);
            outcome.getStorage().setSticky_symbols(stick_symbol);
            outcome.getStorage().setPrevious_sticky_symbols(new ArrayList<>(stick_symbol));
            outcome.getStorage().setBonus_multipliers(new ArrayList<>());
        } else {
            outcome.getStorage().setWild_multipliers(new ArrayList<>());
        }
        return outcome;
    }

    private static int diamondSymbolNum() {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getDiamondSymbolPro();
        int max = 5;
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue()) {
                return max - i;
            }
        }
        return 0;
    }

    private static int wildNum() {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getWildPro();
        int max = 3;
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue()) {
                return max - i;
            }
        }
        return 0;
    }

    private static int freeSymbolNum(double v) {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getFreeSymbol();
        int max = 4;
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue() * Math.pow(v,2)) {
                return max - i;
            }
        }
        return 0;
    }

    private static int usedNum() {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getIssued();
        int max = 3;
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue()) {
                return max - i;
            }
        }
        return 0;
    }

    private static int freeMultiplierNum(double v) {
        double random = RandomUtil.nextDouble();
        List<BigDecimal> diamondSymbolPro = GLOBAL_CONFIG.getFreeMultiplier();
        int max = 2;
        for (int i = 0; i < diamondSymbolPro.size(); i++) {
            if(random <= diamondSymbolPro.get(i).doubleValue() * Math.pow(v,2)) {
                return max - i;
            }
        }
        return 0;
    }


    private static int getMultiplier() {
        double v = RandomUtil.nextDouble();
        for (int i = 0; i < LEVEL_MULTIPLIER_PRO.length; i++) {
            if (v <= LEVEL_MULTIPLIER_PRO[i]) {
                return LEVEL_MULTIPLIER[i];
            }
        }
        return LEVEL_MULTIPLIER[RandomUtil.nextInt(LEVEL_MULTIPLIER.length - 1)];
    }

    private static List<int[]> usePoint(List<List<String>> reelTable,int excludeRotary) {
        List<int[]> usePoint = new ArrayList<>();
        for (int i = 0; i < reelTable.size(); i++) {
            if (i == excludeRotary) {
                continue;
            }
            for (int i1 = 0; i1 < reelTable.get(i).size(); i1++) {
                if (Objects.equals(reelTable.get(i).get(i1), "-1")) {
                    usePoint.add(new int[]{i,i1});
                }
            }
        }
        Collections.shuffle(usePoint);
        return usePoint;
    }

    private static void setOtherSymbols(Outcome outcome) {
        List<List<String>> screen = outcome.getScreen();
        for (int i = 0; i < screen.size(); i++) {
            if (i == 2) {
                continue;
            }
            for (int i1 = 0; i1 < screen.get(i).size(); i1++) {
                if (Objects.equals(screen.get(i).get(i1), "-1")) {
                    Symbol symbol = randomNormalSymbol(null);
                    screen.get(i).set(i1,String.valueOf(symbol.getIndex()));
                }
            }
        }
    }

    private static void setKeySymbols(Outcome outcome,double stake,double v) {
        List<List<String>> reelTable = outcome.getScreen();
        for (int i = 0; i < reelTable.get(2).size(); i++) {
            if (Objects.equals(reelTable.get(2).get(i), "-1")) {
                List<WinLine> winLine = getWinLine(outcome,stake,false);
                if (!winLine.isEmpty() && RandomUtil.nextDouble() <= GLOBAL_CONFIG.getPrizePro().doubleValue() * Math.pow(v,2) && outcome.getFreespins_issued() == 0) {
                    reelTable.get(2).set(i, winLine.get(RandomUtil.nextInt(winLine.size())).getSymbol());
                } else {
                    List<String> excludes = null;
                    if (!winLine.isEmpty()) {
                        excludes = new ArrayList<>();
                        for (WinLine line : winLine) {
                            excludes.add(line.getSymbol());
                        }
                    }
                    reelTable.get(2).set(i, String.valueOf(randomNormalSymbol(excludes).getIndex()));
                }
            }
        }
    }

    private static List<WinLine> getWinLine(Outcome outcome, double stake, boolean settlement) {

        List<List<String>> reelTable = outcome.getScreen();
        List<WinLine> winLines = new ArrayList<>();
        Map<String, List<Integer>> useCalcSymbol = useCalcSymbol(reelTable.get(0));

        useCalcSymbol.forEach((k,v)-> {
            List<List<Integer>> winPositions = new ArrayList<>();
            winPositions.add(new ArrayList<>(v));
            int count = v.size();
            count += antherSymbolNum(outcome,0,k);
            for (int i = 1; i < reelTable.size(); i++) {
                List<Integer> symbolPositionInRotary = getSymbolPositionInRotary(reelTable.get(i), k, settlement);
                if(symbolPositionInRotary.isEmpty()) {
                    if (i > 2) {
                        WinLine winLine = getWinLine(k, i, count, winPositions, stake);
                        winLines.add(winLine);
                    }
                    break;
                } else {
                    count *= (symbolPositionInRotary.size() + antherSymbolNum(outcome,i,k));
                    winPositions.add(new ArrayList<>(symbolPositionInRotary));
                    if(i == reelTable.size() - 1) {
                        WinLine winLine = getWinLine(k, 5, count, winPositions, stake);
                        winLines.add(winLine);
                    }
                }
            }
        });
        return winLines;
    }

    private static int antherSymbolNum(Outcome outcome,int rotary,String symbol) {
        AtomicInteger count = new AtomicInteger();
        List<List<String>> screen = outcome.getScreen();
        if (outcome.getStorage().getWild_multipliers() != null && !outcome.getStorage().getWild_multipliers().isEmpty()) {
            List<int[]> wildMultipliers = outcome.getStorage().getWild_multipliers();
            wildMultipliers.forEach(w -> {
                if (w[0] == rotary && (Objects.equals(screen.get(w[0]).get(w[1]), symbol) || Objects.equals(screen.get(w[0]).get(w[1]), String.valueOf(WILD.getIndex())))) {
                    count.addAndGet((w[2] - 1));
                }
            });
        }
        if (outcome.getStorage().getFinal_bonus_multipliers() != null && !outcome.getStorage().getFinal_bonus_multipliers().isEmpty()) {
            List<int[]> finalBonusMultipliers = outcome.getStorage().getFinal_bonus_multipliers();
            finalBonusMultipliers.forEach(w -> {
                if (w[0] == rotary && Objects.equals(screen.get(w[0]).get(w[1]), symbol)) {
                    count.addAndGet((w[2] - 1));
                }
            });
        }
        return count.get();
    }

    private static WinLine getWinLine(String symbol,int lines,int count,List<List<Integer>> winPositions,double stake) {
        WinLine winLine = new WinLine();
        BigDecimal odds = getOdd(Integer.parseInt(symbol), lines);
        winLine.setSymbol(symbol).setCount(count).setWinPositions(winPositions)
                .setPayout(DecimalUtil.getBigDecimal2(odds.doubleValue() * stake   * count));
        return winLine;
    }

    private static List<Integer> getSymbolPositionInRotary(List<String> rotary,String symbol,boolean settlement) {
        List<Integer> position = new ArrayList<>();
        for (int i = 0; i < rotary.size(); i++) {
            if (Objects.equals(symbol, rotary.get(i)) || String.valueOf(WILD.getIndex()).equals(rotary.get(i)) || (!settlement && Objects.equals(rotary.get(i), "-1"))) {
                position.add(i);
            }
        }
        return position;
    }

    private static Map<String,List<Integer>> useCalcSymbol(List<String> firstRotary) {
        Map<String,List<Integer>> useCalcMap = new HashMap<>();
        for (int i = 0; i < firstRotary.size(); i++) {
            if (Objects.equals(firstRotary.get(i), "0") || Objects.equals(firstRotary.get(i), "-1")) {
                continue;
            }
            if(useCalcMap.containsKey(firstRotary.get(i))) {
                useCalcMap.get(firstRotary.get(i)).add(i);
            } else {
                List<Integer> list = new ArrayList<>();
                list.add(i);
                useCalcMap.put(firstRotary.get(i),list);
            }
        }
        return useCalcMap;
    }


    private static void setWild(Outcome outcome) {
        List<List<String>> reelTable = outcome.getScreen();
        int num = wildNum();
        if (outcome.getFreespins_issued() > 0) {
            num = Math.min(1,num);
        }
        List<int[]> usePoint = usePoint(reelTable,0);
        if(num > 0) {
            HashMap<String,List<int[]>> hashMap = new HashMap<>();
            List<int[]> list = new ArrayList<>();
            hashMap.put(String.valueOf(WILD.getIndex()),list);
            outcome.getSpecial_symbols().setWild(hashMap);
            for (int j = 0; j < num; j++) {
                String icon = String.valueOf(WILD.getIndex());
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1],icon);
                int multiplier = getMultiplier();
                outcome.getStorage().getWild_multipliers().add(new int[]{position[0],position[1],multiplier});
                list.add(new int[] {position[0],position[1]});
            }
        }
    }


    private static void setScatter(Outcome outcome,List<int[]> usePoint,boolean buyBonus) {
        List<List<String>> reelTable = outcome.getScreen();
        int num = diamondSymbolNum();
        if (buyBonus) {
            num = 3;
        }
        if(num > 0) {
            HashMap<String,List<int[]>> hashMap = new HashMap<>();
            List<int[]> list = new ArrayList<>();
            hashMap.put(String.valueOf(SCATTER.getIndex()),list);
            outcome.getSpecial_symbols().setScatter(hashMap);
            for (int j = 0; j < num; j++) {
                String icon = String.valueOf(SCATTER.getIndex());
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1],icon);
                list.add(new int[] {position[0],position[1]});
            }
            if (num >= 3) {
                outcome.setFreespins_issued(FREE_NUM[num - 3]);
            }
        }
    }

    private static BigDecimal getOdd(int symbol,int num) {
        List<PayTable> payTables = PAY_TABLE_CONFIG.getPayTables();
        for (PayTable payTable : payTables) {
            if (payTable.getType() == symbol) {
                Map<String, BigDecimal> multiplierMap = payTable.getMultiplierMap();
                return multiplierMap.get(String.valueOf(num));
            }
        }
        return BigDecimal.ZERO;
    }

    private static void settlementMain(Outcome outcome, double stake) {
        List<WinLine> winLines = getWinLine(outcome, stake, true);
        double totalWins = 0;
        for (int i = 0; i < winLines.size(); i++) {
            WinLine winLine = winLines.get(0);
            List<Object> win = new ArrayList<>();
            win.add("ways");
            win.add(winLine.getPayout());
            win.add(winLine.getWinPositions());
            totalWins += winLine.getPayout().doubleValue();
            outcome.getWins().add(win);
        }
        outcome.setWin(DecimalUtil.getBigDecimal2(totalWins));
    }

    private static void generateMainOutcome(Outcome outcome, double stake, double v, boolean bonus_buy) {
        List<List<String>> reelTable = outcome.getScreen();
        List<int[]> usePoint = usePoint(reelTable,-1);
        setScatter(outcome,usePoint,bonus_buy);
        setWild(outcome);
        setOtherSymbols(outcome);
        setKeySymbols(outcome,stake,v);
        settlementMain(outcome,stake);
    }

    private static List<List<int[]>> mainUsePoints(List<List<String>> reelTable) {
        List<List<int[]>> listList = new ArrayList<>();
        wc:for (int i = 0; i < 3; i++) {
            List<int[]> list = new ArrayList<>();
            for (int i1 = 0; i1 < reelTable.get(i).size(); i1++) {
                if (Objects.equals(reelTable.get(i).get(i1), "-1")) {
                    list.add(new int[] {i,i1});
                } else {
                    continue wc;
                }
            }
            Collections.shuffle(list);
            listList.add(list);
        }
        Collections.shuffle(listList);
        return listList;
    }

    private static void generateFreeGameOutcome(Outcome outcome, double stake, double v,boolean last,String featureSymbol,boolean doubleMultiplier) {
        List<List<String>> reelTable = outcome.getScreen();
        List<int[]> previousStickySymbols = outcome.getStorage().getPrevious_sticky_symbols();
        if (previousStickySymbols != null) {
            previousStickySymbols.forEach(p -> {
                reelTable.get(p[0]).set(p[1],featureSymbol);
            });
        }
        List<int[]> usePoint = usePoint(reelTable,-1);
        int max = usePoint.size();
        int num = freeMultiplierNum(v);
        num = Math.min(max,num);
        num = Math.min(num,5 - outcome.getStorage().getAccumulated_bonus_multipliers().size());
        if (num > 0) {
            for (int i = 0; i < num; i++) {
                int[] position = usePoint.remove(0);
                int multiplier = getMultiplier();
                outcome.getStorage().getAccumulated_bonus_multipliers().add(multiplier);
                outcome.getStorage().getBonus_multipliers().add(new int[] {position[0],position[1],multiplier});
                reelTable.get(position[0]).set(position[1],String.valueOf(MULTIPLIER.getIndex()));
            }
        }
        num = Math.min(usePoint.size(),usedNum());
        if (num > 0) {
            last = false;
            for (int i = 0; i < num; i++) {
                int[] position = usePoint.remove(0);
                reelTable.get(position[0]).set(position[1],String.valueOf(BONUS.getIndex()));
            }
            outcome.setFreespins_issued(num);
        }
        num = Math.min(usePoint.size(),freeSymbolNum(v));
        if (num > 0) {
            List<List<int[]>> mainUsePoints = mainUsePoints(reelTable);
            if (!mainUsePoints.isEmpty() && RandomUtil.nextDouble() <= 0.5 * Math.pow(v,2)) {
                List<int[]> remove = mainUsePoints.remove(0);
                int[] position = remove.remove(0);
                reelTable.get(position[0]).set(position[1],featureSymbol);
                outcome.getStorage().getSticky_symbols().add(position);
                num --;
            }
            if (num> 0) {
                usePoint = usePoint(reelTable,-1);
                for (int i = 0; i < num; i++) {
                    int[] position = usePoint.remove(0);
                    reelTable.get(position[0]).set(position[1],featureSymbol);
                    outcome.getStorage().getSticky_symbols().add(position);
                }
            }
        }
        for (List<String> integers : reelTable) {
            for (int i1 = 0; i1 < integers.size(); i1++) {
                if (Objects.equals(integers.get(i1), "-1")) {
                    integers.set(i1, "0");
                }
            }
        }
        if (last) {
            if (outcome.getStorage().getAccumulated_bonus_multipliers().size() == 5 && doubleMultiplier) {
                outcome.getStorage().getAccumulated_bonus_multipliers().addAll(outcome.getStorage().getAccumulated_bonus_multipliers());
            }
            List<Integer> accumulatedBonusMultipliers = outcome.getStorage().getAccumulated_bonus_multipliers();
            List<int[]> finalBonusMultipliers = new ArrayList<>();
            outcome.getStorage().setFinal_bonus_multipliers(finalBonusMultipliers);
            if (accumulatedBonusMultipliers.size() >= 5) {
                List<int[]> useSymbols = new ArrayList<>(outcome.getStorage().getSticky_symbols());
                Collections.shuffle(useSymbols);
                int doubleMultiplierNum = Math.min(accumulatedBonusMultipliers.size(),useSymbols.size());
                for (int i = 0; i < doubleMultiplierNum; i++) {
                    int[] position = useSymbols.remove(0);
                    finalBonusMultipliers.add(new int[]{position[0],position[1],accumulatedBonusMultipliers.get(i)});
                }
            }
            settlementMain(outcome,stake);
        }

    }


    public static List<ApiClientResult> generateApiResult(Player player,double stake, double v,boolean bonus_buy,int purchased_feature_level,String pOrder,double realStake) {
        boolean isFree = false;
        int freeNum = 0;
        int totalFreeNum = 0;
        int round_num = 1;
        String featureSymbol = "0";
        List<Integer> accumulated_bonus_multipliers = new ArrayList<>();
        List<int[]> stick_symbol = new ArrayList<>();

        List<ApiClientResult> apiClientResults = new ArrayList<>();
        do {

            ApiClientResult clientResult = new ApiClientResult();
            apiClientResults.add(clientResult);
            Outcome outcome = initOutCome(stake,isFree,accumulated_bonus_multipliers,stick_symbol);
            clientResult.setOutcome(outcome);
            Flow flow = new Flow();
            clientResult.setFlow(flow);
            if (isFree) {
                double tmpV = v;
                if (purchased_feature_level == 0) {
                    tmpV *= 1;
                }
                if (purchased_feature_level == 1) {
                    tmpV *= 0.93;
                }
                if (purchased_feature_level == 2) {
                    tmpV *= 0.92;
                }
                generateFreeGameOutcome(outcome,stake,tmpV,freeNum == 1,featureSymbol,purchased_feature_level == 2);
                totalFreeNum += clientResult.getOutcome().getFreespins_issued();
                freeNum += clientResult.getOutcome().getFreespins_issued();
                freeNum--;
                flow.setCommand("freespin");
                if (freeNum > 0) {
                    flow.setState("freespins");
                    flow.setAvailable_actions(new String[] {"init","freespin"});
                }
                if (clientResult.getOutcome().getStorage().getAccumulated_bonus_multipliers() != null) {
                    accumulated_bonus_multipliers = new ArrayList<>(clientResult.getOutcome().getStorage().getAccumulated_bonus_multipliers());
                }
                if (clientResult.getOutcome().getStorage().getSticky_symbols() != null) {
                    stick_symbol = new ArrayList<>(clientResult.getOutcome().getStorage().getSticky_symbols());
                }
            } else {
                ;
                generateMainOutcome(outcome,stake,v * 0.9,bonus_buy);
                if (outcome.getFreespins_issued() > 0) {
                    totalFreeNum += clientResult.getOutcome().getFreespins_issued();
                    freeNum += clientResult.getOutcome().getFreespins_issued();
                    if (purchased_feature_level == 0) {
                        featureSymbol = String.valueOf(randomNormalSymbol(null).getIndex());
                    } else {
                        featureSymbol = String.valueOf(FEATURE_SYMBOL[RandomUtil.nextInt(FEATURE_SYMBOL.length)]);
                    }
                    isFree = true;
                    flow.setState("freespins");
                    flow.setAvailable_actions(new String[] {"init","freespin"});
                }
            }

            if (totalFreeNum > 0) {
                JSONObject features = new JSONObject();
                features.put("freespins_issued",totalFreeNum);
                features.put("freespins_left",freeNum);
                features.put("feature_symbol",String.valueOf(featureSymbol));
                clientResult.setFeatures(features);
                if (freeNum == 0) {
                    flow.setAvailable_actions(new String[] {"init","spin"});
                }
            }
            if (bonus_buy) {
                flow.getPurchased_feature().put("name","bonus_buy");
                flow.getPurchased_feature().put("level",String.valueOf(purchased_feature_level));
            }
            flow.setRound_id(pOrder).setLast_action_id(pOrder + "_" + round_num);
            round_num++;
            clientResult.setBalance(new Balance(DecimalUtil.getBigDecimal2(clientResult.getOutcome().getWin().doubleValue()),DecimalUtil.getBigDecimal2((player.getUser().getScore()  - realStake) * ChickenRushContext.SUB_UNITS)));
        } while (freeNum > 0);
        player.setEFreeNum(totalFreeNum);
        player.getExtendJson().put("apiClient",apiClientResults);
        return apiClientResults;
    }

    public static RoundDetailDto generateRoundDetail(List<ApiClientResult> clientResults, double beforeScore, Player player, double betScore) {

        AtomicReference<Double> totalWin = new AtomicReference<>((double) 0);
        clientResults.forEach(c -> totalWin.updateAndGet(v -> v + c.getOutcome().getWin().doubleValue()));
        BigDecimal realBet = DecimalUtil.getBigDecimal2(betScore);
        String usedFeature = "No";
        if (clientResults.get(0).getFlow().getPurchased_feature() != null) {
            usedFeature =  "bonus_buy";
        }
        String betTextBuy = realBet.toPlainString();

        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(TimeUtil.getLocaleString() + " UTC+00:00");

        BigDecimal realWin = DecimalUtil.getBigDecimal2(totalWin.get() / SUB_UNITS);
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(totalWin.get() / SUB_UNITS - betScore);
        roundDetailDto.setBetText(DecimalUtil.getBigDecimal2(player.getEBetScore() / SUB_UNITS).toPlainString());
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

    public static JSONObject getFeatureOptions() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("feature_multipliers",JSONObject.parseObject("{\"base_bet\":20,\"bonus_buy\":{\"0\":1000,\"1\":2000,\"2\":6000}}"));
        jsonObject.put("disabled_features",new ArrayList<>());
        return jsonObject;
    }


    public static String getBonusType(JSONObject object) {
        if (object == null) {
            return "NORMAL";
        }
        int level = object.getInteger("level");
        if (level == 0) {
            return "BRONZE";
        } else if(level == 1) {
            return "SILVER";
        } else {
            return "GOLD";
        }
    }

    public static String getNewFeatureSymbols(ApiClientResult apiClientResult) {
        List<int[]> stickySymbols = apiClientResult.getOutcome().getStorage().getSticky_symbols();
        List<int[]> previousStickySymbols = apiClientResult.getOutcome().getStorage().getPrevious_sticky_symbols();
        List<int[]> temp = new ArrayList<>();
        stickySymbols.forEach(s -> {
            if (previousStickySymbols.stream().noneMatch(p -> p[0] == s[0] && p[1] == s[1])) {
                temp.add(new int[]{s[0] + 1,s[1] + 1});
            }
        });
        return "New feature symbols:" + JSONArray.toJSONString(temp);
    }

    public static boolean isNewFeatureSymbol(ApiClientResult apiClientResult,int row,int col) {
        List<int[]> previousStickySymbols = apiClientResult.getOutcome().getStorage().getPrevious_sticky_symbols();
        if (previousStickySymbols != null) {
            return previousStickySymbols.stream().noneMatch(p -> p[0] == row && p[1] == col);
        }
        return  false;
    }

    public boolean hasNewFeatureSymbol(ApiClientResult apiClientResult) {
        if (apiClientResult.getOutcome().getStorage().getSticky_symbols() != null && apiClientResult.getOutcome().getStorage().getPrevious_sticky_symbols() != null) {
            return apiClientResult.getOutcome().getStorage().getSticky_symbols().size() != apiClientResult.getOutcome().getStorage().getPrevious_sticky_symbols().size();
        }
        return false;
    }

    public String accunmulatedBonuseMultipliers(List<Integer> multipliers) {
        if (multipliers == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < multipliers.size(); i++) {
            stringBuilder.append(multipliers.get(i));
            if (i < multipliers.size() - 1) {
                stringBuilder.append(",");
            }
        }
        return "Accumulated bonus multipliers: " + stringBuilder.toString();
    }

    public static String winWays(List<Object> objects) {
        //: (Ways count formula)[5 * 4 * 4 * 1 * 5]: (Ways count)[400] * (Base bet count)[3.0] * (Payment per way(length=5))[0.06] = 72
        double win = DecimalUtil.getBigDecimal2((1.0 * (Integer)objects.get(1)) / SUB_UNITS).doubleValue();
        List<List<Integer>>  list = (List<List<Integer>>) objects.get(2);
        int length = list.size();
        int num = 1;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        for (int i = 0; i < length; i++) {
            int x = list.get(i).size();
            num *= x;
            stringBuilder.append(x);
            if (i < length - 1) {
                stringBuilder.append("*");
            } else {
                stringBuilder.append("]");
            }
        }
        double pay = DecimalUtil.getBigDecimal2(win / num / 3).doubleValue();
        return ": (Ways count formula)" + stringBuilder.toString() + "(Ways count)[" + num + "] * (Base bet count)[3.0] * (Payment per way(length="
                + length + "))[" + pay + "] = " + win;
    }

    public static String dealScore(double score) {
        return DecimalUtil.getBigDecimal2(score).toPlainString();
    }


}
