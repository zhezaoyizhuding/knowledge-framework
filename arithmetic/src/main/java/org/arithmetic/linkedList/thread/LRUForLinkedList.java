package org.arithmetic.linkedList.thread;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhengrui
 * @description
 * @date 2022-09-29 20:52
 */
public class LRUForLinkedList extends LinkedHashMap<Integer, Integer> {
        private int capacity;

        public LRUForLinkedList(int capacity) {
            super(capacity, 0.75F, true);
            this.capacity = capacity;
        }

        public int get(int key) {
            return super.getOrDefault(key, -1);
        }

        public void put(int key, int value) {
            super.put(key, value);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > capacity;
        }
}
