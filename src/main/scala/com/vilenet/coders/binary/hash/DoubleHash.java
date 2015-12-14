package com.vilenet.coders.binary.hash;

/*
 * DoubleHash.java
 *
 * Created on April 13, 2004, 12:55 PM
 */


/**
 * This class does the double-hash for passwords. It hashes them alone, then it hashes them again along with the client
 * and server tokens.
 *
 * @author iago
 */
public class DoubleHash
{

    public static void main(String[] args)
    {
        /*
        171362176 1053699930
4859e4e9415bad7720f574cea6c3482013fc911d
44f000058c020a1165eeb59458f75bcd1a98f9e8
         */
        //int[] hash = doubleHash("The quick brown fox jumped over the lazy dog.", 0xdeadbeef, 0xbadcab);
        int[] hash = doubleHash("12354", 171362176, 1053699930);
        System.out.print(PadString.padHex(hash[0], 8));
        System.out.print(PadString.padHex(hash[1], 8));
        System.out.print(PadString.padHex(hash[2], 8));
        System.out.print(PadString.padHex(hash[3], 8));
        System.out.print(PadString.padHex(hash[4], 8));
        System.out.println();
    }

    /**
     * This static method does the actual doublehash.
     *
     * @param str
     *            The string we're doublehashing.
     * @param clientToken
     *            The client token for this session.
     * @param serverToken
     *            The server token for this session.
     * @return The 5-DWord (20 byte) hash.
     */
    static public int[] doubleHash(String str, int clientToken, int serverToken)
    {
        Buffer initialHash = new Buffer();
        initialHash.addNTString(str);
        int[] hash1 = BrokenSHA1.calcHashBuffer(initialHash.getBytes());

        Buffer secondHash = new Buffer();
        secondHash.add(clientToken);
        secondHash.add(serverToken);
        for(int i = 0; i < 5; i++)
            secondHash.add(hash1[i]);

        return BrokenSHA1.calcHashBuffer(secondHash.getBytes());
    }
}
