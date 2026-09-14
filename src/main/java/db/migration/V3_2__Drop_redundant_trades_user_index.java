package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes the single-column index on trades(user_account_id): V3's composite (user_account_id, stock_id)
 * index leads with the same column and therefore serves the user foreign key as well.
 * <p>
 * Done in Java because the index has no fixed name: databases created by V1 call it
 * {@code idx_trades_user_account}, databases adopted from Hibernate ddl-auto carry an auto-generated
 * {@code FK...} name. The lookup goes by columns, so both cases are handled and a database that has
 * neither is left alone. (On adopted databases InnoDB already dropped the auto-generated FK index
 * itself when V3 created the composite — documented MySQL behaviour — so this is a no-op there.)
 * MySQL refuses the drop if no other index could serve the FK, which is the correct failure mode.
 */
public class V3_2__Drop_redundant_trades_user_index extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        List<String> redundant = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trades' "
                        + "GROUP BY INDEX_NAME "
                        + "HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'user_account_id'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                redundant.add(rs.getString(1));
            }
        }

        try (Statement statement = connection.createStatement()) {
            for (String indexName : redundant) {
                statement.execute("DROP INDEX `" + indexName + "` ON trades");
            }
        }
    }
}
