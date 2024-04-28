package dev.storozhenko.familybot.core.telegram

import dev.storozhenko.familybot.BotConfig
import dev.storozhenko.familybot.common.extensions.toChat
import dev.storozhenko.familybot.common.extensions.toJson
import dev.storozhenko.familybot.common.extensions.toUser
import dev.storozhenko.familybot.core.keyvalue.EasyKeyValueService
import dev.storozhenko.familybot.core.keyvalue.models.ChatEasyKey
import dev.storozhenko.familybot.core.routers.PaymentRouter
import dev.storozhenko.familybot.core.routers.PollRouter
import dev.storozhenko.familybot.core.routers.ReactionsRouter
import dev.storozhenko.familybot.core.routers.Router
import dev.storozhenko.familybot.feature.settings.models.FunctionId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

@Component
class FamilyBot(
    val config: BotConfig,
    val router: Router,
    val pollRouter: PollRouter,
    val paymentRouter: PaymentRouter,
    val reactionsRouter: ReactionsRouter,
    val easyKeyValueService: EasyKeyValueService,
) : TelegramLongPollingBot(DefaultBotOptions().apply { allowedUpdates = botAllowedUpdates }, config.botToken) {

    companion object {

        private val botAllowedUpdates = listOf(
            "message",
            "edited_message",
            "callback_query",
            "shipping_query",
            "pre_checkout_query",
            "poll",
            "poll_answer",
            "my_chat_member",
            "chat_member",
            "message_reaction",
            "message_reaction_count"
        )
    }

    private val log = KotlinLogging.logger {}
    private val routerScope = CoroutineScope(Dispatchers.Default)
    private val channels = HashMap<Long, Channel<Update>>()

    override fun onUpdateReceived(tgUpdate: Update?) {
        val update = tgUpdate ?: throw InternalException("Update should not be null")
        if (update.hasPollAnswer()) {
            routerScope.launch { proceedPollAnswer(update) }
            return
        }

        if (update.hasPreCheckoutQuery()) {
            routerScope.launch { proceedPreCheckoutQuery(update).invoke(this@FamilyBot) }
            return
        }

        if (update.message?.hasSuccessfulPayment() == true) {
            routerScope.launch { proceedSuccessfulPayment(update).invoke(this@FamilyBot) }
            return
        }

        if (update.hasPoll()) {
            return
        }

        if (update.messageReaction != null) {
            routerScope.launch { proceedReaction(update) }
            return
        }
        if (update.hasMessage() || update.hasCallbackQuery() || update.hasEditedMessage()) {
            val chat = update.toChat()

            val channel = channels.computeIfAbsent(chat.id) { createChannel() }

            routerScope.launch { channel.send(update) }
        }
    }

    override fun getBotUsername(): String {
        return config.botName
    }

    private suspend fun proceed(update: Update) {
        try {
            val user = update.toUser()
            MDC.put("chat", "${user.chat.name}:${user.chat.id}")
            MDC.put("user", "${user.name}:${user.id}")
            router.processUpdate(update, this)
        } catch (e: TelegramApiRequestException) {
            val logMessage = "Telegram error: ${e.apiResponse}, ${e.errorCode}, update is ${update.toJson()}"
            if (e.errorCode in 400..499) {
                log.warn(e) { logMessage }
                if (e.apiResponse.contains("CHAT_WRITE_FORBIDDEN")) {
                    listOf(FunctionId.Chatting, FunctionId.Huificate, FunctionId.TalkBack)
                        .forEach { function ->
                            easyKeyValueService.put(
                                function,
                                ChatEasyKey(update.toChat().id),
                                false,
                            )
                        }
                }
            } else {
                log.error(e) { logMessage }
            }
        } catch (e: Exception) {
            log.error(e) { "Unexpected error, update is ${update.toJson()}" }
        } finally {
            MDC.clear()
        }
    }

    private fun proceedReaction(update: Update) {
        runCatching {
            reactionsRouter.proceed(update.messageReaction)
        }.onFailure {
            log.error(it) { "reactionRouter.proceed failed" }
        }
    }

    private fun proceedPollAnswer(update: Update) {
        runCatching {
            pollRouter.proceed(update)
        }.onFailure {
            log.warn(it) { "pollRouter.proceed failed" }
        }
    }

    private fun proceedPreCheckoutQuery(update: Update): suspend (AbsSender) -> Unit {
        return runCatching {
            paymentRouter.proceedPreCheckoutQuery(update)
        }.onFailure {
            log.error(it) { "paymentRouter.proceedPreCheckoutQuery failed" }
        }.getOrDefault { }
    }

    private fun proceedSuccessfulPayment(update: Update): suspend (AbsSender) -> Unit {
        return runCatching {
            paymentRouter.proceedSuccessfulPayment(update)
        }.onFailure {
            log.warn(it) { "paymentRouter.proceedSuccessfulPayment failed" }
        }.getOrDefault { }
    }

    private fun createChannel(): Channel<Update> {
        val channel = Channel<Update>()
        routerScope.launch {
            for (incomingUpdate in channel) {
                proceed(incomingUpdate)
            }
        }
        return channel
    }

    class InternalException(override val message: String) : RuntimeException(message)
}
