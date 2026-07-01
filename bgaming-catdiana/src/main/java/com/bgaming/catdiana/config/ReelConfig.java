package com.bgaming.catdiana.config;

import com.game.base.common.util.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bgaming.catdiana.config.LotteryConfig.COLUMNS;
import static com.bgaming.catdiana.config.LotteryConfig.ROWS;

public class ReelConfig {
    public static final int GIANT_REEL_INDEX = 2;

    public static final String[][] REELS =
            {
                    {"elvis", "elvis", "elvis", "k", "a", "limo", "q", "k", "guitar", "star", "a", "k", "guitar", "q", "a", "guitar", "k", "limo", "j", "star", "microphone", "j", "girl", "microphone", "k", "j", "q", "microphone", "k", "guitar", "microphone", "k", "a", "microphone", "q", "a", "microphone", "q", "k", "coin", "coin", "coin", "q", "a", "k", "j", "girl", "star", "k", "q", "guitar", "a"},
                    {"elvis", "elvis", "elvis", "limo", "a", "q", "a", "j", "k", "microphone", "q", "j", "k", "a", "q", "j", "a", "q", "j", "k", "a", "microphone", "limo", "j", "microphone", "k", "guitar", "j", "k", "limo", "guitar", "q", "j", "girl", "q", "guitar", "a", "k", "limo", "q", "coin", "coin", "coin", "guitar", "j", "k", "girl", "j", "a", "guitar", "q", "microphone", "a"},
                    {"elvis", "elvis", "elvis", "k", "k", "j", "q", "j", "girl", "k", "j", "star", "girl", "a", "q", "guitar", "k", "a", "q", "limo", "k", "j", "microphone", "k", "j", "q", "k", "guitar", "j", "limo", "q", "j", "girl", "limo", "j", "q", "guitar", "j", "a", "k", "microphone", "coin", "coin", "coin", "j", "guitar", "limo", "j", "star", "q", "microphone", "guitar", "q", "k"},
                    {"elvis", "elvis", "elvis", "k", "j", "q", "a", "microphone", "k", "j", "girl", "microphone", "a", "q", "guitar", "a", "microphone", "q", "limo", "a", "j", "limo", "microphone", "j", "guitar", "girl", "q", "guitar", "microphone", "limo", "k", "girl", "q", "guitar", "limo", "girl", "k", "a", "q", "guitar", "coin", "coin", "a", "j", "k", "a", "guitar", "k", "microphone", "q", "a", "j"},
                    {"elvis", "elvis", "elvis", "girl", "j", "q", "girl", "microphone", "guitar", "a", "j", "star", "q", "a", "guitar", "star", "q", "k", "guitar", "a", "j", "limo", "q", "j", "limo", "microphone", "q", "j", "k", "q", "j", "limo", "q", "j", "microphone", "q", "star", "guitar", "girl", "coin", "coin", "coin", "guitar", "j", "k", "limo", "j", "star", "microphone", "girl", "a", "q"}
            };

    public static final String[][] FREE_REELS =
            {
                    {"star", "a", "microphone", "limo", "coin", "elvis", "elvis", "elvis", "guitar", "j", "q", "j", "a", "k", "j", "a", "q", "microphone", "j", "k", "limo", "guitar", "a", "q", "microphone", "a", "k", "microphone", "a", "q", "limo", "k", "guitar", "a", "q", "limo", "j", "a", "q", "k", "guitar", "a", "q", "girl", "guitar", "a", "q", "limo", "guitar", "j", "q", "guitar", "limo", "q", "guitar", "a", "q", "k", "guitar", "a", "j", "limo", "a", "guitar", "microphone", "j"},
                    {"elvis"},
                    {"coin", "elvis", "girl", "q", "k", "a", "j", "guitar", "q", "microphone", "a", "q", "k", "j", "a", "k", "limo", "guitar", "k", "j", "j", "k", "j", "limo", "microphone", "k", "j", "limo", "k", "a", "guitar", "q", "a", "q", "k", "j", "star", "k", "j", "q", "k", "j", "q", "k", "j", "q", "k", "j", "q", "microphone", "j", "q", "k", "microphone", "q", "a", "j", "q", "a", "girl", "q", "k", "j", "guitar", "a"},
                    {"elvis"},
                    {"coin", "elvis", "elvis", "elvis", "microphone", "a", "q", "microphone", "k", "guitar", "q", "k", "j", "q", "microphone", "k", "j", "guitar", "q", "a", "guitar", "k", "microphone", "star", "limo", "a", "q", "k", "j", "q", "a", "j", "k", "microphone", "a", "limo", "j", "girl", "k", "a", "j", "q", "a", "girl", "j", "a", "q", "microphone", "guitar", "j", "limo", "q", "k", "j", "a", "q", "a", "j", "a", "microphone", "k", "q", "j", "guitar", "microphone"}
            };


