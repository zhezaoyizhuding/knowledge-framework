package org.arithmetic.linkedList.thread;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author zhengrui
 * @description 三个线程交替打印 --- ReentrantLock版
 * 线程1：1 2 3 4 5. 线程2： 6 7 8 9 10。线程3：11 12 13 14 15
 * @date 2022-03-24 23:17
 */
public class ThreadCondition {
    static Lock lock = new ReentrantLock();
    static Condition A = lock.newCondition();
    static Condition B = lock.newCondition();
    static Condition C = lock.newCondition();
    static int num = 1;
    static int count = 0;

    static class PrintRunnableA implements Runnable {
        @Override
        public void run() {
            try {
                lock.lock();
                while (num < 65) {
                    while (count % 3 != 0) {
                        A.await();
                    }
                    printNum("1");
                    count++;
                    B.signal();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    static class PrintRunnableB implements Runnable {
        @Override
        public void run() {
            try {
                lock.lock();
                while (num < 65) {
                    while (count % 3 != 1) {
                        B.await();
                    }
                    printNum("2");
                    count++;
                    C.signal();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    static class PrintRunnableC implements Runnable {
        @Override
        public void run() {
            try {
                lock.lock();
                while (num < 65) {
                    while (count % 3 != 2) {
                        C.await();
                    }
                    printNum("3");
                    count = 0;
                    A.signal();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    private static void printNum(String name) {
        for (int i = 5; i > 0; i--, num++) {
            System.out.println("线程" + name + ": " + num);
        }
    }

    public static void main(String[] args) {
        new Thread(new PrintRunnableA()).start();
        new Thread(new PrintRunnableB()).start();
        new Thread(new PrintRunnableC()).start();

    }
}