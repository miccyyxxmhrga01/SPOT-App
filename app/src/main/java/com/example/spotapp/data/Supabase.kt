package com.example.spotapp.data

import android.util.Log
import com.example.spotapp.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/** SPOT's single Supabase client: Auth for sessions, PostgREST for data, Realtime for live screens, Functions for AI Assist. */
object Supabase {
    @OptIn(ExperimentalSerializationApi::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            // Row classes use camelCase properties; the database uses snake_case columns.
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
            // The library's error logs can include request headers and Realtime payloads with tokens.
            if (!BuildConfig.DEBUG) defaultLogLevel = LogLevel.NONE
            // Signup waits while Supabase hands the verification email to the SMTP server, which can
            // take ~10s. Giving up sooner makes Supabase cancel the signup without saving the account.
            requestTimeout = 30.seconds
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }.also { created ->
            created.auth.sessionStatus.onEach { status ->
                when (status) {
                    is SessionStatus.Authenticated -> account = status.session.user?.id
                    is SessionStatus.NotAuthenticated -> account = null
                    else -> Unit
                }
            }.launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
    val auth get() = client.auth
    val db get() = client.postgrest

    // On Android the SDK marks the session as loading whenever the app goes to the background and
    // reloads it on return. The member is still signed in, so their id is kept until a real sign-out.
    @Volatile private var account: String? = null

    /** The signed-in member's id, or null. Never throws, so identity checks are safe anywhere. */
    fun uid(): String? = runCatching {
        when (val status = client.auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> status.session.user?.id
            is SessionStatus.NotAuthenticated -> null
            else -> account
        }
    }.getOrNull()
    fun requireUid(): String = checkNotNull(uid()) { "Sign in to continue." }
}

/** Waits while the SDK restores the saved session, so requests carry the member's token rather than none. */
internal suspend fun awaitSession() {
    Supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
}

/** Stops a live query. Kept from the Firestore API so screens keep their disposal code. */
fun interface ListenerRegistration { fun remove() }

/** Where the next page starts: the last shown row's [id] and its keyset or offset [position]. */
data class PageCursor(val id: String, val position: Long)

/** A table, optionally narrowed to rows where [column] equals [value], whose changes refresh a live query. */
internal data class Watch(val table: String, val column: String? = null, val value: String? = null)

/**
 * Live queries re-run their fetch whenever a watched table changes, through Realtime or after
 * this device's own writes. Filtered Realtime subscriptions don't report deletes, so every
 * repository write calls [changed] for the tables it touched.
 */
internal object Live {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val channelIds = AtomicLong()
    private val shared = ConcurrentHashMap<Watch, Flow<Unit>>()
    private val local = MutableSharedFlow<String>(extraBufferCapacity = 64)

    fun changed(vararg tables: String) { tables.forEach { local.tryEmit(it) } }

    // One Realtime channel per distinct watch, shared by every screen that needs it.
    private fun changes(watch: Watch): Flow<Unit> = merge(
        local.filter { it == watch.table }.map { },
        shared.getOrPut(watch) { realtime(watch).shareIn(scope, SharingStarted.WhileSubscribed(5_000)) }
    )

    private fun realtime(watch: Watch): Flow<Unit> = callbackFlow {
        val channel = Supabase.client.channel("spot-${watch.table}-${channelIds.incrementAndGet()}")
        val events = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = watch.table
            if (watch.column != null) filter(watch.column, FilterOperator.EQ, watch.value.orEmpty())
        }
        val forwarding = launch { events.collect { trySend(Unit) } }
        channel.subscribe()
        awaitClose {
            forwarding.cancel()
            scope.launch { runCatching { Supabase.client.realtime.removeChannel(channel) } }
        }
    }.catch { /* Without Realtime, screens still load and refresh after this device's writes. */ }

    private fun triggers(watches: List<Watch>): Flow<Unit> =
        merge(*watches.map(::changes).toTypedArray()).onStart { emit(Unit) }.conflate()

