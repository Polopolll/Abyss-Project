package com.andy.abyss.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/** Delayed and repeating server tasks, ticked once per server tick. */
public final class Scheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long tick;

    private static final class Task {
        long next; int every, left, index; IntConsumer run;
    }

    /** Run once after {@code delay} ticks. */
    public static void later(int delay, Runnable r) {
        repeat(delay, 1, 1, i -> r.run());
    }

    /** Run {@code count} times, first after {@code delay} ticks, then every {@code every} ticks; the argument is the run index. */
    public static void repeat(int delay, int every, int count, IntConsumer r) {
        Task t = new Task();
        t.next = tick + Math.max(0, delay); t.every = Math.max(1, every); t.left = count; t.run = r;
        PENDING.add(t);
    }

    public static void tick() {
        tick++;
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.next > tick) continue;
            try { t.run.accept(t.index++); } catch (Exception e) { com.andy.abyss.Abyss.LOG.error("Abyss task failed", e); SelfTest.errors++; t.left = 0; }
            if (--t.left <= 0) it.remove(); else t.next = tick + t.every;
        }
    }

    public static void clear() { TASKS.clear(); PENDING.clear(); }
    private Scheduler() {}
}
