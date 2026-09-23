package io.horizontalsystems.thorchainkit.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.thorchainkit.models.CoinTransfer
import java.math.BigInteger

class RoomTypeConverters {

    private val gson = Gson()

    @TypeConverter
    fun bigIntegerFromString(string: String): BigInteger = BigInteger(string)

    @TypeConverter
    fun bigIntegerToString(bigInteger: BigInteger): String = bigInteger.toString()

    @TypeConverter
    fun coinTransfersFromString(string: String): List<CoinTransfer> {
        val type = object : TypeToken<List<CoinTransfer>>() {}.type
        return gson.fromJson(string, type)
    }

    @TypeConverter
    fun coinTransfersToString(list: List<CoinTransfer>): String = gson.toJson(list)
}
