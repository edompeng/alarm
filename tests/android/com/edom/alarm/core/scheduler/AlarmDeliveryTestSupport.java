package com.edom.alarm.core.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Framework-free fakes and assertions shared by Java delivery-policy tests. */
public final class AlarmDeliveryTestSupport {
  private AlarmDeliveryTestSupport() {}

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }

  /** A deterministic clock whose value changes only when a test changes it. */
  public static final class FakeClock {
    private long nowMillis;

    public FakeClock(long initialMillis) {
      nowMillis = initialMillis;
    }

    public long nowMillis() {
      return nowMillis;
    }

    public void setNowMillis(long value) {
      nowMillis = value;
    }

    public void advanceMillis(long durationMillis) {
      if (durationMillis < 0) {
        throw new IllegalArgumentException("durationMillis must be non-negative");
      }
      nowMillis = Math.addExact(nowMillis, durationMillis);
    }
  }

  /** A synchronized key-value fake for persistence and atomic-claim policy tests. */
  public static final class FakeAlarmStore {
    private final Map<String, String> values = new LinkedHashMap<>();

    public synchronized void put(String key, String value) {
      values.put(requireKey(key), value);
    }

    public synchronized String get(String key) {
      return values.get(requireKey(key));
    }

    public synchronized boolean compareAndSet(String key, String expected, String updated) {
      String checkedKey = requireKey(key);
      if (!Objects.equals(values.get(checkedKey), expected)) {
        return false;
      }
      values.put(checkedKey, updated);
      return true;
    }

    public synchronized Map<String, String> snapshot() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String requireKey(String key) {
      if (key == null || key.isEmpty()) {
        throw new IllegalArgumentException("key must be non-empty");
      }
      return key;
    }
  }

  /** Records schedule/cancel requests without constructing Android PendingIntents. */
  public static final class FakeAlarmRegistrationGateway {
    public enum Operation {
      SCHEDULE,
      CANCEL
    }

    public static final class Call {
      private final Operation operation;
      private final String identity;
      private final long triggerAtMillis;

      private Call(Operation operation, String identity, long triggerAtMillis) {
        this.operation = operation;
        this.identity = identity;
        this.triggerAtMillis = triggerAtMillis;
      }

      public Operation operation() {
        return operation;
      }

      public String identity() {
        return identity;
      }

      public long triggerAtMillis() {
        return triggerAtMillis;
      }
    }

    private final List<Call> calls = new ArrayList<>();
    private boolean nextScheduleAccepted = true;

    public boolean schedule(String identity, long triggerAtMillis) {
      calls.add(new Call(Operation.SCHEDULE, requireIdentity(identity), triggerAtMillis));
      return nextScheduleAccepted;
    }

    public boolean cancel(String identity) {
      calls.add(new Call(Operation.CANCEL, requireIdentity(identity), 0L));
      return true;
    }

    public void setNextScheduleAccepted(boolean accepted) {
      nextScheduleAccepted = accepted;
    }

    public List<Call> calls() {
      return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    private static String requireIdentity(String identity) {
      if (identity == null || identity.isEmpty()) {
        throw new IllegalArgumentException("identity must be non-empty");
      }
      return identity;
    }
  }

  public static void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public static void assertFalse(boolean condition, String message) {
    assertTrue(!condition, message);
  }

  public static void assertEquals(long expected, long actual, String message) {
    if (expected != actual) {
      throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
    }
  }

  public static void assertEquals(Object expected, Object actual, String message) {
    if (!Objects.equals(expected, actual)) {
      throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
    }
  }

  public static int runSuite(String suiteName, ThrowingRunnable suite) {
    try {
      suite.run();
      System.out.println("PASS " + suiteName);
      return 0;
    } catch (Throwable failure) {
      System.err.println("FAIL " + suiteName + ": " + failure.getMessage());
      failure.printStackTrace(System.err);
      return 1;
    }
  }

  public static void main(String[] args) {
    System.exit(runSuite("AlarmDeliveryTestSupport", AlarmDeliveryTestSupport::selfCheck));
  }

  private static void selfCheck() {
    FakeClock clock = new FakeClock(10L);
    clock.advanceMillis(5L);
    assertEquals(15L, clock.nowMillis(), "clock advances deterministically");

    FakeAlarmStore store = new FakeAlarmStore();
    store.put("claim", "pending");
    assertTrue(store.compareAndSet("claim", "pending", "claimed"), "first claim wins");
    assertFalse(store.compareAndSet("claim", "pending", "claimed-again"), "second claim loses");

    FakeAlarmRegistrationGateway gateway = new FakeAlarmRegistrationGateway();
    gateway.setNextScheduleAccepted(false);
    assertFalse(gateway.schedule("alarm-1", 20L), "configured registration failure is returned");
    assertEquals(1L, gateway.calls().size(), "registration call is recorded");
  }
}
