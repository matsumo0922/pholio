package me.matsumo.pholio.db

import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * JDBC の小さな helper 群。
 */
object Jdbc {
    /**
     * nullable long を statement に設定する。
     */
    fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) {
            setObject(index, null)
        } else {
            setLong(index, value)
        }
    }

    /**
     * nullable int を statement に設定する。
     */
    fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) {
            setObject(index, null)
        } else {
            setInt(index, value)
        }
    }

    /**
     * nullable double を statement に設定する。
     */
    fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
        if (value == null) {
            setObject(index, null)
        } else {
            setDouble(index, value)
        }
    }

    /**
     * nullable string を statement に設定する。
     */
    fun PreparedStatement.setNullableString(index: Int, value: String?) {
        setString(index, value)
    }

    /**
     * nullable long を ResultSet から取得する。
     */
    fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)

        return if (wasNull()) null else value
    }

    /**
     * nullable int を ResultSet から取得する。
     */
    fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)

        return if (wasNull()) null else value
    }

    /**
     * nullable double を ResultSet から取得する。
     */
    fun ResultSet.getNullableDouble(column: String): Double? {
        val value = getDouble(column)

        return if (wasNull()) null else value
    }
}
