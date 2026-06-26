package me.matsumo.pholio.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource
import me.matsumo.pholio.config.AppConfig

/**
 * SQLite への接続と lifecycle を管理する。
 */
class Database(
    config: AppConfig,
) : AutoCloseable {
    private val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            maximumPoolSize = 4
            minimumIdle = 1
            connectionTimeout = 10_000
            idleTimeout = 60_000
            maxLifetime = 600_000
            poolName = "pholio-sqlite"
        },
    )

    init {
        withConnection { connection ->
            Migrations(connection).migrate()
        }
    }

    /**
     * connection を借りて処理を実行する。
     */
    fun <T> withConnection(block: (Connection) -> T): T {
        dataSource.connection.use { connection ->
            connection.configure()

            return block(connection)
        }
    }

    /**
     * JDBC DataSource を返す。
     */
    fun dataSource(): DataSource = dataSource

    override fun close() {
        dataSource.close()
    }

    private fun Connection.configure() {
        createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA busy_timeout = 5000")
            statement.execute("PRAGMA temp_store = MEMORY")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
        }
    }
}
