package com.reason.device.sim;

/**
 * 模拟车牌池（剧本与手控共用的公共取牌逻辑）
 */
public final class Plates {

    private static final String[] POOL = {
            "浙B8K521", "浙B2Q078", "浙B9M633", "浙B5T210", "浙B1F874", "浙B7D302", "浙B3N906"
    };

    private Plates() {
    }

    /** 按时钟轮转取牌（同秒取同牌，便于剧本复现） */
    public static String pick(long nowSeconds) {
        return POOL[(int) (nowSeconds % POOL.length)];
    }
}
