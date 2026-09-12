package com.example.demo.repository;

import com.example.demo.entity.Stock;
import com.example.demo.entity.Trade;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real JPA/SQL coverage of the repository queries on an in-memory H2 database (the MySQL Flyway
 * scripts are skipped; Hibernate creates the schema from the entity mapping). JPQL is portable, so
 * what passes here matches MySQL; the MySQL-specific constraints are exercised against MySQL in the
 * Testcontainers suite.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class TradeRepositoryJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TradeRepository trades;

    private UserAccount alice;
    private UserAccount bob;
    private Stock acme;
    private Stock bolt;

    @BeforeEach
    void seed() {
        alice = em.persist(UserAccount.builder().name("Alice").email("alice@example.com").build());
        bob = em.persist(UserAccount.builder().name("Bob").email("bob@example.com").build());
        acme = em.persist(stock("ACME", "120"));
        bolt = em.persist(stock("BOLT", "50"));

        em.persist(trade(alice, acme, TradeType.BUY, 10, "100", "a-1"));
        em.persist(trade(alice, acme, TradeType.SELL, 4, "110", null));
        em.persist(trade(alice, bolt, TradeType.BUY, 5, "45", null));
        em.persist(trade(bob, acme, TradeType.BUY, 1, "100", null));
        em.flush();
        em.clear();
    }

    private static Stock stock(String name, String price) {
        BigDecimal p = new BigDecimal(price);
        return Stock.builder().name(name).openPrice(p).closePrice(p).highPrice(p).lowPrice(p).settlementPrice(p).build();
    }

    private static Trade trade(UserAccount u, Stock s, TradeType type, int qty, String price, String key) {
        return Trade.builder().userAccount(u).stock(s).tradeType(type).quantity(qty)
                .price(new BigDecimal(price)).clientTradeId(key).build();
    }

    @Test
    void findAllForPortfolio_returnsOnlyThatUsersTrades_inBookingOrder_withStocksLoaded() {
        List<Trade> result = trades.findAllForPortfolio(alice.getId());

        assertEquals(3, result.size());
        assertEquals(List.of(TradeType.BUY, TradeType.SELL, TradeType.BUY),
                result.stream().map(Trade::getTradeType).toList());
        assertEquals(List.of("ACME", "ACME", "BOLT"),
                result.stream().map(t -> t.getStock().getName()).toList());
        assertTrue(result.get(0).getId() < result.get(1).getId() && result.get(1).getId() < result.get(2).getId());
        assertEquals(new BigDecimal("100.0000"), result.get(0).getPrice(), "DECIMAL(19,4) round-trips exactly");
        assertNotNull(result.get(0).getCreatedAt(), "@CreationTimestamp populated on insert");
    }

    @Test
    void findAllForPortfolio_unknownUser_isEmpty() {
        assertTrue(trades.findAllForPortfolio(9999L).isEmpty());
    }

    @Test
    void netPosition_isBuysMinusSells_perUserAndStock() {
        assertEquals(6, trades.netPosition(alice.getId(), acme.getId()));
        assertEquals(5, trades.netPosition(alice.getId(), bolt.getId()));
        assertEquals(1, trades.netPosition(bob.getId(), acme.getId()));
    }

    @Test
    void netPosition_withNoTrades_isZeroNotNull() {
        assertEquals(0, trades.netPosition(bob.getId(), bolt.getId()));
    }

    @Test
    void findByClientTradeId_roundTrips() {
        assertTrue(trades.findByClientTradeId("a-1").isPresent());
        assertTrue(trades.findByClientTradeId("nope").isEmpty());
    }

    @Test
    void clientTradeId_isUniqueAtTheDatabase() {
        trades.saveAndFlush(trade(bob, bolt, TradeType.BUY, 1, "1", "dup"));

        assertThrows(DataIntegrityViolationException.class,
                () -> trades.saveAndFlush(trade(bob, bolt, TradeType.BUY, 1, "1", "dup")));
    }

    // Flushing through TestEntityManager bypasses Spring's repository proxy, so the raw JPA exception surfaces
    // (the saveAndFlush test above shows the translated DataIntegrityViolationException).
    @Test
    void stockName_andUserEmail_areUniqueAtTheDatabase() {
        PersistenceException dupStock = assertThrows(PersistenceException.class, () -> {
            em.persist(stock("ACME", "1"));
            em.flush();
        });
        assertInstanceOf(ConstraintViolationException.class, dupStock.getCause());
        em.clear();
        PersistenceException dupEmail = assertThrows(PersistenceException.class, () -> {
            em.persist(UserAccount.builder().name("Dup").email("alice@example.com").build());
            em.flush();
        });
        assertInstanceOf(ConstraintViolationException.class, dupEmail.getCause());
    }
}
