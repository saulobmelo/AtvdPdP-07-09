import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bulkhead com pool fixo e fila limitada.
 * - Se fila lotada e não adquirir em "acquireTimeoutMs", lança BulkheadFullException.
 */
public class BulkheadExecutor {

    public static class BulkheadFullException extends RuntimeException {
        public BulkheadFullException(String msg) { super(msg); }
    }

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final long acquireTimeoutMs;
    private final String name;

    public BulkheadExecutor(String name, int threads, int queueCapacity, long acquireTimeoutMs) {
        this.name = name;
        this.queueCapacity = queueCapacity;
        this.acquireTimeoutMs = acquireTimeoutMs;

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);

        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                30, TimeUnit.SECONDS,
                queue,
                new NamedThreadFactory("BH-" + name),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.prestartAllCoreThreads();
    }

    public <T> T submit(Callable<T> task) throws Exception {
        // Tenta enfileirar com timeout manual usando FutureTask e offer
        FutureTask<T> f = new FutureTask<>(task);
        boolean offered = executor.getQueue().offer(f, acquireTimeoutMs, TimeUnit.MILLISECONDS);
        if (!offered) {
            throw new BulkheadFullException("Bulkhead '" + name + "' saturado");
        }
        executor.execute(f);
        try {
            return f.get(); // sem timeout aqui; timeout deve ser controlado no cliente upstream
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        }
    }

    // ThreadFactory com nomes amigáveis
    static class NamedThreadFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger seq = new AtomicInteger(1);
        NamedThreadFactory(String base) { this.base = base; }
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, base + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}