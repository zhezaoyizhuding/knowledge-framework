package org.arithmetic.linkedList.thread;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author zhengrui
 * @description 三个线程交替打印ABC --- ReentrantLock版
 * @date 2022-03-24 22:23
 */
public class ThreadPrint {

        private int times;
        private int state;
        private Lock lock = new ReentrantLock();

        public ThreadPrint(int times) {
            this.times = times;
        }

        private void printLetter(String name, int targetNum) {
            for (int i = 0; i < times; ) {
                lock.lock();
                if (state % 3 == targetNum) {
                    state++;
                    i++;
                    System.out.println("thread" + name + " - " + state);
                }
                lock.unlock();
            }
        }

        public static void main(String[] args) {
            ThreadPrint loopThread = new ThreadPrint(50);

            new Thread(() -> {
                loopThread.printLetter("C", 2);
            }, "C").start();
            new Thread(() -> {
                loopThread.printLetter("B", 1);
            }, "B").start();

            new Thread(() -> {
                loopThread.printLetter("A", 0);
            }, "A").start();

        }
}
