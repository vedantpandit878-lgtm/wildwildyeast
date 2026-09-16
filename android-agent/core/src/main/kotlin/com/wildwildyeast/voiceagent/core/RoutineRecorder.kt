package com.wildwildyeast.voiceagent.core

/** Turns the actions of a successful AI run into replayable steps. */
class RoutineRecorder(initial: List<RoutineStep> = emptyList()) {
    private val recorded = initial.toMutableList()
    val steps: List<RoutineStep> get() = recorded.toList()

    fun record(action: AgentAction, before: ScreenState) {
        val step = toStep(action, before) ?: return
        recorded += step
    }

    companion object {
        fun toStep(action: AgentAction, before: ScreenState): RoutineStep? = when (action) {
            is AgentAction.Tap -> before.node(action.nodeId)?.let {
                RoutineStep(StepKind.TAP, before.packageName, TargetRef.of(it, before))
            }
            is AgentAction.LongPress -> before.node(action.nodeId)?.let {
                RoutineStep(StepKind.LONG_PRESS, before.packageName, TargetRef.of(it, before))
            }
            is AgentAction.TapAt -> RoutineStep(
                StepKind.TAP_AT, before.packageName,
                target = before.nodeAt(action.x, action.y)?.let { TargetRef.of(it, before) },
                x = action.x, y = action.y,
            )
            is AgentAction.TypeText -> RoutineStep(
                StepKind.TYPE_TEXT, before.packageName,
                target = action.nodeId?.let { id -> before.node(id)?.let { TargetRef.of(it, before) } },
                text = action.text, submit = action.submit,
            )
            is AgentAction.Scroll -> RoutineStep(
                StepKind.SCROLL, before.packageName,
                target = action.nodeId?.let { id -> before.node(id)?.let { TargetRef.of(it, before) } },
                direction = action.direction.name,
            )
            is AgentAction.Press -> RoutineStep(StepKind.PRESS, before.packageName, key = action.key.name)
            is AgentAction.OpenApp -> RoutineStep(StepKind.OPEN_APP, before.packageName, appName = action.appName)
            is AgentAction.Wait -> RoutineStep(StepKind.WAIT, before.packageName, seconds = action.seconds)
            AgentAction.Screenshot, is AgentAction.AskUser, is AgentAction.Finish -> null
        }
    }
}
