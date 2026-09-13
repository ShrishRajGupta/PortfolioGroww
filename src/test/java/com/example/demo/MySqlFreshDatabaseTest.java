package com.example.demo;

import com.example.demo.dto.PortfolioHoldingDTO;
import com.example.demo.dto.PortfolioResponseDTO;
import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.exception.InsufficientPositionException;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.repository.PositionRepository;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.PositionService;
import com.example.demo.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The provisioning path a brand-new deployment takes: an EMPTY MySQL 8 database, every Flyway
 * migration (SQL and Java) applied from scratch, Hibernate validating the mapping, MySQL enforcing
 * the constraints, and the full trade lifecycle on top. Needs Docker; skipped where it is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.outbox.relay-enabled=false",
        "app.kafka.consumer-enabled=false"
})
class MySqlFreshDatabaseTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("portfolio")
            .withUsername("app")
            .withPassword("app");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private PortfolioService portfolioService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private PositionRepository positions;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StockRepository stocks;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void flywayAppliesEveryMigrationOnAFreshDatabase_andHibernateValidatesTheMapping() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertEquals(List.of("1", "2", "3", "3.1", "3.2", "4"), applied);
        // The context started with ddl-auto=validate: reaching this line means every entity matched the schema.
    }

    @Test
    void v32_dropsTheSingleColumnUserIndexThatV1Created_andKeepsTheComposites() {
        List<String> indexes = jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trades'", String.class);

        assertTrue(indexes.contains("idx_trades_user_stock"), indexes.toString());
        assertTrue(indexes.contains("idx_trades_user_created"), indexes.toString());
        assertFalse(indexes.contains("idx_trades_user_account"), "V3.2 must drop the V1 single-column index: " + indexes);
    }

    @Test
    void mysqlEnforcesTheDomainConstraints() {
        UserAccount user = users.save(UserAccount.builder().name("Con").email("con@example.com").build());
        Stock stock = stocks.save(Stock.builder().name("CONS").openPrice(bd("1")).closePrice(bd("1"))
                .highPrice(bd("1")).lowPrice(bd("1")).settlementPrice(bd("1")).build());

        String insert = "INSERT INTO trades (user_account_id, stock_id, trade_type, quantity, price) VALUES (?, ?, ?, ?, ?)";
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(insert, user.getId(), stock.getId(), "HOLD", 1, bd("1")), "CHECK trade_type");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(insert, user.getId(), stock.getId(), "BUY", 0, bd("1")), "CHECK quantity > 0");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO user_account (name, email) VALUES (?, ?)", "Dup", "con@example.com"), "UNIQUE email");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO positions (user_account_id, stock_id, net_quantity, avg_cost, realized_pnl) VALUES (?, ?, ?, ?, ?)",
                        user.getId(), stock.getId(), -1, bd("1"), bd("0")), "CHECK net_quantity >= 0");
    }

    @Test
    void tradeLifecycle_onRealMySql() {
        UserAccount user = users.save(UserAccount.builder().name("Life").email("life@example.com").build());
        Stock stock = stocks.save(Stock.builder().name("LIFE").openPrice(bd("120")).closePrice(bd("120.0000"))
                .highPrice(bd("120")).lowPrice(bd("120")).settlementPrice(bd("120")).build());

        TradeResponseDTO buy = tradeService.recordTrade(
                new TradeRequestDTO("life-1", user.getId(), stock.getId(), TradeType.BUY, 10, bd("100")));
        TradeResponseDTO replay = tradeService.recordTrade(
                new TradeRequestDTO("life-1", user.getId(), stock.getId(), TradeType.BUY, 10, bd("100")));
        assertEquals(buy.getTradeId(), replay.getTradeId(), "idempotent replay");
        assertEquals("Trade already recorded", replay.getMessage());

        tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.SELL, 4));  // fills at close 120

        Position position = positions.findByUserAccountIdAndStockId(user.getId(), stock.getId()).orElseThrow();
        assertEquals(6, position.getNetQuantity());
        assertEquals(bd("100.00000000"), position.getAvgCost());
        assertEquals(bd("80.00000000"), position.getRealizedPnl(), "4 x (120 - 100)");
        assertEquals(1L, position.getVersion(), "insert + one update");

        PortfolioResponseDTO portfolio = portfolioService.getPortfolio(user.getId());
        PortfolioHoldingDTO holding = portfolio.getHoldings().get(0);
        assertEquals(bd("600.0000"), holding.getCostBasis());
        assertEquals(bd("720.0000"), holding.getMarketValue());
        assertEquals(bd("120.0000"), holding.getUnrealizedPnl());
        assertEquals(bd("80.0000"), portfolio.getTotalRealizedPnl());
        assertEquals(bd("200.0000"), portfolio.getTotalPnl());

        InsufficientPositionException oversell = assertThrows(InsufficientPositionException.class,
                () -> tradeService.recordTrade(new TradeRequestDTO(user.getId(), stock.getId(), TradeType.SELL, 7)));
        assertTrue(oversell.getMessage().endsWith("only 6 held"));

        assertTrue(positionService.drift(user.getId()).isEmpty(), "positions agree with the ledger");
        assertEquals(1, outbox.findByAggregateTypeAndAggregateId("Trade", buy.getTradeId()).size(),
                "exactly one outbox row for the BUY; the idempotent replay added none");
        assertEquals(2, outbox.countByPublishedAtIsNull(),
                "one row per booked trade (BUY + SELL), both still pending because the relay is off here");
    }
}
