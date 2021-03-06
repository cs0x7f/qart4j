package free6om.research.qart4j;

import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import org.apache.commons.imaging.ImageReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * Created by free6om on 7/21/15.
 */
public class Image {
    private static final Logger LOGGER = LoggerFactory.getLogger(Image.class);

    private int[][] target;
    private int divider;
    private int dx, dy;
    private String URL;
    private int version;
    private int mask;
    private int rotation;

    private boolean randControl;
    private long seed;

    private boolean dither;

    private boolean onlyDataBits;

    private boolean saveControl;
    private byte[] control;

    private boolean noAlign;
    private boolean noTiming;
    private int pixelPriority;
    private int allowedError;

    public Image(int[][] target, int dx, int dy, int version) {
        this.target = target;
        this.dx = dx;
        this.dy = dy;
        this.version = version;
    }

    public Image(int[][] target, int dx, int dy, String URL,
                 int version, int mask, int rotation,
                 boolean randControl, long seed, boolean dither, boolean onlyDataBits, boolean saveControl, boolean noAlign, boolean noTiming, int pixelPriority, int allowedError) throws IOException, ImageReadException {
        this.target = target;
        this.dx = dx;
        this.dy = dy;
        this.URL = URL;

        this.version = version;
        this.mask = mask;
        this.rotation = rotation;

        this.randControl = randControl;
        this.seed = seed;
        this.dither = dither;
        this.onlyDataBits = onlyDataBits;
        this.saveControl = saveControl;
        this.noAlign = noAlign;
        this.noTiming = noTiming;
        this.pixelPriority = pixelPriority;
        this.allowedError = allowedError;

        this.divider = calculateDivider();
    }

    public void setTarget(int[][] target) {
        this.target = target;
    }

    public void setDivider(int divider) {
        this.divider = divider;
    }

    public void setDx(int dx) {
        this.dx = dx;
    }

