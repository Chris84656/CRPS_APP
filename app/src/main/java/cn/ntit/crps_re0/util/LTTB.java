package cn.ntit.crps_re0.util;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

/**
 * Largest Triangle Three Buckets 降采样算法
 */
public class LTTB {

    public static List<Entry> downsample(List<Entry> data, int threshold) {
        int size = data.size();
        if (size <= threshold) return new ArrayList<>(data);

        List<Entry> result = new ArrayList<>(threshold);
        result.add(data.get(0));

        double bucketSize = (double) (size - 2) / (threshold - 2);

        int prevIndex = 0;

        for (int i = 1; i < threshold - 1; i++) {
            int bucketStart = (int) Math.floor((i - 1) * bucketSize) + 1;
            int bucketEnd = (int) Math.floor(i * bucketSize) + 1;
            if (bucketEnd > size - 1) bucketEnd = size - 1;

            // 下一个 bucket 的平均值
            int nextBucketStart = (int) Math.floor(i * bucketSize) + 1;
            int nextBucketEnd = (int) Math.floor((i + 1) * bucketSize) + 1;
            if (nextBucketEnd > size - 1) nextBucketEnd = size - 1;

            float avgX = 0, avgY = 0;
            int nextCount = nextBucketEnd - nextBucketStart;
            if (nextCount <= 0) {
                // #23: 首 bucket 空边界修复——循环不执行时，用 bucketStart 处的值作为平均
                avgX = data.get(nextBucketStart).getX();
                avgY = data.get(nextBucketStart).getY();
            } else {
                for (int j = nextBucketStart; j < nextBucketEnd; j++) {
                    avgX += data.get(j).getX();
                    avgY += data.get(j).getY();
                }
                avgX /= nextCount;
                avgY /= nextCount;
            }

            // 当前 bucket 中面积最大的点
            Entry prevEntry = data.get(prevIndex);
            float maxArea = -1;
            int maxIndex = bucketStart;

            for (int j = bucketStart; j < bucketEnd; j++) {
                Entry cur = data.get(j);
                float area = Math.abs(
                        (prevEntry.getX() - avgX) * (cur.getY() - prevEntry.getY())
                                - (prevEntry.getX() - cur.getX()) * (avgY - prevEntry.getY())
                ) * 0.5f;
                if (area > maxArea) {
                    maxArea = area;
                    maxIndex = j;
                }
            }

            result.add(data.get(maxIndex));
            prevIndex = maxIndex;
        }

        result.add(data.get(size - 1));
        return result;
    }
}
