package utils

import com.google.gson.Gson
import com.rethinkdb.net.Cursor
import commands.games.CoinflipGame
import commands.info.formatter
import main.conn
import main.r
import net.dv8tion.jda.core.entities.User
import org.json.simple.JSONObject
import java.util.*
import kotlin.collections.HashMap
import java.util.LinkedHashMap
import java.util.function.Supplier
import java.util.stream.Collectors



private val random = Random()
private val gsons = listOf(Gson(), Gson(), Gson(), Gson(), Gson(), Gson(), Gson(), Gson(), Gson(), Gson())

fun <E> MutableList<E>.shuffle() : MutableList<E> {
    Collections.shuffle(this)
    return this
}

fun List<String>.stringify() : String {
    if (size == 0) return "none"
    val builder = StringBuilder()
    forEach { builder.append(it + ", ") }
    return builder.removeSuffix(", ").toString()
}

fun List<String>.concat() : String {
    val builder = StringBuilder()
    forEach { builder.append("$it ") }
    return builder.removeSuffix(" ").toString()
}

fun Any.insert(table: String) {
    r.table(table).insert(r.json(getGson().toJson(this))).runNoReply(conn)
}


fun <T> asPojo(map: HashMap<*, *>?, tClass: Class<T>): T? {
    return getGson().fromJson(JSONObject.toJSONString(map), tClass)
}

fun <T> Any.queryAsArrayList(t: Class<T>): MutableList<T?> {
    val cursor = this as Cursor<HashMap<*, *>>
    val tS = mutableListOf<T?>()
    cursor.forEach { hashMap -> tS.add(asPojo(hashMap, t)) }
    return tS
}

fun Int.format() : String {
    return formatter.format(this)
}

fun Long.format() : String {
    return formatter.format(this)
}

fun String.shortenIf(numChars: Int) : String {
    if (length <= numChars) return this
    else return substring(0, numChars)
}

fun <K> MutableMap<K, Int>.incrementValue(key : K) : Int {
    val value = putIfAbsent(key, 0) ?: 0
    replace(key, value + 1)
    return value
}

fun PlayerData.update() {
    r.table("playerData").get(id).update(r.json(getGson().toJson(this))).runNoReply(conn)
}

fun GuildData.update() {
    r.table("guilds").get(id).update(r.json(getGson().toJson(this))).runNoReply(conn)
}

fun Long.formatMinSec() : String {
    val seconds = this % 60
    val minutes = (this % 3600) / 60
    if (minutes.compareTo(0) == 0) return "$seconds seconds"
    else return "$minutes minutes, $seconds seconds"
}

fun getGson(): Gson {
    return gsons[random.nextInt(gsons.size)]
}

fun MutableList<CoinflipGame.Round>.mapScores() : MutableMap<String, Int> {
    val scores = hashMapOf<String, Int>()
    forEach { round ->
        round.winners.forEach {
            scores.putIfAbsent(it, 0)
            scores.replace(it, scores[it]!! + 1)
        }
        round.losers.forEach {
            scores.putIfAbsent(it, 0)
        }
    }
    return JavaUtils.sortByValue(scores)
}

fun Any.toJson() : String {
    return getGson().toJson(this)
}

fun <T> MutableList<T>.without(t: T) : MutableList<T> {
    this.remove(t)
    return this
}

/**
 * Credit mfulton26 @ https://stackoverflow.com/questions/34498368/kotlin-convert-large-list-to-sublist-of-set-partition-size
 */
fun <T> List<T>.collate(size: Int): List<List<T>> {
    require(size > 0)
    return if (isEmpty()) {
        emptyList()
    } else {
        (0..lastIndex / size).map {
            val fromIndex = it * size
            val toIndex = Math.min(fromIndex + size, this.size)
            subList(fromIndex, toIndex)
        }
    }
}