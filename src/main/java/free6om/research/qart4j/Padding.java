package free6om.research.qart4j;

import java.io.UnsupportedEncodingException;

/**
 * Created by free6om on 7/21/15.
 */
public class Padding implements Encoding {

    private byte[] padding;

    public Padding(byte[] padding) {
        this.padding = padding;
    }

    @Override
    public String validate() {
        return null;
    }

    @Override
    public int availableBits(Version version) {
        return 4 + padding.length;
    }

    @Override
    public void encode(Bits bits, Version version) {
        bits.write(0, 4);

        for (int i = 0; i < padding.length; i++) {
            bits.write(padding[i], 1);
        }
    }

    @Override
    public String toString() {
        return "Padding(" + new String(padding) + ")";
    }
}
