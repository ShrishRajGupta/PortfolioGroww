package com.example.demo;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.repository.PositionRepository;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.TradeRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.PositionService;
import com.example.demo.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end concurrency proof on a real (in-memory) database with real transactions:
 * many threads book trades against ONE position at the same time. The position row's optimistic
 * lock plus the retry loop must give exactly-once accounting — no lost updates, no oversell.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.trade.max-attempts=200",
        "app.trade.retry-backoff-ms=1"
})
class TradeConcurrencyTest {

    @Autowired
    private TradeService tradeService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private PositionRepository positions;
    @Autowired
    private TradeRepository trades;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StockRepository stocks;

    private UserAccount user;
    private Stock stock;

    @BeforeEach
    void freshUserAndStock() {
        String tag = Long.toHexString(System.nanoTime());
        user = users.save(UserAccount.builder().name("Racer " + tag).email("racer-" + tag + "@example.com").build());
        BigDecimal p = new BigDecimal("100.0000");
        stock = stocks.save(Stock.builder().name("RACE-" + tag).openPrice(p).closePrice(p).highPrice(p).lowPrice(p).settlementPrice(p).build());
    }

    /** Runs every task at once (common start latch) and returns the exceptions they threw. */
    private List<Throwable> runAllAtOnce(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                task.run();
                return null;
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                fail("a trade hung");
            }
        }
        pool.shutdownNow();
        return failures;
    }

    @Test
    void concurrentBuysOnOnePosition_loseNoUpdates() throws Exception {
        int threads = 16;
        int perThread = 5;
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                for (int i = 0; i < perThread; i++) {
                    tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.BUY, 1));
                }
            });
        }

        List<Throwable> failures = runAllAtOnce(tasks);

        assertTrue(failures.isEmpty(), "unexpected failures: " + failures);
        int expected = threads * perThread;
        assertEquals(expected, trades.findByUserAccountId(user.getId()).size(), "every trade reached the ledger");
        Position position = positions.findByUserAccountIdAndStockId(user.getId(), stock.getId()).orElseThrow();
        assertEquals(expected, position.getNetQuantity(), "no lost update on the position");
        assertEquals(expected - 1L, position.getVersion(), "one insert + one version bump per committed update");
        assertTrue(positionService.drift(user.getId()).isEmpty(), "position agrees with the ledger");
    }

    @Test
    void concurrentSells_canNeverOversell() throws Exception {
        int held = 5;
        tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.BUY, held));

        int attempts = 12;
        AtomicInteger rejected = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                try {
                    tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.SELL, 1));
                } catch (InsufficientPositionException e) {
                    rejected.incrementAndGet();
                }
            });
        }

        List<Throwable> failures = runAllAtOnce(tasks);

        assertTrue(failures.isEmpty(), "only InsufficientPositionException is acceptable: " + failures);
        assertEquals(attempts - held, rejected.get(), "exactly the surplus sells were rejected");
        Position position = positions.findByUserAccountIdAndStockId(user.getId(), stock.getId()).orElseThrow();
        assertEquals(0, position.getNetQuantity());
        assertEquals(1 + held, trades.findByUserAccountId(user.getId()).size(), "1 BUY + exactly `held` SELLs booked");
        assertTrue(positionService.drift(user.getId()).isEmpty());
    }

    @Test
    void concurrentFirstTradesOnANewPosition_createExactlyOneRow() throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.BUY, 2)));
        }

        List<Throwable> failures = runAllAtOnce(tasks);

        assertTrue(failures.isEmpty(), "unexpected failures: " + failures);
        assertEquals(1, positions.findAllForUser(user.getId()).size(), "the insert race collapsed to one row");
        assertEquals(20, positions.findByUserAccountIdAndStockId(user.getId(), stock.getId()).orElseThrow().getNetQuantity());
    }
}
