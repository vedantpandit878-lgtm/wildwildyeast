package com.wildwildyeast.voiceagent.core

import com.anthropic.client.AnthropicClient
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaFallbackParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/** Claude via the Anthropic API, using tool use. History is append-only. */
class ClaudeBrain(
    private val client: AnthropicClient,
    private val config: AgentConfig = AgentConfig(),
) : Brain {
    override val name: String get() = config.model
    override val acceptsImages: Boolean = true

    private val history = mutableListOf<BetaMessageParam>()
    private var pendingToolUseIds: List<String> = emptyList()

    override suspend fun begin(goal: String, context: String?, apps: List<String>, screen: ScreenState) {
        history.clear()
        pendingToolUseIds = emptyList()
        val text = buildString {
            append("Task from the user (spoken): \"").append(goal).append("\"\n\n")
            context?.takeIf { it.isNotBlank() }?.let { append(it.trim()).append("\n\n") }
            if (apps.isNotEmpty()) append("Launchable apps on this phone: ").append(apps.take(200).joinToString(", ")).append("\n\n")
            append("Current screen:\n").append(screen.toPromptText())
        }
        history += userMessage(text, screen.screenshotPng)
    }

    override suspend fun next(): BrainTurn {
        val response = callModel()
        history += BetaMessageParam.builder()
            .role(BetaMessageParam.Role.ASSISTANT)
            .contentOfBetaContentBlockParams(response.content().map { it.toParam() })
            .build()
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString(" ").trim().ifBlank { null }
        val stop = response.stopReason().orElse(null)
        if (stop == BetaStopReason.REFUSAL) {
            val why = response.stopDetails().map { it.toString() }.orElse("")
            pendingToolUseIds = emptyList()
            return BrainTurn(emptyList(), text, refusal = "I can't help with that request. $why".trim())
        }
        val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
        pendingToolUseIds = toolUses.map { it.id() }
        return BrainTurn(
            actions = toolUses.map { AgentTools.parse(it.name(), it._input()) },
            text = text,
            truncated = stop == BetaStopReason.MAX_TOKENS,
        )
    }

    override suspend fun report(observations: List<Observation>) {
        val blocks = mutableListOf<BetaContentBlockParam>()
        val ids = pendingToolUseIds
        pendingToolUseIds = emptyList()
        observations.forEachIndexed { index, obs ->
            val body = render(obs)
            val id = ids.getOrNull(index)
            if (id != null) blocks += toolResult(id, body, obs.png, obs.isError)
            else blocks += BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(body).build())
        }
        // Every pending tool call needs a result, even if the loop had nothing to say.
        for (i in observations.size until ids.size) {
            blocks += toolResult(ids[i], "No result.", null, false)
        }
        if (blocks.isNotEmpty()) {
            history += BetaMessageParam.builder().role(BetaMessageParam.Role.USER).contentOfBetaContentBlockParams(blocks).build()
        }
    }

    private fun render(obs: Observation): String = buildString {
        append(obs.text)
        obs.screen?.let { append("\nScreen after the action:\n").append(it.toPromptText()) }
    }

    private suspend fun callModel(): BetaMessage = withContext(Dispatchers.IO) {
        val builder = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(config.maxTokens)
            .systemOfBetaTextBlockParams(
                listOf(
                    BetaTextBlockParam.builder()
                        .text(SYSTEM_PROMPT)
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
            .messages(history)
        AgentTools.definitions().forEach { builder.addTool(it) }
        config.fallbackModel?.let { fb ->
            builder
                .fallbacksOfFallbackParams(listOf(BetaFallbackParam.builder().model(fb).build()))
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
        }
        client.beta().messages().create(builder.build())
    }

    private fun userMessage(text: String, png: ByteArray?): BetaMessageParam {
        val blocks = mutableListOf<BetaContentBlockParam>(
            BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(text).build()),
        )
        png?.let { blocks += BetaContentBlockParam.ofImage(imageBlock(it)) }
        return BetaMessageParam.builder().role(BetaMessageParam.Role.USER).contentOfBetaContentBlockParams(blocks).build()
    }

    private fun toolResult(toolUseId: String, text: String, png: ByteArray?, isError: Boolean): BetaContentBlockParam {
        val blocks = mutableListOf(
            BetaToolResultBlockParam.Content.Block.ofText(BetaTextBlockParam.builder().text(text).build()),
        )
        png?.let { blocks += BetaToolResultBlockParam.Content.Block.ofImage(imageBlock(it)) }
        val builder = BetaToolResultBlockParam.builder().toolUseId(toolUseId).contentOfBlocks(blocks)
        if (isError) builder.isError(true)
        return BetaContentBlockParam.ofToolResult(builder.build())
    }

    private fun imageBlock(png: ByteArray): BetaImageBlockParam =
        BetaImageBlockParam.builder()
            .source(
                BetaBase64ImageSource.builder()
                    .mediaType(BetaBase64ImageSource.MediaType.IMAGE_PNG)
                    .data(Base64.getEncoder().encodeToString(png))
                    .build(),
            )
            .build()

    companion object {
        val SYSTEM_PROMPT = AgentLoop.SYSTEM_PROMPT
    }
}
