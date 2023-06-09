package jzip;

public class InflaterHuffmanTree {

    private short[] tree;
    public static InflaterHuffmanTree defLitLenTree, defDistTree;

    static {
        byte[] codeLengths = new byte[288];
        int i = 0;
        while (i < 144) {
            codeLengths[i++] = 8;
        }
        while (i < 256) {
            codeLengths[i++] = 9;
        }
        while (i < 280) {
            codeLengths[i++] = 7;
        }
        while (i < 288) {
            codeLengths[i++] = 8;
        }
        defLitLenTree = new InflaterHuffmanTree(codeLengths, 288);

        codeLengths = new byte[32];
        i = 0;
        while (i < 32) {
            codeLengths[i++] = 5;
        }
        defDistTree = new InflaterHuffmanTree(codeLengths, 32);
    }

    public InflaterHuffmanTree(byte[] codeLengths, int len) {
        buildTree(codeLengths, len);
    }

    private void buildTree(byte[] codeLengths, int len) {
        int[] blCount = new int[15 + 1];
        int[] nextCode = new int[15 + 1];
        for (int i = 0; i < len; i++) {
            int bits = codeLengths[i];
            if (bits > 0) {
                blCount[bits]++;
            }
        }

        int code = 0;
        int treeSize = 512;
        for (int bits = 1; bits <= 15; bits++) {
            nextCode[bits] = code;
            code += blCount[bits] << (16 - bits);
            if (bits >= 10) {
                int start = nextCode[bits] & 0x1ff80;
                int end = code & 0x1ff80;
                treeSize += (end - start) >> (16 - bits);
            }
        }
        if (code != 65536) {
            throw new RuntimeException("Code lengths don't add up properly.");
        }

        tree = new short[treeSize];
        int treePtr = 512;
        for (int bits = 15; bits >= 10; bits--) {
            int end = code & 0x1ff80;
            code -= blCount[bits] << (16 - bits);
            int start = code & 0x1ff80;
            for (int i = start; i < end; i += 1 << 7) {
                tree[DeflaterHuffman.bitReverse(i)] = (short) ((-treePtr << 4) | bits);
                treePtr += 1 << (bits - 9);
            }
        }

        for (int i = 0; i < len; i++) {
            int bits = codeLengths[i];
            if (bits == 0) {
                continue;
            }
            code = nextCode[bits];
            int revcode = DeflaterHuffman.bitReverse(code);
            if (bits <= 9) {
                do {
                    tree[revcode] = (short) ((i << 4) | bits);
                    revcode += 1 << bits;
                } while (revcode < 512);
            } else {
                int subTree = tree[revcode & 511];
                int treeLen = 1 << (subTree & 15);
                subTree = -(subTree >> 4);
                do {
                    tree[subTree | (revcode >> 9)] = (short) ((i << 4) | bits);
                    revcode += 1 << bits;
                } while (revcode < treeLen);
            }
            nextCode[bits] = code + (1 << (16 - bits));
        }
    }

    public int getSymbol(StreamManipulator input) {
        int lookahead, symbol;
        if ((lookahead = input.peekBits(9)) >= 0) {
            if ((symbol = tree[lookahead]) >= 0) {
                input.dropBits(symbol & 15);
                return symbol >> 4;
            }
            int subtree = -(symbol >> 4);
            int bitlen = symbol & 15;
            if ((lookahead = input.peekBits(bitlen)) >= 0) {
                symbol = tree[subtree | (lookahead >> 9)];
                input.dropBits(symbol & 15);
                return symbol >> 4;
            } else {
                int bits = input.getAvailableBits();
                lookahead = input.peekBits(bits);
                symbol = tree[subtree | (lookahead >> 9)];
                if ((symbol & 15) <= bits) {
                    input.dropBits(symbol & 15);
                    return symbol >> 4;
                } else {
                    return -1;
                }
            }
        } else {
            int bits = input.getAvailableBits();
            lookahead = input.peekBits(bits);
            symbol = tree[lookahead];
            if (symbol >= 0 && (symbol & 15) <= bits) {
                input.dropBits(symbol & 15);
                return symbol >> 4;
            } else {
                return -1;
            }
        }
    }
}
