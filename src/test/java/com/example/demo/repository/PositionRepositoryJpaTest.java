package com.example.demo.repository;

import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.UserAccount;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real JPA coverage of the positions mapping: query shape, unique pair, and the optimistic lock itself. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class PositionRepositoryJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PositionRepository positions;

    private UserAccount alice;
    private Stock acme;
    private Stock bolt;

    @BeforeEach
    void seed() {
        alice = em.persist(UserAccount.builder().name("Alice").email("alice@example.com").build());
        UserAccount bob = em.persist(UserAccount.builder().name("Bob").email("bob@example.com").build());
        acme = em.persist(stock("ACME", "120"));
        bolt = em.persist(stock("BOLT", "50"));
        em.persist(position(alice, bolt, 2, "40"));
        em.persist(position(alice, acme, 0, "0"));
        em.persist(position(bob, acme, 1, "100"));
        em.flush();
        em.clear();
    }

    private static Stock stock(String name, String price) {
        BigDecimal p = new BigDecimal(price);
        return Stock.builder().name(name).openPrice(p).closePrice(p).highPrice(p).lowPrice(p).settlementPrice(p).build();
    }

    private static Position position(UserAccount u, Stock s, long net, String avg) {
        return Position.builder().userAccount(u).stock(s).netQuantity(net)
                .avgCost(new BigDecimal(avg)).realizedPnl(BigDecimal.ZERO).build();
    }

    @Test
    void findAllForUser_returnsOpenAndClosed_oldestFirst_withStocksLoaded() {
        List<Position> result = positions.findAllForUser(alice.getId());

        assertEquals(2, result.size());
        assertEquals(List.of("BOLT", "ACME"), result.stream().map(p -> p.getStock().getName()).toList());
        assertEquals(new BigDecimal("40.00000000"), result.get(0).getAvgCost(), "DECIMAL(19,8) round-trips");
        assertNotNull(result.get(0).getUpdatedAt());
        assertEquals(0L, result.get(0).getVersion(), "fresh rows start at version 0");
    }

    @Test
    void findByUserAccountIdAndStockId_isThePairLookup() {
        assertTrue(positions.findByUserAccountIdAndStockId(alice.getId(), bolt.getId()).isPresent());
        assertTrue(positions.findByUserAccountIdAndStockId(alice.getId(), 9999L).isEmpty());
    }

    @Test
    void userStockPair_isUniqueAtTheDatabase() {
        PersistenceException ex = assertThrows(PersistenceException.class, () -> {
            em.persist(position(alice, bolt, 1, "1"));
            em.flush();
        });
        assertInstanceOf(ConstraintViolationException.class, ex.getCause());
    }

    @Test
    void updatingARow_bumpsItsVersion() {
        Position p = positions.findByUserAccountIdAndStockId(alice.getId(), bolt.getId()).orElseThrow();
        p.setNetQuantity(5);
        positions.saveAndFlush(p);
        em.clear();

        assertEquals(1L, positions.findByUserAccountIdAndStockId(alice.getId(), bolt.getId()).orElseThrow().getVersion());
    }

    @Test
    void staleUpdate_failsWithOptimisticLockException() {
        Position stale = positions.findByUserAccountIdAndStockId(alice.getId(), bolt.getId()).orElseThrow();
        em.detach(stale);

        // someone else commits first: version goes 0 -> 1
        Position fresh = positions.findByUserAccountIdAndStockId(alice.getId(), bolt.getId()).orElseThrow();
        fresh.setNetQuantity(3);
        positions.saveAndFlush(fresh);
        em.clear();

        // the stale copy (version 0) tries to write over it
        stale.setNetQuantity(99);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> positions.saveAndFlush(stale));
    }

    @Test
    void deleteByUserAccountId_removesOnlyThatUsersRows() {
        long removed = positions.deleteByUserAccountId(alice.getId());
        em.flush();

        assertEquals(2, removed);
        assertEquals(1, positions.count());
    }
}
