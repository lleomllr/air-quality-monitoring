package com.type;

public class SumCount {
    private double sum;
    private int count;

    public SumCount(double sum, int count) {
        this.sum = sum;
        this.count = count;
    }

    public double getSum() {
        return sum;
    }

    public int getCount() {
        return count;
    }

    public void add(double value) {
        this.sum += value;
        this.count += 1;
    }

    public double average() {
        return this.count == 0 ? 0.0 : this.sum / this.count;
    }

    public String toString() {
        return "Sum = " + sum + ", Count = " + count;
    }
}