    /** Emits a fresh result on start and after changes; a failed fetch ends the flow. */
    fun <T> flow(watches: List<Watch>, fetch: suspend () -> T): Flow<T> =
        triggers(watches).transform { awaitSession(); emit(fetch()); delay(REFRESH_SPACING_MS) }

    /** Like [flow], but failures are reported and the query keeps listening for the next change. */
    fun <T> query(watches: List<Watch>, fetch: suspend () -> T, onResult: (T) -> Unit,
        onError: (Exception) -> Unit): ListenerRegistration {
        val job = scope.launch {
            triggers(watches).collect {
                try { awaitSession(); onResult(fetch()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { onError(error) }
                delay(REFRESH_SPACING_MS)
            }
        }
        return ListenerRegistration { job.cancel() }
    }

    // Collapses bursts (for example many likes at once) into one refetch.
    private const val REFRESH_SPACING_MS = 300L
}

/**
 * Calls a SPOT database function. Messages the function raises for people (SQLSTATE P0001)
 * become [IllegalStateException]s, which screens already show as-is.
 */
internal suspend fun rpc(function: String, parameters: JsonObject = JsonObject(emptyMap())): PostgrestResult = try {
    awaitSession()
    Supabase.db.rpc(function, parameters)
} catch (error: PostgrestRestException) {
    if (error.code == "P0001") throw IllegalStateException(error.error, error) else throw error
}

/** Readable text for any repository failure; [fallback] covers unexpected errors. */
internal fun userMessage(error: Throwable, fallback: String): String = when (error) {
    is IllegalArgumentException, is IllegalStateException -> error.message ?: fallback
    is HttpRequestTimeoutException -> "SPOT's server took too long to respond. Please try again."
    is IOException -> "Check your internet connection and try again."
    else -> fallback
}

/**
 * Logs [error] without secrets. Supabase error messages embed the request's headers
 * (including the member's access token) and its URL, so only the type, status, code and
 * short reason are kept, along with the original stack traces.
 */
internal fun logFailure(tag: String, message: String, error: Throwable) {
    Log.w(tag, message, redacted(error))
}

internal fun redacted(error: Throwable, depth: Int = 0): Throwable {
    val name = error::class.java.simpleName
    val text = when (error) {
        is AuthRestException -> "$name(status=${error.statusCode}, code=${error.errorCode?.value ?: error.error}): ${error.errorDescription}"
        is PostgrestRestException -> "$name(status=${error.statusCode}, code=${error.code}): ${error.error}"
        is RestException -> "$name(status=${error.statusCode}): ${error.error}"
        // Other messages may still quote a request URL; drop its query, which holds filter values.
        else -> "$name: ${error.message.orEmpty().replace(Regex("""\?[^\s)]*"""), "").take(300)}"
    }
    val cause = error.cause?.takeIf { depth < 5 && it !== error }?.let { redacted(it, depth + 1) }
    return RedactedException(text, cause).apply { stackTrace = error.stackTrace }
}

private class RedactedException(message: String, cause: Throwable?) : Exception(message, cause)

internal fun millis(timestamp: String?): Long =
    timestamp?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

internal fun isoTimestamp(millis: Long): String = Instant.ofEpochMilli(millis).toString()

internal fun jsonStrings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

/** Converts app maps (the shapes Firestore used) to JSON for jsonb columns. */
internal fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
    is Iterable<*> -> JsonArray(value.map(::toJson))
    else -> JsonPrimitive(value.toString())
}

/** Converts jsonb values back to plain maps, lists and primitives for the existing model parsers. */
internal fun fromJson(value: JsonElement?): Any? = when (value) {
    null, JsonNull -> null
    is JsonObject -> value.mapValues { fromJson(it.value) }
    is JsonArray -> value.map(::fromJson)
    is JsonPrimitive -> if (value.isString) value.content
        else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.content
}
