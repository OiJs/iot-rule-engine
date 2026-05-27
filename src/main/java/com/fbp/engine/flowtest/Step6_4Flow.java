package com.fbp.engine.flowtest;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.SplitNode;
import com.fbp.engine.node.TimerNode;
import java.util.Map;

public class Step6_4Flow {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 6-4: SplitNode 분기 플로우 ===");

        TimerNode timer = new TimerNode("timer", Map.of("intervalMs", 500L));
        SplitNode split = new SplitNode("split", "tick", 3.0);
        PrintNode matchPrinter = new PrintNode("경고");
        PrintNode mismatchPrinter = new PrintNode("정상");

        LocalConnection conn1 = new LocalConnection("timer-to-split");
        LocalConnection matchConn = new LocalConnection("match-conn");
        LocalConnection mismatchConn = new LocalConnection("mismatch-conn");

        timer.getOutputPort("out").connect(conn1);
        split.getOutputPort("match").connect(matchConn);
        split.getOutputPort("mismatch").connect(mismatchConn);

        // SplitThread
        Thread splitThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = conn1.poll();
                if (msg != null) split.process(msg);
            }
        }, "SplitThread");

        // MatchThread
        Thread matchThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = matchConn.poll();
                if (msg != null) matchPrinter.process(msg);
            }
        }, "MatchThread");

        // MismatchThread
        Thread mismatchThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = mismatchConn.poll();
                if (msg != null) mismatchPrinter.process(msg);
            }
        }, "MismatchThread");

        splitThread.start();
        matchThread.start();
        mismatchThread.start();
        timer.initialize();

        Thread.sleep(4000);

        timer.shutdown();
        splitThread.interrupt();
        matchThread.interrupt();
        mismatchThread.interrupt();

        System.out.println("=== 완료 (tick 0,1,2 → 정상 / tick 3 이상 → 경고) ===");
    }
}
