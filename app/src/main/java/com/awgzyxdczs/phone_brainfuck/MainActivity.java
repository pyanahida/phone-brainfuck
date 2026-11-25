package com.awgzyxdczs.phone_brainfuck;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends Activity {

    private EditText etCode, etInput;
    private TextView tvOutput;
    private ScrollView scrollOutput;
    private Button btnRun, btnStop, btnClear;

    private final StringBuilder outputBuffer = new StringBuilder();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private Thread worker;

    private static final int TAPE_SIZE = 30000;
    private static final long MAX_STEPS = 100_000_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etCode = findViewById(R.id.etCode);
        etInput = findViewById(R.id.etInput);
        tvOutput = findViewById(R.id.tvOutput);
        scrollOutput = findViewById(R.id.scrollOutput);
        btnRun = findViewById(R.id.btnRun);
        btnStop = findViewById(R.id.btnStop);
        btnClear = findViewById(R.id.btnClear);

        btnRun.setOnClickListener(v -> startRun());
        btnStop.setOnClickListener(v -> stopRun());
        btnClear.setOnClickListener(v -> {
            outputBuffer.setLength(0);
            tvOutput.setText("");
        });
    }

    private void startRun() {
        if (running) return;
        String code = etCode.getText().toString();
        String input = etInput.getText().toString();

        outputBuffer.setLength(0);
        tvOutput.setText("");
        running = true;

        worker = new Thread(() -> {
            try {
                execute(code, input);
            } catch (BrainfuckException e) {
                appendOutput("\n[错误] " + e.getMessage());
            } catch (Throwable t) {
                appendOutput("\n[异常] " + t);
            } finally {
                running = false;
                uiHandler.post(() -> {
                    btnRun.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            }
        });
        btnRun.setEnabled(false);
        btnStop.setEnabled(true);
        worker.start();
    }

    private void stopRun() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void appendOutput(final String s) {
        uiHandler.post(() -> {
            outputBuffer.append(s);
            tvOutput.setText(outputBuffer.toString());
            scrollOutput.post(() -> scrollOutput.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    // ================= Brainfuck 解释器 =================

    private void execute(String rawCode, String input) throws BrainfuckException {
        // 1. 过滤非指令字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawCode.length(); i++) {
            char c = rawCode.charAt(i);
            if (c == '+' || c == '-' || c == '<' || c == '>' ||
                c == '.' || c == ',' || c == '[' || c == ']') {
                sb.append(c);
            }
        }
        String code = sb.toString();

        // 2. 括号匹配
        int n = code.length();
        int[] jump = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (code.charAt(i) == '[') {
                stack.push(i);
            } else if (code.charAt(i) == ']') {
                if (stack.isEmpty()) {
                    throw new BrainfuckException("多余的 ']' 在位置 " + i);
                }
                int open = stack.pop();
                jump[open] = i;
                jump[i] = open;
            }
        }
        if (!stack.isEmpty()) {
            throw new BrainfuckException("未闭合的 '[' 在位置 " + stack.peek());
        }

        // 3. 准备输入队列
        Deque<Integer> inputQueue = new ArrayDeque<>();
        for (int i = 0; i < input.length(); i++) {
            inputQueue.addLast((int) input.charAt(i));
        }

        // 4. 执行
        byte[] tape = new byte[TAPE_SIZE];
        int ptr = 0;
        long steps = 0;
        int pc = 0;

        while (pc < n) {
            if (!running) {
                appendOutput("\n[已停止]");
                return;
            }
            if (++steps > MAX_STEPS) {
                throw new BrainfuckException("超过最大步数限制 " + MAX_STEPS + "，可能死循环");
            }

            char op = code.charAt(pc);
            switch (op) {
                case '>':
                    ptr++;
                    if (ptr >= TAPE_SIZE) {
                        throw new BrainfuckException("指针越界 (右)");
                    }
                    break;
                case '<':
                    ptr--;
                    if (ptr < 0) {
                        throw new BrainfuckException("指针越界 (左)");
                    }
                    break;
                case '+':
                    tape[ptr]++;
                    break;
                case '-':
                    tape[ptr]--;
                    break;
                case '.':
                    appendOutput(String.valueOf((char) (tape[ptr] & 0xFF)));
                    break;
                case ',':
                    Integer in = inputQueue.pollFirst();
                    tape[ptr] = (byte) (in == null ? 0 : in);
                    break;
                case '[':
                    if (tape[ptr] == 0) {
                        pc = jump[pc];
                    }
                    break;
                case ']':
                    if (tape[ptr] != 0) {
                        pc = jump[pc];
                    }
                    break;
                default:
                    break;
            }
            pc++;
        }

        appendOutput("\n[完成] 共执行 " + steps + " 步");
    }

    private static class BrainfuckException extends Exception {
        BrainfuckException(String msg) {
            super(msg);
        }
    }
}
