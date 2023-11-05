/*
 * MIT License
 *
 * Copyright (c) 2023 Mischa Kölliker
 */
package ch.mischak.fb5210.crc;

public class CrcCalculator {

    private AlgoParams parameters;
    private byte       hashSize;
    private int        mask       = 0xFFFFFFFF;
    private int[]      table      = new int[256];

    public CrcCalculator(AlgoParams params)
    {
        parameters = params;

        hashSize = (byte) params.hashSize;
        if (hashSize < 64)
        {
            mask = (1 << hashSize) - 1;
        }

        createTable();
    }

    private static int reverseBits(int ul)
    {
        int newValue = 0;

        for (int i = 7; i >= 0; i--)
        {
            newValue |= (ul & 1) << i;
            ul >>= 1;
        }

        return newValue;
    }

    public int calc(byte[] data, int offset, int length)
    {
        int init = parameters.refOut ? reverseBits(parameters.init) : parameters.init;
        int hash = computeCrc(init, data, offset, length);
        return (hash ^ parameters.xorOut) & mask;
    }

    private int computeCrc(int init, byte[] data, int offset, int length)
    {
        int crc = init;

        if (parameters.refOut)
        {
            for (int i = offset; i < offset + length; i++)
            {
                crc = (table[(crc ^ data[i]) & 0xFF] ^ (crc >>> 8));
                crc &= mask;
            }
        }
        else
        {
            int toRight = (hashSize - 8);
            toRight = Math.max(toRight, 0);
            for (int i = offset; i < offset + length; i++) {
                crc = (table[((crc >> toRight) ^ data[i]) & 0xFF] ^ (crc << 8));
                crc &= mask;
            }
        }

        return crc;
    }

    private void createTable()
    {
        for (int i = 0; i < table.length; i++)
            table[i] = createTableEntry(i);
    }

    private int createTableEntry(int index)
    {
        int r = index;

        if (parameters.refIn)
            r = reverseBits(r);
        else if (hashSize > 8)
            r <<= (hashSize - 8);

        long lastBit = (1L << (hashSize - 1));

        for (int i = 0; i < 8; i++)
        {
            if ((r & lastBit) != 0)
                r = ((r << 1) ^ parameters.poly);
            else
                r <<= 1;
        }

        if (parameters.refOut)
            r = reverseBits(r);

        return r & mask;
    }

    //
    //
    //

    public static void main(String[] args) {
        CrcCalculator crcCalc = new CrcCalculator(new AlgoParams(8, 0xD5, 0x0, true, true, 0x0));

        verify(crcCalc, new byte [] {(byte)0xFF, (byte)0xFF});
        verify(crcCalc, new byte [] {0x10, 0x02, (byte)0x9B, 0x7F, 0x05, 0x02, (byte)0x83, (byte)0xE7, 0x00, 0x5C, 0x10, 0x03});
        verify(crcCalc, new byte [] {0x10, 0x02, (byte)0x9B, 0x7F, 0x05, 0x02, (byte)0x83, (byte)0xF7, 0x00, 0x06, 0x21, 0x0A, (byte)0xD0, 0x02, 0x06, (byte)0x86, 0x03, (byte)0xE8, 0x00, 0x00, 0x03, (byte)0xE8, (byte)0xAF, 0x10, 0x03});
    }

    private static void verify(CrcCalculator crcCalc, byte[] ticket) {
        int ticketpos = ticket.length;

        if (ticketpos > 5) {
            int checksum = crcCalc.calc(ticket, 2, ticketpos - 5 /* 2 byte start, 2 byte end, 1 byte checksum*/);

            System.out.println(((byte)checksum) == ticket[ticketpos - 3] ? " OK" : " NOK! ["+Integer.toHexString(checksum)+"]");
        } else {
            System.out.println(" [Too short]");
        }
    }
}
