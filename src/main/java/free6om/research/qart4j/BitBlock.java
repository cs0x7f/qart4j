package free6om.research.qart4j;

import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;

import java.util.Arrays;

/**
 * Created by free6om on 7/21/15.
 */
public class BitBlock {
    private int numberOfDataBytes;
    private int numberOfCheckBytes;
    private byte[] blockBytes;
    private byte[] decodedBytes;
    private boolean[] blockBytesAE;
    private byte[][] maskMatrix;
    private static byte[][] maskMatrixStatic;
    private int maskIndex;
    private ReedSolomonEncoder encoder;
    private byte[] primaryDataBytes;
    private int primaryDataIndex;
    private byte[] primaryCheckBytes;
    private int primaryCheckIndex;

    private int allowedErrorCount;
    private int allowedError;

    private static byte[][] getMaskMatrix(int numberOfDataBytes, int numberOfCheckBytes, ReedSolomonEncoder encoder) {
        byte[][] maskMatrix = new byte[numberOfDataBytes*8][numberOfDataBytes + numberOfCheckBytes];
        int iniIdxEnd = 0;

        if (maskMatrixStatic == null || maskMatrixStatic[0].length - maskMatrixStatic.length / 8 != numberOfCheckBytes) {
            maskMatrixStatic = new byte[numberOfDataBytes*8][numberOfDataBytes + numberOfCheckBytes];
            iniIdxEnd = numberOfDataBytes * 8;
        } else if (maskMatrixStatic.length != numberOfDataBytes * 8) {
            byte[][] maskMatrixStaticOld = maskMatrixStatic;
            maskMatrixStatic = new byte[numberOfDataBytes*8][numberOfDataBytes + numberOfCheckBytes];
            iniIdxEnd = numberOfDataBytes * 8 - maskMatrixStaticOld.length;
            for (int i = iniIdxEnd; i < maskMatrixStatic.length; i++) {
                Arrays.fill(maskMatrixStatic[i], (byte) 0);
                System.arraycopy(
                    maskMatrixStaticOld[i - iniIdxEnd], 0,
                    maskMatrixStatic[i], iniIdxEnd / 8, maskMatrixStaticOld[0].length
                );
            }
        }

        for(int i = 0;i < iniIdxEnd; i++) {
            Arrays.fill(maskMatrixStatic[i], (byte) 0);
            maskMatrixStatic[i][i/8] = (byte) (1 << (7 - i%8));
            System.arraycopy(
                ReedSolomonUtil.generateECBytes(encoder, maskMatrixStatic[i], 0, numberOfDataBytes, numberOfCheckBytes), 0,
                maskMatrixStatic[i], numberOfDataBytes, numberOfCheckBytes
            );
        }

        return maskMatrixStatic;
        // for(int i = 0;i < numberOfDataBytes*8;i++) {
        //     System.arraycopy(maskMatrixStatic[i], 0, maskMatrix[i], 0, numberOfDataBytes + numberOfCheckBytes);
        // }
        // return maskMatrix;
    }

    public BitBlock(int numberOfDataBytes, int numberOfCheckBytes, ReedSolomonEncoder encoder, byte[] primaryDataBytes, int primaryDataIndex, byte[] primaryCheckBytes, int primaryCheckIndex, int allowedError) throws QArtException {
        this.numberOfDataBytes = numberOfDataBytes;
        this.numberOfCheckBytes = numberOfCheckBytes;
        this.encoder = encoder;
        this.blockBytes = new byte[numberOfDataBytes + numberOfCheckBytes];
        this.decodedBytes = new byte[numberOfDataBytes + numberOfCheckBytes];
        this.blockBytesAE = new boolean[numberOfDataBytes + numberOfCheckBytes];
        this.primaryDataBytes = primaryDataBytes;
        this.primaryDataIndex = primaryDataIndex;
        this.primaryCheckBytes = primaryCheckBytes;
        this.primaryCheckIndex = primaryCheckIndex;
        this.allowedError = allowedError;
        this.allowedErrorCount = 0;

        System.arraycopy(primaryDataBytes, primaryDataIndex, decodedBytes, 0, numberOfDataBytes);
        byte[] checkBytes = ReedSolomonUtil.generateECBytes(encoder, decodedBytes, 0, numberOfDataBytes, numberOfCheckBytes);
        System.arraycopy(checkBytes, 0, decodedBytes, numberOfDataBytes, numberOfCheckBytes);
        System.arraycopy(decodedBytes, 0, blockBytes, 0, numberOfDataBytes + numberOfCheckBytes);

        byte[] expectCheckBytes = new byte[numberOfCheckBytes];
        System.arraycopy(primaryCheckBytes, primaryCheckIndex, expectCheckBytes, 0, numberOfCheckBytes);
        if(!Arrays.equals(expectCheckBytes, checkBytes)) {
            throw new QArtException("check data not match");
        }

        this.maskMatrix = getMaskMatrix(numberOfDataBytes, numberOfCheckBytes, encoder);
        this.maskIndex = this.maskMatrix.length;

    }

    public byte[] getBlockBytes() {
        return blockBytes;
    }

