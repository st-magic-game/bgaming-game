package com.test;

public class Bonus {

    private int x;
    private int y;

    private int add;
    private double multiply;

    public Bonus(
            AviamastersRng rng,
            int rarity,
            int add,
            double multiply) {

        this.add = add;
        this.multiply = multiply;

        respawn(rng, rarity);
    }

    private void respawn(
            AviamastersRng rng,
            int rarity) {

        this.x =
                (int) rng.random(rarity) + 4000;

        this.y =
                -(int) rng.random(4000) - 700;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getAdd() {
        return add;
    }

    public double getMultiply() {
        return multiply;
    }
}