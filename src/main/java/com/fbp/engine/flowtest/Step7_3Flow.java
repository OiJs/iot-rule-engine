package com.fbp.engine.flowtest;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.core.Flow;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.SplitNode;
import com.fbp.engine.node.TimerNode;
import java.util.ArrayList;
import java.util.List;

public class Step7_3Flow {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 7-3: Main에서 process를 직접 구동하는 테스트 ===");

        TimerNode timer = new TimerNode("timer", 500);
        SplitNode split = new SplitNode("split", "tick", 3.0);
        PrintNode matchPrinter = new PrintNode("경고");
        PrintNode mismatchPrinter = new PrintNode("정상");

        Flow flow = new Flow("split-flow")
                .addNode(timer)
                .addNode(split)
                .addNode(matchPrinter)
                .addNode(mismatchPrinter)
                .connect("timer", "out", "split", "in")
                .connect("split", "match", "경고", "in")
                .connect("split", "mismatch", "정상", "in");

        flow.initialize();

        List<Thread> pipelineThreads = new ArrayList<>();

        LocalConnection conn1 = flow.getConnections().get(0);
        LocalConnection connMatch = flow.getConnections().get(1);
        LocalConnection connMismatch = flow.getConnections().get(2);

        pipelineThreads.add(new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = conn1.poll();
                if (msg != null) split.process(msg);
            }
        }, "Thread-Split"));

        pipelineThreads.add(new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = connMatch.poll();
                if (msg != null) matchPrinter.process(msg);
            }
        }, "Thread-Match"));

        pipelineThreads.add(new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = connMismatch.poll();
                if (msg != null) mismatchPrinter.process(msg);
            }
        }, "Thread-Mismatch"));

        pipelineThreads.forEach(Thread::start);

        Thread.sleep(4000);

        pipelineThreads.forEach(Thread::interrupt);
        flow.shutdown();

        System.out.println("=== Flow 테스트 완료 ===");
    }
}