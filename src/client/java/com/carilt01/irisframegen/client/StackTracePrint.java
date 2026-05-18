package com.carilt01.irisframegen.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StackTracePrint {

    public static Set<Integer> alreadyPrinted = new HashSet<>();
    private static final boolean ENABLED = false;


    public static void findMyCaller() {
        if (!ENABLED) {
            return;
        }
        List<StackWalker.StackFrame> stack = StackWalker.getInstance()
                .walk(frames -> frames.limit(99).collect(Collectors.toList()));

        StringBuilder fullStack = new StringBuilder();

        for (StackWalker.StackFrame frame : stack) {
            fullStack.append(frame.getClassName())
                    .append("#")
                    .append(frame.getMethodName())
                    .append(":")
                    .append(frame.getLineNumber())
                    .append("\n");
        }

        int hash = fullStack.toString().hashCode();

        if (alreadyPrinted.add(hash)) {
            System.out.println("---- stack ----");
            for (StackWalker.StackFrame frame : stack) {
                System.out.println("Class: " + frame.getClassName()
                        + " | Method: " + frame.getMethodName()
                        + " | Line: " + frame.getLineNumber());
            }
        }
    }
}