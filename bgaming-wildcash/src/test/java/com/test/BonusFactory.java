package com.test;

import java.util.ArrayList;
import java.util.List;

public class BonusFactory {

    public static List<Bonus> create(
            AviamastersRng rng) {

        List<Bonus> list =
                new ArrayList<>();

        list.add(new Bonus(rng,1000,1,1));
        list.add(new Bonus(rng,1000,1,1));
        list.add(new Bonus(rng,1000,1,1));

        list.add(new Bonus(rng,2000,2,1));
        list.add(new Bonus(rng,2000,2,1));

        list.add(new Bonus(rng,5000,5,1));

        list.add(new Bonus(rng,10000,10,1));

        list.add(new Bonus(rng,8000,0,2));
        list.add(new Bonus(rng,10000,0,3));
        list.add(new Bonus(rng,12000,0,4));
        list.add(new Bonus(rng,15000,0,5));

        list.add(new Bonus(rng,5000,0,0.5));

        return list;
    }
}