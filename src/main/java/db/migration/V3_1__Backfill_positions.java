package db.migration;

import com.example.demo.service.Impl.AverageCostPosition;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the positions table from the existing trade ledger.
 * <p>
 * A weighted-average cost depends on the order trades happened in, so it cannot be produced by a
 * single SQL aggregate; this migration replays each (user, stock) ledger chronologically with the
 * same {@link AverageCostPosition} arithmetic the application uses, then inserts one row per pair.
 * Plain JDBC only — Flyway runs this before the Spring context is fully up.
 */
public class V3_1__Backfill_positions extends BaseJavaMigration {

    private record Key(long userId, long stockId) { }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        Map<Key, AverageCostPosition> book = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT user_account_id, stock_id, trade_type, quantity, price "
                             + "FROM trades ORDER BY user_account_id, stock_id, created_at, id")) {
            while (rs.next()) {
                Key key = new Key(rs.getLong(1), rs.getLong(2));
                AverageCostPosition position = book.computeIfAbsent(key, k -> new AverageCostPosition());
                int quantity = rs.getInt(4);
                BigDecimal price = rs.getBigDecimal(5);
                if ("BUY".equals(rs.getString(3))) {
                    position.applyBuy(quantity, price);
                } else {
                    position.applySell(quantity, price);
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO positions (user_account_id, stock_id, net_quantity, avg_cost, realized_pnl, version) "
                        + "VALUES (?, ?, ?, ?, ?, 0)")) {
            for (Map.Entry<Key, AverageCostPosition> entry : book.entrySet()) {
                AverageCostPosition p = entry.getValue();
                insert.setLong(1, entry.getKey().userId());
                insert.setLong(2, entry.getKey().stockId());
                insert.setLong(3, p.getNetQuantity());
                insert.setBigDecimal(4, p.getAvgCost());
                insert.setBigDecimal(5, p.getRealizedPnl());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
