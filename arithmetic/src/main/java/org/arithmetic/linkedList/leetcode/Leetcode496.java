package org.arithmetic.linkedList.leetcode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhengrui
 * @description
 *       下一个更大元素
 *
 *       nums1 中数字 x 的 下一个更大元素 是指 x 在 nums2 中对应位置 右侧 的 第一个 比 x 大的元素。
 *
 * 给你两个 没有重复元素 的数组 nums1 和 nums2 ，下标从 0 开始计数，其中nums1 是 nums2 的子集。
 *
 * 对于每个 0 <= i < nums1.length ，找出满足 nums1[i] == nums2[j] 的下标 j ，并且在 nums2 确定 nums2[j] 的 下一个更大元素 。如果不存在下一个更大元素，那么本次查询的答案是 -1 。
 *
 * 返回一个长度为 nums1.length 的数组 ans 作为答案，满足 ans[i] 是如上所述的 下一个更大元素 。
 *
 * 来源：力扣（LeetCode）
 * 链接：https://leetcode.cn/problems/next-greater-element-i
 * 著作权归领扣网络所有。商业转载请联系官方授权，非商业转载请注明出处。
 * @date 2022-09-27 20:47
 */
public class Leetcode496 {
    public static int[] nextGreaterElement(int[] nums1, int[] nums2) {
        Map<Integer,Integer> map = new HashMap<>();
        for(int i = 0; i< nums2.length; i++) {
            if(i == nums2.length - 1) {
                map.put(nums2[1], -1);
                break;
            }
            if(nums2[i] < nums2[i + 1]) {
                map.put(nums2[i],nums2[i + 1]);
            } else {
                map.put(nums2[i], -1);
            }
        }
        int[] ret = new int[nums1.length];
        for(int i = 0; i< nums1.length; i++) {
            ret[i] = map.get(nums1[i]) == null ? -1  :  map.get(nums1[i]);
        }
        return ret;
    }

    public static void main(String[] args) {
        int[] nums1 = {2,4};
        int[] nums2 = {2,1,3,4};
        nextGreaterElement(nums1,nums2);
    }
}
