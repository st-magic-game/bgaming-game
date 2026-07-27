package com.bgaming.burningchillix.service;

import com.bgaming.burningchillix.config.PayLinesConfig;
import com.bgaming.burningchillix.config.PayTableConfig;
import com.bgaming.burningchillix.config.SymbolConfig;
import com.bgaming.burningchillix.entity.PayLines;
import com.bgaming.burningchillix.entity.PayTable;
import com.bgaming.burningchillix.entity.Symbol;
import com.bgaming.burningchillix.entity.client.Outcome;
import com.bgaming.burningchillix.entity.client.SpecialSymbols;
import com.bgaming.burningchillix.entity.client.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SlotGameService {

    private static final int WILD = 0;
    private static final int SCATTER = 7;

    private static final int ROWS = 4;
    private static final int COLS = 5;
    private static final int MIN_NORMAL_SYMBOL = 1;
    private static final int MAX_NORMAL_SYMBOL = 6;

    /**

     * 因此总 Hit Rate 稳定在 25% 左右，Scatter 中奖率稳定在 2% 左右。
     */
    private static final int SPIN_BAG_SIZE = 100;
    private static final int LINE_WIN_COUNT_PER_BAG = 23;
    private static final int SCATTER_WIN_COUNT_PER_BAG = 2;
    private static final int LOSE_COUNT_PER_BAG = 75;

    /**
     * 其余 98 次非 Scatter 中奖局中：
     * 20 次放 1 个 Scatter，18 次放 2 个 Scatter，60 次不放 Scatter。
     * 再加上 2 次放 3 个 Scatter 的中奖局，整体约 40% 的盘面能看到 Scatter。
     */
    private static final int ONE_SCATTER_COUNT_PER_BAG = 20;
    private static final int TWO_SCATTER_COUNT_PER_BAG = 18;

    private static final double TARGET_HIT_RATE = 0.25D;
    private static final double LINE_WIN_RATE = 0.23D;
    private static final double SCATTER_WIN_RATE = 0.02D;

    /**
     * f 只做轻微 RTP 调节，避免原逻辑对权重放大过度。
     * f = 1 时目标 RTP 为 100%。
     * f 在 0.65 ~ 1.35 时，目标 RTP 大约在 93% ~ 107%。
     */
    private static final double RTP_F_SENSITIVITY = 0.20D;
    private static final double MIN_TARGET_RTP = 0.92D;
    private static final double MAX_TARGET_RTP = 1.08D;

    /** 中奖盘面搜索次数。 */
    private static final int WIN_SCREEN_SEARCH_ATTEMPTS = 120;

    /** 候选盘面与目标倍数差距小于该值时提前结束搜索。 */
    private static final double EARLY_ACCEPT_MULTIPLE_DIFF = 0.025D;

    private final PayLinesConfig payLinesConfig;
    private final PayTableConfig payTableConfig;
    public final SymbolConfig symbolConfig;

    /**
     * 不同购买线数使用各自的开奖签袋，避免 20/40/60/80/100 线互相干扰。
     * 每个签同时决定本局中奖类型与 Scatter 展示数量。
     */
    private final Map<Integer, SpinBag> spinBagMap = new ConcurrentHashMap<>();

    /**
     * 赔率缓存：下标为 [symbol][count]。
     * 候选盘面搜索会频繁计算赔率，不能每次都 stream 查询配置。
     */
    private final BigDecimal[][] multiplierTable = new BigDecimal[SCATTER + 1][COLS + 1];
    private final double[][] multiplierDoubleTable = new double[SCATTER + 1][COLS + 1];

    @Autowired
    public SlotGameService(PayLinesConfig payLinesConfig,
                           PayTableConfig payTableConfig,
                           SymbolConfig symbolConfig) {
        this.payLinesConfig = payLinesConfig;
        this.payTableConfig = payTableConfig;
        this.symbolConfig = symbolConfig;
        initMultiplierCache();
    }

    /**
     * 核心开奖方法。
     *
     * displayBet 是本局总下注；lineCount 是本局购买的中奖线数量。
     * 例如：
     * displayBet = 1，lineCount = 20，则每线下注 = 1 / 20；
     * displayBet = 2，lineCount = 40，则每线下注 = 2 / 40。
     * 两种模式的每线下注相同。
     */
    public Outcome spin(double f, BigDecimal displayBet, int lineCount) {
        validateSpinParams(displayBet, lineCount);

        List<PayLines> activeLines = getActiveLines(lineCount);
        BigDecimal betPerLine = displayBet.divide(
                BigDecimal.valueOf(lineCount),
                8,
                RoundingMode.HALF_UP
        );

        double targetRtp = calculateTargetRtp(f);
        double targetLineWinMultiple = calculateTargetLineWinMultiple(targetRtp);

        SpinPlan spinPlan = spinBagMap
                .computeIfAbsent(lineCount, key -> new SpinBag())
                .next();

        int[][] screen;
        if (spinPlan.resultType == SpinResultType.SCATTER_WIN) {
            screen = generateScatterWinScreen();
        } else if (spinPlan.resultType == SpinResultType.LINE_WIN) {
            screen = generateTargetWinScreen(
                    activeLines,
                    targetLineWinMultiple,
                    spinPlan.scatterCount
            );
        } else {
            screen = generateGuaranteedLoseScreen();
            addScatterSymbols(screen, null, 0, spinPlan.scatterCount, ThreadLocalRandom.current());
        }

        ScreenEvaluation evaluation = evaluateScreen(screen, activeLines, betPerLine, displayBet, true);

        // 理论上不会触发，作为配置异常或未来改动后的保险。
        if (spinPlan.resultType == SpinResultType.SCATTER_WIN
                && countSymbol(screen, SCATTER) < 3) {
            screen = generateScatterWinScreen();
            evaluation = evaluateScreen(screen, activeLines, betPerLine, displayBet, true);
        } else if (spinPlan.resultType == SpinResultType.LINE_WIN
                && evaluation.totalWin.compareTo(BigDecimal.ZERO) <= 0) {
            screen = generateFallbackWinScreen(activeLines, spinPlan.scatterCount);
            evaluation = evaluateScreen(screen, activeLines, betPerLine, displayBet, true);
        } else if (spinPlan.resultType == SpinResultType.LOSE
                && evaluation.totalWin.compareTo(BigDecimal.ZERO) > 0) {
            screen = generateGuaranteedLoseScreen();
            addScatterSymbols(screen, null, 0, spinPlan.scatterCount, ThreadLocalRandom.current());
            evaluation = evaluateScreen(screen, activeLines, betPerLine, displayBet, true);
        }

        Outcome outcome = new Outcome();
        outcome.setBet(displayBet);
        outcome.setScreen(convertScreen(screen));
        outcome.setWin(money(evaluation.totalWin));
        outcome.setWins(evaluation.wins);
        outcome.setSpecial_symbols(buildSpecialSymbols(screen));

        Storage storage = new Storage();
        storage.setMode(lineCount);
        outcome.setStorage(storage);

        return outcome;
    }

    /**
     * 只取配置中 id 最小的前 lineCount 条线。
     * 因此购买 20 条线时，只有 id 0~19 的线参与结算；
     * 购买 40 条线时，只有 id 0~39 的线参与结算。
     */
    private List<PayLines> getActiveLines(int lineCount) {
        List<PayLines> allLines = new ArrayList<>(payLinesConfig.getPayLines());
        allLines.sort(Comparator.comparingInt(PayLines::getId));

        if (lineCount > allLines.size()) {
            throw new IllegalArgumentException(
                    "lineCount=" + lineCount + " 超过配置中奖线数量=" + allLines.size()
            );
        }

        return new ArrayList<>(allLines.subList(0, lineCount));
    }

    /**
     * 生成保证不中奖的盘面。
     *
     * 前 3 个转轴分别使用 3 组互不重复的普通图标。
     * 任意中奖线从左向右取前 3 个转轴时，都不可能出现同一普通图标连续 3 个；
     * 同时该盘面不放 Wild 和 Scatter，因此一定不中奖。
     *
     * 每个转轴仍然只包含 1~2 种普通图标。
     */
    private int[][] generateGuaranteedLoseScreen() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Integer> symbols = new ArrayList<>();
        for (int symbol = MIN_NORMAL_SYMBOL; symbol <= MAX_NORMAL_SYMBOL; symbol++) {
            symbols.add(symbol);
        }
        Collections.shuffle(symbols, new Random(random.nextLong()));

        int[][] screen = new int[COLS][ROWS];

        for (int col = 0; col < COLS; col++) {
            int symbolA;
            int symbolB;

            if (col < 3) {
                symbolA = symbols.get(col * 2);
                symbolB = symbols.get(col * 2 + 1);
            } else {
                symbolA = randomNormalSymbol(-1, random);
                symbolB = randomNormalSymbol(symbolA, random);
            }

            fillSplitColumn(screen[col], symbolA, symbolB, random.nextInt(1, ROWS));
        }

        return screen;
    }

    /**
     * 搜索一个总赔付尽量接近 targetWinMultiple × 总下注的中奖盘面。
     *
     * 目标 RTP 为 100%、Hit 为 25% 时：
     * 每个中奖局平均需要返回约 4 倍总下注。
     */
    private int[][] generateTargetWinScreen(List<PayLines> activeLines,
                                            double targetWinMultiple,
                                            int scatterCount) {
        int[][] bestScreen = null;
        double bestDiff = Double.MAX_VALUE;

        for (int attempt = 0; attempt < WIN_SCREEN_SEARCH_ATTEMPTS; attempt++) {
            int[][] candidate = generateWinningCandidate(
                    activeLines,
                    targetWinMultiple,
                    scatterCount
            );
            double totalOdds = calculateTotalOdds(candidate, activeLines);

            if (totalOdds <= 0D) {
                continue;
            }

            // 总下注 = 每线下注 × lineCount，所以总返奖倍数 = 总赔率 / lineCount。
            double actualMultiple = totalOdds / activeLines.size();
            double diff = Math.abs(actualMultiple - targetWinMultiple);

            if (diff < bestDiff) {
                bestDiff = diff;
                bestScreen = candidate;
            }

            if (diff <= EARLY_ACCEPT_MULTIPLE_DIFF) {
                break;
            }
        }

        return bestScreen != null
                ? bestScreen
                : generateFallbackWinScreen(activeLines, scatterCount);
    }

    /**
     * 生成一个候选中奖盘面。
     * 每个转轴的基础普通图标仍然最多为两种，Scatter 作为特殊覆盖图标单独处理。
     */
    private int[][] generateWinningCandidate(List<PayLines> activeLines,
                                             double targetWinMultiple,
                                             int scatterCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[][] screen = generateGuaranteedLoseScreen();

        PayLines targetLine = activeLines.get(random.nextInt(activeLines.size()));
        List<Integer> targetPositions = targetLine.getPositions();

        int targetSymbol = chooseTargetSymbol(targetWinMultiple, random);
        int matchCount = chooseMatchCount(targetWinMultiple, random);

        // 少量出现 Wild 开头，让 Wild 替代结算逻辑实际参与游戏。
        int wildPrefixCount = 0;
        if (random.nextDouble() < 0.12D) {
            wildPrefixCount = random.nextInt(1, Math.min(3, matchCount) + 1);
        }

        for (int col = 0; col < COLS; col++) {
            int targetRow = targetPositions.get(col);

            if (col < matchCount) {
                int winningSymbol = col < wildPrefixCount ? WILD : targetSymbol;
                int stackSize = chooseStackSize(targetWinMultiple, random);
                int fillerSymbol = randomNormalSymbol(targetSymbol, random);

                screen[col] = createSegmentColumn(
                        winningSymbol,
                        targetRow,
                        stackSize,
                        fillerSymbol,
                        random
                );
            } else {
                // 在 matchCount 后主动打断目标图标，防止连线长度失控。
                int symbolA = randomNormalSymbol(targetSymbol, random);
                int symbolB = randomNormalSymbol(targetSymbol, symbolA, random);
                fillSplitColumn(screen[col], symbolA, symbolB, random.nextInt(1, ROWS));
            }
        }

        addScatterSymbols(
                screen,
                targetPositions,
                matchCount,
                scatterCount,
                random
        );

        return screen;
    }

    /**
     * 最低限度的保底中奖盘面。
     * 使用第一条已购买线制造 3 个 l3，确保至少有一个有效中奖。
     */
    private int[][] generateFallbackWinScreen(List<PayLines> activeLines, int scatterCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[][] screen = generateGuaranteedLoseScreen();
        List<Integer> positions = activeLines.get(0).getPositions();
        int targetSymbol = MAX_NORMAL_SYMBOL;

        for (int col = 0; col < 3; col++) {
            int targetRow = positions.get(col);
            int filler = randomNormalSymbol(targetSymbol, random);
            screen[col] = createSegmentColumn(targetSymbol, targetRow, 1, filler, random);
        }

        addScatterSymbols(screen, positions, 3, scatterCount, random);
        return screen;
    }

    /**
     * 生成只包含 Scatter 奖的盘面。
     * 基础盘面本身保证不产生连线奖，再放入 3 个不同转轴上的 Scatter。
     */
    private int[][] generateScatterWinScreen() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[][] screen = generateGuaranteedLoseScreen();
        addScatterSymbols(screen, null, 0, 3, random);
        return screen;
    }

    /**
     * 结算完整盘面。
     */
    private ScreenEvaluation evaluateScreen(int[][] screen,
                                            List<PayLines> activeLines,
                                            BigDecimal betPerLine,
                                            BigDecimal totalBet,
                                            boolean buildWinDetails) {
        BigDecimal totalWin = BigDecimal.ZERO;
        List<List<Object>> wins = new ArrayList<>();

        int scatterCount = countSymbol(screen, SCATTER);
        if (scatterCount >= 3) {
            int payCount = Math.min(scatterCount, 5);
            BigDecimal scatterOdds = getMultiplier(SCATTER, payCount);

            if (scatterOdds.compareTo(BigDecimal.ZERO) > 0) {
                // Scatter 按本局总下注计算，不按单线下注计算。
                BigDecimal scatterWin = money(totalBet.multiply(scatterOdds));
                totalWin = totalWin.add(scatterWin);

                if (buildWinDetails) {
                    List<Object> detail = new ArrayList<>();
                    detail.add("scatter");
                    detail.add(scatterWin);
                    detail.add(collectSymbolPositions(screen, SCATTER));
                    wins.add(detail);
                }
            }
        }

        for (PayLines line : activeLines) {
            validatePayLine(line);

            BestWinResult bestWin = calculateLineBestWin(screen, line.getPositions());
            if (bestWin.odds.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal lineWin = money(betPerLine.multiply(bestWin.odds));
            totalWin = totalWin.add(lineWin);

            if (buildWinDetails) {
                List<Object> detail = new ArrayList<>();
                detail.add("line");
                detail.add(lineWin);
                detail.add(new ArrayList<>(line.getPositions().subList(0, bestWin.count)));
                detail.add(line.getId());
                wins.add(detail);
            }
        }

        return new ScreenEvaluation(money(totalWin), wins);
    }

    /**
     * Wild 最优赔付规则：
     *
     * 示例：[W, W, W, A, A]
     * 1. 先计算 3 个 Wild 自身的赔率；
     * 2. 再计算 Wild 替代 A 后，5 个 A 的赔率；
     * 3. 两者只取金额更高的一项，不重复派奖。
     *
     * 若赔率相同，优先取连线长度更长的一项。
     */
    private BestWinResult calculateLineBestWin(int[][] screen, List<Integer> positions) {
        int leadingWildCount = 0;
        while (leadingWildCount < COLS
                && screen[leadingWildCount][positions.get(leadingWildCount)] == WILD) {
            leadingWildCount++;
        }

        BestWinResult wildResult = BestWinResult.empty();
        if (leadingWildCount >= 3) {
            wildResult = new BestWinResult(
                    WILD,
                    leadingWildCount,
                    getMultiplier(WILD, leadingWildCount)
            );
        }

        // 第一个非 Wild 图标决定 Wild 可以替代成哪一种普通图标。
        int targetSymbol = -1;
        if (leadingWildCount < COLS) {
            int firstNonWild = screen[leadingWildCount][positions.get(leadingWildCount)];
            if (isNormalSymbol(firstNonWild)) {
                targetSymbol = firstNonWild;
            }
        }

        BestWinResult substituteResult = BestWinResult.empty();
        if (targetSymbol != -1) {
            int substituteCount = 0;
            for (int col = 0; col < COLS; col++) {
                int symbol = screen[col][positions.get(col)];
                if (symbol == WILD || symbol == targetSymbol) {
                    substituteCount++;
                } else {
                    break;
                }
            }

            if (substituteCount >= 3) {
                substituteResult = new BestWinResult(
                        targetSymbol,
                        substituteCount,
                        getMultiplier(targetSymbol, substituteCount)
                );
            }
        }

        int compare = substituteResult.odds.compareTo(wildResult.odds);
        if (compare > 0) {
            return substituteResult;
        }
        if (compare < 0) {
            return wildResult;
        }

        // 金额相同时优先展示更长的连线；长度也相同时优先普通图标替代结果。
        if (substituteResult.count >= wildResult.count) {
            return substituteResult;
        }
        return wildResult;
    }

    /** 候选盘面搜索使用的快速总赔率计算，不创建中奖详情，也不进行金额运算。 */
    private double calculateTotalOdds(int[][] screen, List<PayLines> activeLines) {
        double totalOdds = 0D;

        int scatterCount = countSymbol(screen, SCATTER);
        if (scatterCount >= 3) {
            totalOdds += getMultiplierDouble(SCATTER, Math.min(scatterCount, 5));
        }

        for (PayLines line : activeLines) {
            totalOdds += calculateLineBestOdds(screen, line.getPositions());
        }

        return totalOdds;
    }

    /** Wild 与替代图标二选一的快速赔率版本。 */
    private double calculateLineBestOdds(int[][] screen, List<Integer> positions) {
        int leadingWildCount = 0;
        while (leadingWildCount < COLS
                && screen[leadingWildCount][positions.get(leadingWildCount)] == WILD) {
            leadingWildCount++;
        }

        double wildOdds = leadingWildCount >= 3
                ? getMultiplierDouble(WILD, leadingWildCount)
                : 0D;

        if (leadingWildCount >= COLS) {
            return wildOdds;
        }

        int targetSymbol = screen[leadingWildCount][positions.get(leadingWildCount)];
        if (!isNormalSymbol(targetSymbol)) {
            return wildOdds;
        }

        int substituteCount = 0;
        for (int col = 0; col < COLS; col++) {
            int symbol = screen[col][positions.get(col)];
            if (symbol == WILD || symbol == targetSymbol) {
                substituteCount++;
            } else {
                break;
            }
        }

        double substituteOdds = substituteCount >= 3
                ? getMultiplierDouble(targetSymbol, substituteCount)
                : 0D;

        return Math.max(wildOdds, substituteOdds);
    }

    private SpecialSymbols buildSpecialSymbols(int[][] screen) {
        Map<String, List<int[]>> scatterMap = new HashMap<>();
        Map<String, List<int[]>> wildMap = new HashMap<>();

        List<int[]> scatterPositions = collectSymbolPositions(screen, SCATTER);
        List<int[]> wildPositions = collectSymbolPositions(screen, WILD);

        if (!scatterPositions.isEmpty()) {
            scatterMap.put(String.valueOf(SCATTER), scatterPositions);
        }
        if (!wildPositions.isEmpty()) {
            wildMap.put(String.valueOf(WILD), wildPositions);
        }

        return new SpecialSymbols(scatterMap, wildMap);
    }

    /** 按照 [转轴下标, 行下标] 收集特殊图标坐标。 */
    private List<int[]> collectSymbolPositions(int[][] screen, int targetSymbol) {
        List<int[]> positions = new ArrayList<>();

        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                if (screen[col][row] == targetSymbol) {
                    positions.add(new int[]{col, row});
                }
            }
        }

        return positions;
    }

    private List<List<String>> convertScreen(int[][] screen) {
        List<List<String>> result = new ArrayList<>();

        for (int col = 0; col < COLS; col++) {
            List<String> column = new ArrayList<>();
            for (int row = 0; row < ROWS; row++) {
                column.add(String.valueOf(screen[col][row]));
            }
            result.add(column);
        }

        return result;
    }

    /**
     * 在不同转轴上放置指定数量的 Scatter。
     * protectedPositions 的前 protectedColumnCount 个位置不会被覆盖，
     * 用于保证普通连线中奖不会被 Scatter 打断。
     *
     * 基础转轴最多只有两种普通图标，因此当某个转轴出现第三种图标时，
     * 第三种图标一定是 Scatter。
     */
    private void addScatterSymbols(int[][] screen,
                                   List<Integer> protectedPositions,
                                   int protectedColumnCount,
                                   int scatterCount,
                                   ThreadLocalRandom random) {
        int safeCount = Math.max(0, Math.min(scatterCount, COLS));
        if (safeCount == 0) {
            return;
        }

        List<Integer> columns = new ArrayList<>();
        for (int col = 0; col < COLS; col++) {
            columns.add(col);
        }
        Collections.shuffle(columns, new Random(random.nextLong()));

        for (int i = 0; i < safeCount; i++) {
            int col = columns.get(i);
            int protectedRow = -1;

            if (protectedPositions != null
                    && col < protectedColumnCount
                    && col < protectedPositions.size()) {
                protectedRow = protectedPositions.get(col);
            }

            int row = random.nextInt(ROWS);
            while (row == protectedRow) {
                row = random.nextInt(ROWS);
            }

            screen[col][row] = SCATTER;
        }
    }

    private int[] createSegmentColumn(int winningSymbol,
                                      int targetRow,
                                      int requestedStackSize,
                                      int fillerSymbol,
                                      ThreadLocalRandom random) {
        int stackSize = Math.max(1, Math.min(ROWS, requestedStackSize));

        boolean canUseTop = targetRow < stackSize;
        boolean canUseBottom = targetRow >= ROWS - stackSize;

        boolean useTop;
        int splitPoint;

        if (canUseTop && canUseBottom) {
            useTop = random.nextBoolean();
        } else if (canUseTop) {
            useTop = true;
        } else if (canUseBottom) {
            useTop = false;
        } else {
            // 请求的堆叠长度无法覆盖该行时，自动使用距离最近的边缘连续区间。
            int topSize = targetRow + 1;
            int bottomSize = ROWS - targetRow;
            useTop = topSize <= bottomSize;
            stackSize = useTop ? topSize : bottomSize;
        }

        splitPoint = useTop ? stackSize : ROWS - stackSize;

        int[] column = new int[ROWS];
        for (int row = 0; row < ROWS; row++) {
            if (useTop) {
                column[row] = row < splitPoint ? winningSymbol : fillerSymbol;
            } else {
                column[row] = row < splitPoint ? fillerSymbol : winningSymbol;
            }
        }

        return column;
    }

    private void fillSplitColumn(int[] column, int symbolA, int symbolB, int splitPoint) {
        for (int row = 0; row < ROWS; row++) {
            column[row] = row < splitPoint ? symbolA : symbolB;
        }
    }

    private int chooseTargetSymbol(double targetMultiple, ThreadLocalRandom random) {
        if (targetMultiple >= 6D) {
            return weightedChoice(
                    new int[]{1, 2, 3, 4, 5, 6},
                    new int[]{10, 10, 10, 5, 4, 2},
                    random
            );
        }

        if (targetMultiple >= 2.5D) {
            return weightedChoice(
                    new int[]{1, 2, 3, 4, 5, 6},
                    new int[]{5, 6, 6, 9, 10, 8},
                    random
            );
        }

        return weightedChoice(
                new int[]{4, 5, 6},
                new int[]{3, 4, 7},
                random
        );
    }

    private int chooseMatchCount(double targetMultiple, ThreadLocalRandom random) {
        double value = random.nextDouble();

        if (targetMultiple >= 6D) {
            return value < 0.35D ? 4 : 5;
        }

        if (targetMultiple >= 2.5D) {
            if (value < 0.20D) {
                return 3;
            }
            if (value < 0.65D) {
                return 4;
            }
            return 5;
        }

        return value < 0.80D ? 3 : 4;
    }

    private int chooseStackSize(double targetMultiple, ThreadLocalRandom random) {
        double value = random.nextDouble();

        if (targetMultiple >= 5D) {
            if (value < 0.05D) return 1;
            if (value < 0.30D) return 2;
            if (value < 0.75D) return 3;
            return 4;
        }

        if (value < 0.20D) return 1;
        if (value < 0.65D) return 2;
        if (value < 0.93D) return 3;
        return 4;
    }

    private int randomNormalSymbol(int excludedSymbol, ThreadLocalRandom random) {
        return randomNormalSymbol(excludedSymbol, -1, random);
    }

    private int randomNormalSymbol(int excludedSymbolA,
                                   int excludedSymbolB,
                                   ThreadLocalRandom random) {
        List<Symbol> configuredSymbols = symbolConfig.getSymbols();

        double totalWeight = 0D;
        for (Symbol symbol : configuredSymbols) {
            int index = symbol.getIndex();
            if (isNormalSymbol(index)
                    && index != excludedSymbolA
                    && index != excludedSymbolB
                    && symbol.getWeight() > 0D) {
                totalWeight += symbol.getWeight();
            }
        }

        if (totalWeight > 0D) {
            double value = random.nextDouble(totalWeight);
            double current = 0D;

            for (Symbol symbol : configuredSymbols) {
                int index = symbol.getIndex();
                if (!isNormalSymbol(index)
                        || index == excludedSymbolA
                        || index == excludedSymbolB
                        || symbol.getWeight() <= 0D) {
                    continue;
                }

                current += symbol.getWeight();
                if (value <= current) {
                    return index;
                }
            }
        }

        // 配置为空或权重异常时的兜底。
        int result;
        do {
            result = random.nextInt(MIN_NORMAL_SYMBOL, MAX_NORMAL_SYMBOL + 1);
        } while (result == excludedSymbolA || result == excludedSymbolB);
        return result;
    }

    private int weightedChoice(int[] values, int[] weights, ThreadLocalRandom random) {
        int totalWeight = 0;
        for (int weight : weights) {
            totalWeight += weight;
        }

        int value = random.nextInt(totalWeight);
        int current = 0;
        for (int i = 0; i < values.length; i++) {
            current += weights[i];
            if (value < current) {
                return values[i];
            }
        }

        return values[values.length - 1];
    }

    private int countSymbol(int[][] screen, int targetSymbol) {
        int count = 0;
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                if (screen[col][row] == targetSymbol) {
                    count++;
                }
            }
        }
        return count;
    }

    private double calculateTargetRtp(double f) {
        double safeF = Double.isFinite(f) ? f : 1D;
        safeF = clamp(safeF, 0.65D, 1.35D);

        double targetRtp = 1D + (safeF - 1D) * RTP_F_SENSITIVITY;
        return clamp(targetRtp, MIN_TARGET_RTP, MAX_TARGET_RTP);
    }

    /**
     * Scatter 固定占约 2% 的开奖局，因此普通连线奖需要扣除 Scatter 对 RTP 的贡献。
     * Scatter 按总下注结算，所以它的总下注返奖倍数就是配置赔率本身。
     */
    private double calculateTargetLineWinMultiple(double targetRtp) {
        double scatterWinMultiple = getMultiplierDouble(SCATTER, 3);
        double remainingRtp = targetRtp - SCATTER_WIN_RATE * scatterWinMultiple;

        if (remainingRtp <= 0D || LINE_WIN_RATE <= 0D) {
            return targetRtp / TARGET_HIT_RATE;
        }

        return remainingRtp / LINE_WIN_RATE;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isNormalSymbol(int symbol) {
        return symbol >= MIN_NORMAL_SYMBOL && symbol <= MAX_NORMAL_SYMBOL;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getMultiplier(int symbolType, int count) {
        if (symbolType < 0 || symbolType >= multiplierTable.length
                || count < 0 || count >= multiplierTable[symbolType].length) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = multiplierTable[symbolType][count];
        return value == null ? BigDecimal.ZERO : value;
    }

    private double getMultiplierDouble(int symbolType, int count) {
        if (symbolType < 0 || symbolType >= multiplierDoubleTable.length
                || count < 0 || count >= multiplierDoubleTable[symbolType].length) {
            return 0D;
        }
        return multiplierDoubleTable[symbolType][count];
    }

    private void initMultiplierCache() {
        for (int symbol = 0; symbol < multiplierTable.length; symbol++) {
            for (int count = 0; count < multiplierTable[symbol].length; count++) {
                multiplierTable[symbol][count] = BigDecimal.ZERO;
                multiplierDoubleTable[symbol][count] = 0D;
            }
        }

        for (PayTable payTable : payTableConfig.getPayTables()) {
            int symbolType = payTable.getType();
            if (symbolType < 0 || symbolType >= multiplierTable.length
                    || payTable.getMultiplierMap() == null) {
                continue;
            }

            for (Map.Entry<String, BigDecimal> entry : payTable.getMultiplierMap().entrySet()) {
                int count;
                try {
                    count = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException ignored) {
                    continue;
                }

                if (count < 0 || count >= multiplierTable[symbolType].length
                        || entry.getValue() == null) {
                    continue;
                }

                multiplierTable[symbolType][count] = entry.getValue();
                multiplierDoubleTable[symbolType][count] = entry.getValue().doubleValue();
            }
        }
    }

    private void validateSpinParams(BigDecimal displayBet, int lineCount) {
        if (displayBet == null || displayBet.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("displayBet 必须大于 0");
        }
        if (lineCount <= 0) {
            throw new IllegalArgumentException("lineCount 必须大于 0");
        }
        if (payLinesConfig.getPayLines() == null || payLinesConfig.getPayLines().isEmpty()) {
            throw new IllegalStateException("payLines 配置为空");
        }
    }

    private void validatePayLine(PayLines line) {
        if (line.getPositions() == null || line.getPositions().size() != COLS) {
            throw new IllegalStateException("中奖线 " + line.getId() + " 的 positions 数量必须为 " + COLS);
        }

        for (Integer row : line.getPositions()) {
            if (row == null || row < 0 || row >= ROWS) {
                throw new IllegalStateException("中奖线 " + line.getId() + " 存在非法行号: " + row);
            }
        }
    }

    private enum SpinResultType {
        LOSE,
        LINE_WIN,
        SCATTER_WIN
    }

    private static class SpinPlan {
        private final SpinResultType resultType;
        private final int scatterCount;

        private SpinPlan(SpinResultType resultType, int scatterCount) {
            this.resultType = resultType;
            this.scatterCount = scatterCount;
        }
    }

    /**
     * 100 局一袋：
     * - 23 局普通连线中奖；
     * - 2 局 Scatter 中奖；
     * - 75 局不中奖；
     * - 共 40 局至少出现 1 个 Scatter，其中只有 2 局达到 3 个并中奖。
     */
    private static class SpinBag {
        private final List<SpinPlan> values = new ArrayList<>();
        private int index;

        synchronized SpinPlan next() {
            if (index >= values.size()) {
                refill();
            }
            return values.get(index++);
        }

        private void refill() {
            values.clear();

            List<SpinResultType> normalResultTypes = new ArrayList<>();
            for (int i = 0; i < LINE_WIN_COUNT_PER_BAG; i++) {
                normalResultTypes.add(SpinResultType.LINE_WIN);
            }
            for (int i = 0; i < LOSE_COUNT_PER_BAG; i++) {
                normalResultTypes.add(SpinResultType.LOSE);
            }

            List<Integer> normalScatterCounts = new ArrayList<>();
            for (int i = 0; i < ONE_SCATTER_COUNT_PER_BAG; i++) {
                normalScatterCounts.add(1);
            }
            for (int i = 0; i < TWO_SCATTER_COUNT_PER_BAG; i++) {
                normalScatterCounts.add(2);
            }
            while (normalScatterCounts.size() < normalResultTypes.size()) {
                normalScatterCounts.add(0);
            }

            Random shuffleRandom = new Random(ThreadLocalRandom.current().nextLong());
            Collections.shuffle(normalResultTypes, shuffleRandom);
            Collections.shuffle(normalScatterCounts, shuffleRandom);

            for (int i = 0; i < normalResultTypes.size(); i++) {
                values.add(new SpinPlan(normalResultTypes.get(i), normalScatterCounts.get(i)));
            }

            for (int i = 0; i < SCATTER_WIN_COUNT_PER_BAG; i++) {
                values.add(new SpinPlan(SpinResultType.SCATTER_WIN, 3));
            }

            if (values.size() != SPIN_BAG_SIZE) {
                throw new IllegalStateException("SpinBag 配置数量必须等于 " + SPIN_BAG_SIZE);
            }

            Collections.shuffle(values, new Random(ThreadLocalRandom.current().nextLong()));
            index = 0;
        }
    }

    private static class ScreenEvaluation {
        private final BigDecimal totalWin;
        private final List<List<Object>> wins;

        private ScreenEvaluation(BigDecimal totalWin, List<List<Object>> wins) {
            this.totalWin = totalWin;
            this.wins = wins;
        }
    }

    private static class BestWinResult {
        private final int symbol;
        private final int count;
        private final BigDecimal odds;

        private BestWinResult(int symbol, int count, BigDecimal odds) {
            this.symbol = symbol;
            this.count = count;
            this.odds = odds;
        }

        private static BestWinResult empty() {
            return new BestWinResult(-1, 0, BigDecimal.ZERO);
        }
    }
}
