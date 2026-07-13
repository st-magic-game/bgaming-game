package com.bgaming.aztecclusters.utils;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.aztecclusters.config.LotteryConfig;
import com.bgaming.aztecclusters.entity.PrizeIcon;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.RandomUtil;

import java.util.*;

import static com.bgaming.aztecclusters.config.LotteryConfig.*;

public class FloodFillPrizeChecker {
    private static final int[][] NEIGHBORS = buildNeighbors();
    private static final int SIZE = COLUMNS * ROWS;
    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public static void main(String[] args) {
        int[][] scene = {
                {2, 7, 7, 5, 7, 7},
                {5, 7, 7, 7, 2, 2},
                {1, 6, 7, 1, 7, 7},
                {1, 7, 2, 5, 7, 2},
                {5, 2, 7, 1, 6, 6},
                {1, 3, 2, 3, 3, 3},
                {6, 3, 3, 2, 5, 7},
                {2, 1, 6, 6, 4, 1},
        };
        int[][] rotary = {
                {-1, -1, -1, -1, -1, -1},
                {-1, 0, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, 8, -1, -1, -1},
                {-1, -1, -1, 0, -1, 0},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
        };
        List<PrizeIcon> prizeIcons = checkPrizeIcon(scene, 0.5);
        System.out.println(JSONObject.toJSONString(prizeIcons));
//        List<int[]> pick = pick(rotary, 38);
//        System.out.println(JSONObject.toJSONString(pick));
//        System.out.println(pick.size());
    }

    public static List<PrizeIcon> checkPrizeIcon(int[][] scene, double betScore) {
        FloodContext ctx = new FloodContext();
        Arrays.fill(ctx.processed, false);
        ctx.visitId = 1;
        List<PrizeIcon> prizes = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                int icon = scene[y][x];
                if (icon == WILD || icon == SCATTER) {
                    continue;
                }
                int start = toIndex(x, y);
                if (ctx.processed[start]) {
                    continue;
                }
                ctx.visitId++;
                int count = flood(scene, icon, start, ctx);

                // 标记普通图标
                for (int i = 0; i < count; i++) {
                    int idx = ctx.resultBuffer[i];
                    int x1 = idx % COLUMNS;
                    int y1 = idx / COLUMNS;
                    if (scene[y1][x1] == icon) {
                        ctx.processed[idx] = true;
                    }
                }
                if (count < 5) {
                    continue;
                }
                PrizeIcon prize = buildPrize(icon, count, betScore, ctx);
                prizes.add(prize);
            }
        }
        return prizes;
    }

    private static PrizeIcon buildPrize(int icon, int count, double betScore, FloodContext ctx) {
        PrizeIcon prize = new PrizeIcon();
        prize.setIcon(icon);
        prize.setLine(count);
        int mul = LotteryConfig.getMul(icon, count);
        prize.setMul(mul);
        prize.setGold(DecimalUtil.getBigDecimal2(mul * 1.0d / BASE_LINE * betScore));
        HashSet<Integer> indexes = new HashSet<>(count * 2);
        for (int i = 0; i < count; i++) {
            indexes.add(ctx.resultBuffer[i]);
        }
        prize.setPrizeIndex(indexes);
        return prize;
    }

    private static int[][] buildNeighbors() {
        int[][] neighbors = new int[SIZE][];
        for (int x = 0; x < COLUMNS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int count = 0;
                if (x > 0) count++;
                if (x < COLUMNS - 1) count++;
                if (y > 0) count++;
                if (y < ROWS - 1) count++;
                int[] arr = new int[count];
                int p = 0;
                if (x > 0)
                    arr[p++] = toIndex(x - 1, y);
                if (x < COLUMNS - 1)
                    arr[p++] = toIndex(x + 1, y);
                if (y > 0)
                    arr[p++] = toIndex(x, y - 1);
                if (y < ROWS - 1)
                    arr[p] = toIndex(x, y + 1);
                neighbors[toIndex(x, y)] = arr;
            }
        }
        return neighbors;
    }

    private static int toIndex(int x, int y) {
        return y * COLUMNS + x;
    }

    private static int flood(int[][] scene, int icon, int start, FloodContext ctx) {
        int head = 0;
        int tail = 0;
        int count = 0;
        ctx.queue[tail++] = start;
        ctx.visit[start] = ctx.visitId;
        while (head < tail) {
            int cur = ctx.queue[head++];
            ctx.resultBuffer[count++] = cur;
            int[] neighbors = NEIGHBORS[cur];
            for (int next : neighbors) {
                if (ctx.visit[next] == ctx.visitId) {
                    continue;
                }
                int x = next % COLUMNS;
                int y = next / COLUMNS;
                int value = scene[y][x];
                if (value != icon && value != WILD) {
                    continue;
                }
                ctx.visit[next] = ctx.visitId;
                ctx.queue[tail++] = next;
            }
        }
        return count;
    }

    /**
     * [y,x]
     */
    public static List<int[]> pick(int[][] rotary, int count) {
        List<int[]> result = new ArrayList<>();
        if (count <= 0) return result;
        boolean[][] visited = new boolean[ROWS][COLUMNS];
        int sx, sy;
        int tries = 0;
        do {
            sx = RandomUtil.nextInt(ROWS);
            sy = RandomUtil.nextInt(COLUMNS);
            tries++;
            if (tries > 100) return result; // 防死循环
        } while (rotary[sx][sy] != -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        List<int[]> frontier = new ArrayList<>();
        queue.add(new int[]{sx, sy});
        visited[sx][sy] = true;
        result.add(new int[]{sx, sy});
        while (!queue.isEmpty() && result.size() < count) {
            int[] cur = queue.poll();
            int x = cur[0];
            int y = cur[1];
            frontier.clear();
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || nx >= ROWS || ny < 0 || ny >= COLUMNS) continue;
                if (visited[nx][ny]) continue;
                if (rotary[nx][ny] != -1) continue;

                frontier.add(new int[]{nx, ny});
            }
            Collections.shuffle(frontier);
            for (int[] next : frontier) {
                if (result.size() >= count) break;
                int nx = next[0];
                int ny = next[1];
                visited[nx][ny] = true;
                queue.add(next);
                result.add(next);
            }
        }
        return result;
    }
}