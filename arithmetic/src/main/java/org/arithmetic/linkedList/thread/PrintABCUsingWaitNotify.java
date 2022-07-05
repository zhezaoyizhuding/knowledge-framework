package org.arithmetic.linkedList.thread;

/**
 * @author zhengrui
 * @description
 * @date 2022-03-24 23:17
 */
public class PrintABCUsingWaitNotify {
    private int state;
    private int times;
    private static final Object LOCK = new Object();

    public PrintABCUsingWaitNotify(int times) {
        this.times = times;
    }

    public static void main(String[] args) {
        PrintABCUsingWaitNotify printABC = new PrintABCUsingWaitNotify(50);
        new Thread(() -> {
            printABC.printLetter("1", 0);
        }, "A").start();
        new Thread(() -> {
            printABC.printLetter("2", 1);
        }, "B").start();
    }

    private void printLetter(String name, int targetState) {
        for (int i = 0; i < times; i++) {
            synchronized (LOCK) {
                while (state % 2 != targetState) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                state++;
                System.out.println("thread" + name + " - " + state);
                LOCK.notifyAll();
            }
        }
    }
}
