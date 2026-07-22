package com.test;

import java.util.List;

public class Test111 {
    public static void main(String[] args) {

        long seed = 1876874595L;

        AviamastersRng rng =
                new AviamastersRng(seed);

        List<Bonus> bonuses =
                BonusFactory.create(rng);

        bonuses.forEach(b -> {

            System.out.printf(
                    "x=%d y=%d add=%d mul=%s%n",
                    b.getX(),
                    b.getY(),
                    b.getAdd(),
                    b.getMultiply()
            );
        });
    }
}
