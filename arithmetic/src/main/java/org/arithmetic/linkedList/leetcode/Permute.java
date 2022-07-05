package org.arithmetic.linkedList.leetcode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhengrui
 * @description
 * @date 2022-04-17 14:31
 */
public class Permute {
    public static List<List<Integer>> permute(int[] nums) {
        if(nums == null || nums.length == 0) {
            return new ArrayList<>();
        }
        List<List<Integer>> ans = new ArrayList<>();
        backdating(ans,new ArrayList<>(),nums,0);
        return ans;
    }

    private static void backdating(List<List<Integer>> ans, List<Integer> combine, int[] nums, int index) {
        if(index == nums.length) {
            ans.add(new ArrayList<>(combine));
            return;
        }
        for(int i = index; i < nums.length; i++) {
            combine.add(nums[i]);
            backdating(ans,combine,nums,i + 1);
            combine.remove(combine.size() - 1);
        }
    }

    public static void main(String[] args) {
        int[] nums = new int[]{1,2,3};
        System.out.println(permute(nums));
    }
}
