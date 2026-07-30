package com.test;

public class AviamastersRng {

    private long state0;
    private long state1;
    private long state2;

    public AviamastersRng(long seed) {
        seed(seed);
    }

    public void seed(long seed) {

        this.state0 = seed;

        this.state1 =
                seed * 213947L + 1238971L;

        this.state2 =
                seed * 7431L + 94823L;

        random();
    }

    public long random() {
        return random(Long.MAX_VALUE);
    }

    public long random(long max) {

        long t = state0;
        long s = state1;

        state0 = s;

        t ^= (t << 23);
        t ^= (t >>> 17);
        t ^= s;
        t ^= (s >>> 26);

        state1 = t;

        state2 =
                (1103515245L * state2 + 12345L)
                        % 2147483648L;

        long value =
                (state0 + state1 + state2);

        value = value & Long.MAX_VALUE;

        return value % max;
    }
}