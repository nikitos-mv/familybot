package space.yaroslav.familybot.executors.continious

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.executors.command.nonpublic.QUOTE_MESSAGE
import space.yaroslav.familybot.models.router.ExecutorContext
import space.yaroslav.familybot.models.telegram.Command
import space.yaroslav.familybot.repos.QuoteRepository
import space.yaroslav.familybot.telegram.BotConfig

@Component
class QuoteContiniousExecutor(
    private val quoteRepository: QuoteRepository,
    botConfig: BotConfig
) : ContiniousConversationExecutor(botConfig) {

    override fun command(): Command {
        return Command.QUOTE_BY_TAG
    }

    override fun getDialogMessages(context: ExecutorContext): Set<String> {
        return setOf(QUOTE_MESSAGE)
    }

    override fun execute(context: ExecutorContext): suspend (AbsSender) -> Unit {
        return {
            val callbackQuery = context.update.callbackQuery
            it.execute(AnswerCallbackQuery(callbackQuery.id))
            it.execute(
                (
                    SendMessage(
                        callbackQuery.message.chatId.toString(),
                        quoteRepository.getByTag(callbackQuery.data) ?: "Такого тега нет, идите нахуй"
                    )
                    )
            )
        }
    }
}