    public static final List<String> ICON_NAME = Arrays.asList("j", "q", "k", "a", "microphone", "guitar", "limo", "girl", "elvis", "star", "coin");

    public static int[][] NORMAL_SPIN_REELS = new int[REELS.length][];

    public static int[][] FREE_SPIN_REELS = new int[FREE_REELS.length][];

    static {
        for (int i = 0; i < REELS.length; i++) {
            String[] rows = REELS[i];
            int[] rowIconS = new int[rows.length];
            for (int i1 = 0; i1 < rows.length; i1++) {
                String iconName = rows[i1];
                rowIconS[i1] = ICON_NAME.indexOf(iconName);
            }
            NORMAL_SPIN_REELS[i] = rowIconS;
        }

        for (int i = 0; i < FREE_REELS.length; i++) {
            String[] rows = FREE_REELS[i];
            int[] rowIconS = new int[rows.length];
            for (int i1 = 0; i1 < rows.length; i1++) {
                String iconName = rows[i1];
                rowIconS[i1] = ICON_NAME.indexOf(iconName);
            }
            FREE_SPIN_REELS[i] = rowIconS;
        }
    }

    public static int getRanNameIndex(int type,int columnId,int icon){
        List<Integer> iconRowNameIndex = getIconRowNameIndex(type, columnId, icon);
        return iconRowNameIndex.get(RandomUtil.nextInt(iconRowNameIndex.size()));
    }

    public static List<Integer> getIconRowNameIndex(int type, int columnId, int icon) {
        if (type == 0) {
            return findIcon(NORMAL_SPIN_REELS[columnId], icon);
        } else {
            if(columnId > 0 && columnId < COLUMNS - 1){
                columnId = GIANT_REEL_INDEX;
            }
            return findIcon(FREE_SPIN_REELS[columnId], icon);
        }
    }

    private static List<Integer> findIcon(int[] spinReel, int icon) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < spinReel.length; i++) {
            if (icon == spinReel[i]) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    public static int[] getRowIcons(int type, int rowNameIndex, int columnId) {
        int[] rowIcons = new int[ROWS];
        if (type == 0) {
            installRowIcon(rowNameIndex, rowIcons, NORMAL_SPIN_REELS[columnId]);
            return rowIcons;
        }

        if (columnId > 0 && columnId < COLUMNS - 1) {
            int icon = FREE_SPIN_REELS[GIANT_REEL_INDEX][rowNameIndex];
            Arrays.fill(rowIcons, icon);
        } else {
            installRowIcon(rowNameIndex, rowIcons, FREE_SPIN_REELS[columnId]);
        }
        return rowIcons;
    }

    private static void installRowIcon(int rowNameIndex, int[] rowIcons, int[] spinReel) {
        for (int i = 0; i < ROWS; i++) {
            int idx = rowNameIndex + i;
            idx %= spinReel.length;
            rowIcons[i] = spinReel[idx];
        }
    }

    public static int getRowIndexIconStartIdx(int columnId, int rowId, int icon) {
        List<Integer> index = findIcon(NORMAL_SPIN_REELS[columnId], icon);
        Integer ranIdx = index.get(RandomUtil.nextInt(index.size()));
        if(rowId == -1){
            rowId = RandomUtil.nextInt(ROWS);
        }
        switch (rowId){
            case 0:
                return ranIdx;
            case 1:
                if(ranIdx == 0){
                    return NORMAL_SPIN_REELS[columnId].length - 1;
                }
                return ranIdx - 1;
            default:
                if(ranIdx < 2){
                    return NORMAL_SPIN_REELS[columnId].length - 2;
                }
                return ranIdx - 2;
        }
    }
}
