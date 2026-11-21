package com.phoneai.overlay;

/** 下拉重新整理的數學（純 Java）：手指拉的距離怎麼換成畫面上移動的距離。 */
final class PullMath {
    private PullMath() { }

    /**
     * 阻尼：一開始幾乎跟著手指，越拉越吃力，但永遠還會動一點（不會到某個距離就突然卡死）。
     * limit 是永遠拉不到的上限。
     */
    static float resist(float fingerDistance, float limit) {
        if (fingerDistance <= 0) return 0;
        return (float) (limit * (1 - Math.exp(-fingerDistance / (limit * 1.1))));
    }

    /** 0～1：拉到門檻的程度（圓圈的弧線依它畫出來）。 */
    static float progress(float pulled, float threshold) { return Math.max(0f, Math.min(1f, pulled / threshold)); }

    /** 放手時算不算「拉夠了」：到門檻的九成就算，免得差一點點白拉。 */
    static boolean triggers(float pulled, float threshold) { return pulled >= threshold * 0.9f; }

    /**
     * 圓圈上弧線的長度（0～1 圈）：從一小段慢慢變長，但最多約四分之三圈，不會封成「整個圓」
     * （封成整圈看起來就像已經完成或壞掉；超過門檻後靠旋轉表示還在回應手指）。
     */
    static float arcFraction(float pulled, float threshold) {
        if (pulled <= 0) return 0f;
        return 0.05f + 0.70f * progress(pulled, threshold);
    }
}
