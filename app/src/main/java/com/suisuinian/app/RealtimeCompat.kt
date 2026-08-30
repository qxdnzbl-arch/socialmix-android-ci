package com.suisuinian.app

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.PostgresChangeFilter
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow as supabasePostgresChangeFlow
import kotlinx.coroutines.flow.Flow

inline fun <reified T : PostgresAction> RealtimeChannel.postgresChangeFlow(
    schema: String,
    noinline filter: PostgresChangeFilter.() -> Unit = {}
): Flow<T> = supabasePostgresChangeFlow(schema, filter)
