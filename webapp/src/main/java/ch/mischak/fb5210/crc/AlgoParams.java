/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.crc;

public class AlgoParams {
    public AlgoParams(int hashSize, int poly, int init, boolean refIn, boolean refOut, int xorOut) {
        this.init = init;
        this.poly = poly;
        this.refIn = refIn;
        this.refOut = refOut;
        this.xorOut = xorOut;
        this.hashSize = hashSize;
    }

    int     hashSize;
    int     init;
    int     poly;
    boolean refIn;
    boolean refOut;
    int     xorOut;
}