    public void setDy(int dy) {
        this.dy = dy;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public void setRandControl(boolean randControl) {
        this.randControl = randControl;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void setDither(boolean dither) {
        this.dither = dither;
    }

    public void setOnlyDataBits(boolean onlyDataBits) {
        this.onlyDataBits = onlyDataBits;
    }

    public void setSaveControl(boolean saveControl) {
        this.saveControl = saveControl;
    }

    public void setNoAlign(boolean noAlign) {
        this.noAlign = noAlign;
    }

    public void setNoTiming(boolean noTiming) {
        this.noTiming = noTiming;
    }

    public void setPixelPriority(int pixelPriority) {
        this.pixelPriority = pixelPriority;
    }

    public void setControl(byte[] control) {
        this.control = control;
    }

    public Target target(int x, int y) {
        int tx = x + dx;
        int ty = y + dy;
        if (ty < 0 || ty >= target.length || tx < 0 || tx >= target[ty].length) {
            return new Target((byte) 255, -1);
        }

        int v0 = target[ty][tx];
        if (v0 < 0) {
            return new Target((byte) 255, -1);
        }

        byte targ = (byte) v0;

        int contrast = 0;
        if (this.pixelPriority == 0) {
            int n = 0;
            long sum = 0;
            long sumSequence = 0;
            int del = 5;
            for (int dy = -del; dy <= del; dy++) {
                for (int dx = -del; dx <= del; dx++) {
                    if (0 <= ty+dy && ty+dy < this.target.length && 0 <= tx+dx && tx+dx < this.target[ty+dy].length) {
                        int v = this.target[ty+dy][tx+dx];
                        sum += v;
                        sumSequence += v * v;
                        n++;
                    }
                }
            }

            int avg = (int) (sum / n);
            contrast = (int) (sumSequence/n - avg*avg);
        } else if (this.pixelPriority == 1) {
            int center = 8 + this.version * 2;
            contrast = 100000 - Math.max(Math.abs(x - center), Math.abs(y - center)) * 100 - Math.min(Math.abs(x - center), Math.abs(y - center));
        } else if (this.pixelPriority == 2) {
            int center = 8 + this.version * 2;
            contrast = 100000 - (x - center) * (x - center) - (y - center) * (y - center);
        } else if (this.pixelPriority == 3) {
            int center = 8 + this.version * 2;
            contrast = 100000 - Math.max(Math.abs(x - center) * 5 / 4, Math.abs(y - center)) * 100 - Math.min(Math.abs(x - center) * 5 / 4, Math.abs(y - center));
        }
        return new Target(targ, contrast);
    }

    public void rotate(Plan plan, int rotation) {
        if(rotation == 0) {
            return;
        }

        int n = plan.getPixels().length;
        Pixel[][] pixels = new Pixel[n][n];

        switch (rotation) {
            case 1:
                for(int y = 0;y < n;y++) {
                    for(int x = 0;x < n;x++) {
                        pixels[y][x] = plan.getPixels()[x][n - 1 - y];
                    }
                }
                break;
            case 2:
                for(int y = 0;y < n;y++) {
                    for(int x = 0;x < n;x++) {
                        pixels[y][x] = plan.getPixels()[n - 1 - y][n - 1 - x];
                    }
                }
                break;
            case 3:
                for(int y = 0;y < n;y++) {
                    for(int x = 0;x < n;x++) {
                        pixels[y][x] = plan.getPixels()[n - 1 -x][y];
                    }
                }
                break;
        }

        plan.setPixels(pixels);
    }

    public QRCode encode() throws QArtException {
        Plan plan = Plan.newPlan(new Version(version), Level.L, new Mask(mask));

        rotate(plan, rotation);

        QRCode qrCode = Plan.encode(plan, new Padding(new byte[0]));

        Random random = new Random(seed);

        // QR parameters.
        int numberOfDataBytesPerBlock = plan.getNumberOfDataBytes() / plan.getNumberOfBlocks();
        int numberOfCheckBytesPerBlock = plan.getNumberOfCheckBytes() / plan.getNumberOfBlocks();
        int numberOfExtraBytes = plan.getNumberOfDataBytes() - numberOfDataBytesPerBlock * plan.getNumberOfBlocks();
        ReedSolomonEncoder encoder = new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256);

        if (allowedError < 0) {
            allowedError += numberOfCheckBytesPerBlock / 2 + 1;
        }

        // Build information about pixels, indexed by data/check bit number.
        PixelInfo[] pixelByOffset = new PixelInfo[(plan.getNumberOfDataBytes() + plan.getNumberOfCheckBytes()) * 8];
        boolean[][] expect = new boolean[plan.getPixels().length][plan.getPixels().length];
        Pixel[][] pixels = plan.getPixels();
        for(int y = 0;y < pixels.length;y++) {
            for(int x = 0;x < pixels[y].length;x++) {
                Pixel pixel = pixels[y][x];
                expect[y][x] = ((pixel.getPixel()&Pixel.BLACK.getPixel()) != 0);

                Target target = target(x, y);
                if(randControl && target.contrast >= 0) {
                    target.contrast = random.nextInt(128) + 64*((x+y)%2) + 64*((x+y)%3%2);
                }
                Pixel.PixelRole role = pixel.getPixelRole();
                if(role == Pixel.PixelRole.DATA || role == Pixel.PixelRole.CHECK) {
                    pixelByOffset[pixel.getOffset()] = new PixelInfo(x, y, new Pixel(pixel), target.target, target.contrast);
                }
            }
        }

        String url = this.URL;
        int errorCount;

        Bits bits = new Bits();
        // Count fixed initial data bits, prepare template URL.
        Encoding dataEncoding = new Alpha(url);
        if (dataEncoding.validate() != null) {
            dataEncoding = new Raw(url);
        }
        dataEncoding.encode(bits, plan.getVersion());
        new Padding(new byte[0]).encode(bits, plan.getVersion());
        int headSize = bits.getSize();
        int dataBitsRemaining = plan.getNumberOfDataBytes()*8 - headSize;
        if(dataBitsRemaining < 0) {
            throw new QArtException("cannot encode URL into available bits");
        }

        byte[] paddings = new byte[dataBitsRemaining];

        do {
            int nd = numberOfDataBytesPerBlock;
            bits.reset();
            Arrays.fill(paddings, (byte) 0);

            dataEncoding.encode(bits, plan.getVersion());
            new Padding(paddings).encode(bits, plan.getVersion());
            bits.addCheckBytes(plan.getVersion(), plan.getLevel());

            byte[] data = bits.getBits();

            int dataOffset = 0;
            int checkOffset = 0;
            int mainDataBits = headSize + dataBitsRemaining;

            // Choose pixels.
            BitBlock[] bitBlocks = new BitBlock[plan.getNumberOfBlocks()];
            for (int blockNumber = 0; blockNumber < plan.getNumberOfBlocks(); blockNumber++) {
                if (blockNumber == plan.getNumberOfBlocks()-numberOfExtraBytes) {
                    nd++;
                }

                BitBlock bitBlock = new BitBlock(nd, numberOfCheckBytesPerBlock, encoder,
                        data, dataOffset/8,
                        data, plan.getNumberOfDataBytes() + checkOffset/8, allowedError);
                bitBlocks[blockNumber] = bitBlock;

                // Determine which bits in this block we can try to edit.
                int low = 0, high = nd*8;
                if(low < headSize - dataOffset) {
                    low = headSize - dataOffset;
                    if(low > high) {
                        low = high;
                    }
                }
                if(high > mainDataBits - dataOffset) {
                    high = mainDataBits - dataOffset;
                    if(high < low) {
                        high = low;
                    }
                }

                // Preserve [0, lo) and [hi, nd*8).
                for (int i = 0; i < low; i++) {
                    if (!bitBlock.canSet(i, (byte) ((data[dataOffset/8 + i/8]>>(7-i&7))&1), low, high)) {
                        throw new QArtException("cannot preserve required bits");
                    }
                }
                for (int i = high; i < nd*8; i++) {
                    if (!bitBlock.canSet(i, (byte) ((data[dataOffset/8 + i/8]>>(7-i&7))&1), low, high)) {
                        throw new QArtException("cannot preserve required bits");
                    }
                }

                // Can edit [lo, hi) and checksum bits to hit target.
                // Determine which ones to try first.
                PixelOrder[] order = new PixelOrder[nd*8 + numberOfCheckBytesPerBlock*8];
                for(int i = 0;i < order.length;i++) {
                    order[i] = new PixelOrder();
                }
                for (int i = 0; i < nd*8; i++) {
                    order[i].setOffset(dataOffset + i);
                }
                for (int i = 0; i < numberOfCheckBytesPerBlock*8; i++) {
                    order[nd*8+i].setOffset(plan.getNumberOfDataBytes()*8 + checkOffset + i);
                }
                if (onlyDataBits) {
                    order = Arrays.copyOf(order, high - low);
                }
                for (int i = 0;i < order.length;i++) {
                    PixelOrder pixelOrder = order[i];
                    pixelOrder.setPriority(pixelByOffset[pixelOrder.getOffset()].getContrast() << 8 | random.nextInt(256));
                }
                Arrays.sort(order, new Comparator<PixelOrder>() {
                    @Override
                    public int compare(PixelOrder o1, PixelOrder o2) {
                        return o2.getPriority() - o1.getPriority();
                    }
                });

                boolean mark = false;
                for (int i = 0;i < order.length;i++) {
                    PixelOrder po = order[i];
                    PixelInfo info = pixelByOffset[po.getOffset()];
                    int value = ((int)info.getTarget())&0xFF;
                    if(value < this.divider) {
                        value = 1;
                    } else {
                        value = 0;
                    }

                    Pixel pixel = info.getPixel();
                    if(pixel.shouldInvert()) {
                        value ^= 1;
                    }
                    if(info.isHardZero()) {
                        value = 0;
                    }

                    int index;
                    boolean shouldKeep = false;

                    if (pixel.getPixelRole() == Pixel.PixelRole.DATA) {
                        index = po.getOffset() - dataOffset;
                        shouldKeep = index < low || index >= high;
                    } else {
                        index = po.getOffset() - plan.getNumberOfDataBytes()*8 - checkOffset + nd*8;
                    }
                    if (shouldKeep ? bitBlock.tryError(index, (byte) value, low, high) : bitBlock.canSet(index, (byte) value, low, high)) {
                        info.setBlock(bitBlock);
                        info.setBitIndex(index);
                        if(mark) {
                            pixels[info.getY()][info.getX()] = Pixel.BLACK;
                        }
                    } else {
//                        LOGGER.debug("can't set, i: {}, high - low: {}", i, (high - low));
//                        if(info.isHardZero()) {
//                            throw new QArtException("Hard zero can not set");
//                        }
                        if(mark) {
                            pixels[info.getY()][info.getX()] = new Pixel(0); //todo will cause error?
                        }
                    }
                }
                bitBlock.copyOut();

                boolean cheat = false;
                for (int i = 0; i < nd*8; i++) {
                    PixelInfo info = pixelByOffset[dataOffset+i];
                    Pixel pixel = new Pixel(pixels[info.getY()][info.getX()]);
                    if ((bitBlock.getBlockBytes()[i/8]&(1<<(7-i&7))) != 0) {
                        pixel.xorPixel(Pixel.BLACK.getPixel());
                    }
                    expect[info.getY()][info.getX()] = ((pixel.getPixel()&Pixel.BLACK.getPixel()) != 0);
                    if (cheat) {
                        Pixel p = new Pixel(pixel);
                        p.setPixel(Pixel.BLACK.getPixel());
                        pixels[info.getY()][info.getX()] = p;
                    }
                }
                for (int i = 0; i < numberOfCheckBytesPerBlock*8; i++) {
                    PixelInfo info = pixelByOffset[plan.getNumberOfDataBytes()*8 + checkOffset + i];
                    Pixel pixel = new Pixel(pixels[info.getY()][info.getX()]);

                    if ((bitBlock.getBlockBytes()[nd+i/8]&(1<<(7-i&7))) != 0) {
                        pixel.xorPixel(Pixel.BLACK.getPixel());
                    }
                    expect[info.getY()][info.getX()] = ((pixel.getPixel()&Pixel.BLACK.getPixel()) != 0);
                    if (cheat) {
                        Pixel p = new Pixel(pixel);
                        p.setPixel(Pixel.BLACK.getPixel());
                        pixels[info.getY()][info.getX()] = p;
                    }
                }

                dataOffset += nd * 8;
                checkOffset += numberOfCheckBytesPerBlock * 8;
            }


            // Pass over all pixels again, dithering.
            if (this.dither) {
                for(int i = 0;i < pixelByOffset.length;i++) {
                    PixelInfo info = pixelByOffset[i];
                    info.setDitherTarget(info.getTarget());
                }
                for(int y = 0;y < pixels.length;y++) {
                    Pixel[] row = pixels[y];
                    for(int x = 0;x < row.length;x++) {
                        Pixel pixel = row[x];
                        Pixel.PixelRole role = pixel.getPixelRole();
                        if (role != Pixel.PixelRole.DATA && role != Pixel.PixelRole.CHECK) {
                            continue;
                        }
                        PixelInfo info = pixelByOffset[pixel.getOffset()];
                        if (info.getBlock() == null) {
                            // did not choose this pixel
                            continue;
                        }

                        pixel = info.getPixel();

                        byte pixelValue = 1;
                        int grayValue = 0;
                        int targ = info.getDitherTarget();

                        if (targ >= this.divider) {
                            // want white
                            pixelValue = 0;
                            grayValue = 255;
                        }
                        byte bitValue = pixelValue;
                        if (pixel.shouldInvert()) {
                            bitValue ^= 1;
                        }
                        if (info.isHardZero() && bitValue != 0) {
                            bitValue ^= 1;
                            pixelValue ^= 1;
                            grayValue ^= 0xFF;
                        }

                        // Set pixel value as we want it.
                        info.getBlock().reset(info.getBitIndex(), bitValue);

                        int error = targ - grayValue;

                        if (x+1 < row.length) {
                            addDither(pixelByOffset, row[x+1], error*7/16);
                        }
//                        if (false && y+1 < pixels.length) {
//                            if (x > 0) {
//                                addDither(pixelByOffset, pixels[y+1][x-1], error*3/16);
//                            }
//                            addDither(pixelByOffset, pixels[y+1][x], error*5/16);
//                            if (x+1 < row.length) {
//                                addDither(pixelByOffset, pixels[y+1][x+1], error*1/16);
//                            }
//                        }
                    }
                }

                for(int i = 0;i < bitBlocks.length;i++) {
                    bitBlocks[i].copyOut();
                }

            }

            errorCount = 0;

            // Copy data back out.
            for (int i = 0; i < dataBitsRemaining; i++) {
                int index = headSize + i;
                paddings[i] = (byte) ((data[index/8] >> (7 - index&7)) & 1);;
            }
        } while (errorCount > 0);

        // Bits finalBits = new Bits();
        // dataEncoding.encode(finalBits, plan.getVersion());
        // new Padding(paddings).encode(finalBits, plan.getVersion());
        // finalBits.addCheckBytes(plan.getVersion(), plan.getLevel());

        // if(!Arrays.equals(finalBits.getBits(), bits.getBits())) {
        //     LOGGER.warn("mismatch\n{} {}\n{} {}\n", bits.getBits().length, bits.getBits(), finalBits.getBits().length, finalBits.getBits());
        //     throw new QArtException("byte mismatch");
        // }

        // qrCode = Plan.encode(plan, dataEncoding, new Padding(paddings));

        if (this.saveControl) {
            for(int y = 0;y < pixels.length;y++) {
                Pixel[] row = pixels[y];
                for(int x = 0;x < row.length;x++) {
                    Pixel pixel = row[x];
                    Pixel.PixelRole role = pixel.getPixelRole();
                    if (role != Pixel.PixelRole.DATA && role != Pixel.PixelRole.CHECK) {
                        pixels[y][x].setCtrl(1);
                        continue;
                    }
                    PixelInfo info = pixelByOffset[pixel.getOffset()];
                    if (info.getBlock() == null) {
                        pixels[y][x].setCtrl(1);
                    }
                }
            }
        }

        removePattern(Pixel.PixelRole.EXTRA, pixels);

        if (this.noAlign) {
            removePattern(Pixel.PixelRole.ALIGNMENT, pixels);
        }

        if (this.noTiming) {
            removePattern(Pixel.PixelRole.TIMING, pixels);
        }

        return new QRCode(bits.getBits(), pixels);
    }

    private void removePattern(Pixel.PixelRole target_role, Pixel[][] pixels) {
        Pixel pixelWhite = new Pixel(target_role);
        Pixel pixelBlack = new Pixel(target_role);
        pixelBlack.setPixel(Pixel.BLACK.getPixel());
        for(int y = 0;y < pixels.length;y++) {
            Pixel[] row = pixels[y];
            for(int x = 0;x < row.length;x++) {
                Pixel pixel = row[x];
                if (pixel.getPixelRole() != target_role) {
                    continue;
                }
                if (y > pixels.length - 10 && x > row.length - 10) {
                    continue;
                }
                int targ = target(x, y).target & 0xff;

                pixels[y][x] = (targ >= this.divider) ? pixelWhite : pixelBlack;
            }
        }
    }

    private int calculateDivider() {
        long sum = 0;
        int n = 0;
        for(int i = 0;i < this.target.length;i++) {
            for(int j = 0;j < this.target[i].length;j++) {
                sum += this.target[i][j];
                n++;
            }
        }

        if(n == 0) {
            return 128;
        }

        return (int) (sum/n);
    }

    private void addDither(PixelInfo[] pixelByOffset, Pixel pixel, int error) {
        Pixel.PixelRole role = pixel.getPixelRole();
        if (role != Pixel.PixelRole.DATA && role != Pixel.PixelRole.CHECK) {
            return;
        }

        PixelInfo info = pixelByOffset[pixel.getOffset()];

        info.setDitherTarget(info.getDitherTarget() + error);
    }

    public static final class Target {
        public byte target;
        public int contrast;

        public Target(byte target, int contrast) {
            this.target = target;
            this.contrast = contrast;
        }
    }
}
