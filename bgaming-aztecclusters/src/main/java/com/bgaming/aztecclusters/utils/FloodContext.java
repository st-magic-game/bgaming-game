package com.bgaming.aztecclusters.utils;

import static com.bgaming.aztecclusters.config.LotteryConfig.COLUMNS;
import static com.bgaming.aztecclusters.config.LotteryConfig.ROWS;

public class FloodContext {
    static final int SIZE = COLUMNS * ROWS;;
    final int[] visit = new int[SIZE];
    final boolean[] processed = new boolean[SIZE];
    final int[] queue = new int[SIZE];
    final int[] resultBuffer = new int[SIZE];
    int visitId;
}