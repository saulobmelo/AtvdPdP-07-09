import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Circuit Breaker simples com 3 estados:
 * - CLOSED: tudo normal
 * - OPEN: falhas acima do limiar; bloqueia chamadas por um período (openTimeoutMs)
 * - HALF_OPEN: após timeout, permite 1 tentativa; se sucesso -> CLOSED; se falha -> OPEN
 */
public class CircuitBreaker {

    public static class OpenCircuitException extends RuntimeException {
        public OpenCircuitException(String msg) { super(msg); }
    }

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openTimeoutMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openSince = 0L;
    private volatile State state = State.CLOSED;
    private volatile boolean halfOpenTrialInProgress = false;

    public CircuitBreaker(int failureThreshold, long openTimeoutMs) {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold >= 1");
        this.failureThreshold = failureThreshold;
        this.openTimeoutMs = openTimeoutMs;
    }

    public synchronized <T> T call(Supplier<T> supplier) {
        long now = System.currentTimeMillis();
        switch (state) {
            case OPEN:
                if (now - openSince >= openTimeoutMs) {
                    state = State.HALF_OPEN;
                    halfOpenTrialInProgress = false; // libera 1 tentativa
                } else {
                    throw new OpenCircuitException("Circuito aberto");
                }
                // fallthrough

            case HALF_OPEN:
                if (halfOpenTrialInProgress) {
                    // outra chamada simultânea tentou durante HALF_OPEN
                    throw new OpenCircuitException("Teste HALF_OPEN em progresso");
                }
                halfOpenTrialInProgress = true;
                try {
                    T res = supplier.get();
                    // sucesso -> fecha circuito
                    state = State.CLOSED;
                    consecutiveFailures.set(0);
                    halfOpenTrialInProgress = false;
                    return res;
                } catch (RuntimeException re) {
                    // volta a abrir
                    state = State.OPEN;
                    openSince = System.currentTimeMillis();
                    halfOpenTrialInProgress = false;
                    throw re;
                } catch (Exception ex) {
                    state = State.OPEN;
                    openSince = System.currentTimeMillis();
                    halfOpenTrialInProgress = false;
                    throw new RuntimeException(ex);
                }

            case CLOSED:
            default:
                try {
                    T res = supplier.get();
                    consecutiveFailures.set(0);
                    return res;
                } catch (RuntimeException re) {
                    int fails = consecutiveFailures.incrementAndGet();
                    if (fails >= failureThreshold) {
                        state = State.OPEN;
                        openSince = System.currentTimeMillis();
                    }
                    throw re;
                } catch (Exception ex) {
                    int fails = consecutiveFailures.incrementAndGet();
                    if (fails >= failureThreshold) {
                        state = State.OPEN;
                        openSince = System.currentTimeMillis();
                    }
                    throw new RuntimeException(ex);
                }
        }
    }

    // Métodos utilitários para testes/observabilidade
    public synchronized String state() { return state.name(); }
    public synchronized int failures() { return consecutiveFailures.get(); }
}