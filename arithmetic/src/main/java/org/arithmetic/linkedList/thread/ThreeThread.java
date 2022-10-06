package org.arithmetic.linkedList.thread;

/**
 * @author zhengrui
 * @description 三个线程交替打印 --- synchronized
 * 线程1：1 2 3 4 5. 线程2： 6 7 8 9 10。线程3：11 12 13 14 15
 * @date 2022-03-24 23:17
 */
public class ThreeThread {

    public static void main(String[] args) {
        Object o = new Object();
        new Thread(new PrintRunnable(o, 1)).start();
        new Thread(new PrintRunnable(o, 2)).start();
        new Thread(new PrintRunnable(o, 3)).start();
    }
}

class PrintRunnable implements Runnable {
    private Object o;
    private int threadId;
    private static int num = 1;

    public PrintRunnable(Object o, int threadId) {
        this.o = o;
        this.threadId = threadId;
    }

    @Override
    public void run() {
        while (num < 65) {//65是因为刚开始3个线程拿到num都为1
            synchronized (o) {
                while (num / 5 % 3 + 1 != threadId) {
                    try {
                        o.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.print("线程：" + threadId + ": ");
                for (int i = 5; i > 0; i--, num++) {
                    System.out.print(" " + num);
                }
                System.out.println();
                o.notifyAll();
            }
        }

    }
}
