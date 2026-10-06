package com.wildwildyeast.voiceagent.core

/** What the phone reports back after an action (or a note from the loop). */
data class Observation(
    val text: String,
    val screen: ScreenState? = null,
    val png: ByteArray? = null,
    val isError: Boolean = false,
)

/** One reply from a brain: the actions it wants performed, in order. A failed Result is an unparseable request. */
data class BrainTurn(
    val actions: List<Result<AgentAction>>,
    val text: String? = null,
    /** Set when the model declined the task outright. */
    val refusal: String? = null,
    /** Set when the reply was cut off before it finished. */
    val truncated: Boolean = false,
)

/**
 * The planner. Implementations keep their own conversation state. The loop
 * calls [begin] once, then alternates [next] and [report] until finished.
 */
interface Brain {
    val name: String

    /** Whether screenshots are useful to this brain. */
    val acceptsImages: Boolean

    suspend fun begin(goal: String, context: String?, apps: List<String>, screen: ScreenState)

    suspend fun next(): BrainTurn

    /** One observation per action of the previous turn, in order; or a single note when no actions were pending. */
    suspend fun report(observations: List<Observation>)
}
