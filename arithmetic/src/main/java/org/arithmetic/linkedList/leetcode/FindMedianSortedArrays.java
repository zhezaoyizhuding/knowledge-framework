package org.arithmetic.linkedList.leetcode;

/**
 * @author zhengrui
 * @description 给定两个大小分别为 m 和 n 的正序（从小到大）数组 nums1 和 nums2。请你找出并返回这两个正序数组的 中位数 。
 *
 * 算法的时间复杂度应该为 O(log (m+n)) 。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode-cn.com/problems/median-of-two-sorted-arrays
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 * @date 2022-04-09 13:48
 */
public class FindMedianSortedArrays {
    public static double findMedianSortedArrays(int[] nums1, int[] nums2) {
        if(nums1.length == 0 && nums2.length == 0) {
            return 0;
        }
        int low = 0;
        int high = 0;
        int mCur = 0;
        int nCur = 0;
        while(mCur + nCur < (nums1.length + nums2.length) / 2 + 1) {
            low = high;
            if(nCur == nums2.length) {
                high = nums1[mCur];
                mCur++;
                continue;
            }
            if(mCur == nums1.length) {
                high = nums2[nCur];
                nCur ++;
                continue;
            }
            if(nums1[mCur] <= nums2[nCur]) {
                high = nums1[mCur];
                mCur++;
            } else {
                high = nums2[nCur];
                nCur ++;
            }
        }
        if((nums1.length + nums2.length) % 2 == 0) {
            return Double.valueOf(low + high) / 2;
        } else {
            return Double.valueOf(high);
        }
    }

    public static void main(String[] args) {
        int[] nums1 = new int[]{};
        int[] nums2 = new int[]{5,6,7,8};
        System.out.println(findMedianSortedArrays(nums1,nums2));
    }
}