    public void check() throws QArtException {
        byte[] checkBytes = ReedSolomonUtil.generateECBytes(encoder, decodedBytes, 0, numberOfDataBytes, numberOfCheckBytes);
        byte[] expectCheckBytes = new byte[numberOfCheckBytes];
        System.arraycopy(decodedBytes, numberOfDataBytes, expectCheckBytes, 0, numberOfCheckBytes);

        if(!Arrays.equals(expectCheckBytes, checkBytes)) {
            throw new QArtException("ecc mismatch");
        }
    }

    private void revert(int index) {
        int nset = 0;
        int n2set = 0;
        int rowidx = 0;
        for (int j=maskIndex; j<maskMatrix.length; j++) {
            if((maskMatrix[j][index/8]&(1<<(7-index&7))) == 0) {
                continue;
            }
            rowidx = j;
            nset++;
        }
        for (int j=0; j<maskIndex; j++) {
            if((maskMatrix[j][index/8]&(1<<(7-index&7))) == 0) {
                continue;
            }
            n2set++;
        }
        if (nset == 1 && n2set == 0) {
            exchangeRow(maskMatrix, maskIndex, rowidx);
            maskIndex++;
        }
    }

    public void reset(int index, byte value) throws QArtException {
        if (((blockBytes[index/8]>>(7-index&7))&1) == (value&1)) {
            // already has desired bit
            return;
        }

        for(int i = this.maskIndex;i < this.maskMatrix.length;i++) {
            byte[] row = this.maskMatrix[i];
            if((row[index/8]&(1<<(7-index&7))) != 0) {
                for(int j = 0;j < row.length;j++) {
                    decodedBytes[j] ^= (value&1);
                    blockBytes[j] ^= (value&1);
                }

                return;
            }
        }

        throw new QArtException("reset of unset bit");
    }


    public boolean tryError(int index, byte value, int low, int high) {
        if (!blockBytesAE[index/8]) {
            if (allowedErrorCount >= allowedError) {
                return false;
            }
            allowedErrorCount++;
            blockBytesAE[index >> 3] = true;
            for (int i=0; i<8; i++) {
                int revertIdx = index & 0xfffffff8 | i;
                if (revertIdx >= low && revertIdx < high) {
                    revert(revertIdx);
                }
            }
        }
        if(((blockBytes[index/8]>>(7-index&7))&1) != (value&1)) {
            blockBytes[index/8] ^= 1<<(7-index&7);
        }
        return true;
    }

    public boolean canSet(int index, byte value, int low, int high) throws QArtException {

        if (blockBytesAE[index/8]) {
            if(((blockBytes[index/8]>>(7-index&7))&1) != (value&1)) {
                blockBytes[index/8] ^= 1<<(7-index&7);
            }
            return true;
        }

        boolean found = false;
        for(int j = 0;j < maskIndex;j++) {
            if((maskMatrix[j][index/8]&(1<<(7-index&7))) == 0) {
                continue;
            }

            if(!found) {
                found = true;
                if(j != 0) {
                    exchangeRow(maskMatrix, 0, j);
                }
                continue;
            }

            for(int k = 0;k < maskMatrix[j].length;k++) {
                maskMatrix[j][k] ^= maskMatrix[0][k];
            }
        }

        if(!found) {
            return tryError(index, value, low, high);
        }

        // Subtract from saved-away rows too.
        byte[] target = maskMatrix[0];
        for(int i = maskIndex;i < maskMatrix.length;i++) {
            byte[] row = maskMatrix[i];
            if((row[index/8]&(1<<(7-index&7))) == 0) {
                continue;
            }
            for(int k = 0;k < row.length;k++) {
                row[k] ^= target[k];
            }
        }

        // Found a row with bit #bi == 1 and cut that bit from all the others.
        // Apply to data and remove from m.
        if(((decodedBytes[index/8]>>(7-index&7))&1) != (value&1)) {
            for(int j = 0;j < target.length;j++) {
                byte v = target[j];
                decodedBytes[j] ^= v;
                if (blockBytesAE[j]) {
                    continue;
                }
                blockBytes[j] ^= v;
            }
        }

        // this.check();
        exchangeRow(maskMatrix, 0, maskIndex - 1);
        maskIndex--;

        for(int i = 0;i < maskIndex;i++) {
            byte[] row = maskMatrix[i];
            if((row[index/8]&(1<<(7-index&7))) != 0) {
                throw new QArtException("did not reduce");
            }
        }

        return true;

    }

    public void copyOut() throws QArtException {
        check();

        System.arraycopy(blockBytes, 0, primaryDataBytes, primaryDataIndex, numberOfDataBytes);
        System.arraycopy(blockBytes, numberOfDataBytes, primaryCheckBytes, primaryCheckIndex, numberOfCheckBytes);
    }

    private void exchangeRow(byte[][] matrix, int i, int j) {
        byte[] tmp = new byte[matrix[i].length];
        System.arraycopy(maskMatrix[i], 0, tmp, 0, tmp.length);
        System.arraycopy(maskMatrix[j], 0, maskMatrix[i], 0, maskMatrix[i].length);
        System.arraycopy(tmp, 0, maskMatrix[j], 0, tmp.length);
    }
}
