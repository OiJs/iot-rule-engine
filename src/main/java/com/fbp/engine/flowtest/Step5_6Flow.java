//package com.fbp.engine.flowtest;
//
//import com.fbp.engine.core.LocalConnection;
//import com.fbp.engine.message.Message;
//import com.fbp.engine.node.FilterNode;
//import com.fbp.engine.node.PrintNode;
//import com.fbp.engine.node.TimerNode;
//
//public class Step5_6Flow {
//    public static void main(String[] args) throws InterruptedException {
//        System.out.println("=== Step 5-6: TimerNode → FilterNode(tick>=3) → PrintNode ===");
//
//        TimerNode timer = new TimerNode("timer", 500);
//        FilterNode filter = new FilterNode("filter", "tick", 3.0);
//        PrintNode printer = new PrintNode("printer");
//
//        LocalConnection conn1 = new LocalConnection("timer-to-filter");
//        LocalConnection conn2 = new LocalConnection("filter-to-print");
//
//        timer.getOutputPort("out").connect(conn1);
//        filter.getOutputPort("out").connect(conn2);
//
//        Thread filterThread = new Thread(() -> {
//            while (!Thread.currentThread().isInterrupted()) {
//                Message msg = conn1.poll();
//                if (msg != null) filter.process(msg);
//            }
//        }, "FilterThread");
//
//        Thread printThread = new Thread(() -> {
//            while (!Thread.currentThread().isInterrupted()) {
//                Message msg = conn2.poll();
//                if (msg != null) printer.process(msg);
//            }
//        }, "PrintThread");
//
//        filterThread.start();
//        printThread.start();
//        timer.initialize();
//
//        Thread.sleep(3000);
//
//        timer.shutdown();
//        filterThread.interrupt();
//        printThread.interrupt();
//
//        System.out.println("=== 완료 (tick 0,1,2는 필터링, tick 3 이상만 출력) ===");
//    }
//}