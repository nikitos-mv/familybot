package space.yaroslav.familybot.executors.command.stats

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.common.extensions.DateConstants
import space.yaroslav.familybot.common.extensions.PluralizedWordsProvider
import space.yaroslav.familybot.common.extensions.bold
import space.yaroslav.familybot.common.extensions.formatTopList
import space.yaroslav.familybot.common.extensions.send
import space.yaroslav.familybot.executors.Configurable
import space.yaroslav.familybot.executors.command.CommandExecutor
import space.yaroslav.familybot.models.dictionary.Phrase
import space.yaroslav.familybot.models.router.ExecutorContext
import space.yaroslav.familybot.models.router.FunctionId
import space.yaroslav.familybot.models.telegram.Command
import space.yaroslav.familybot.models.telegram.Pidor
import space.yaroslav.familybot.repos.CommonRepository

@Component
class PidorStatsWorldExecutor(
    private val repository: CommonRepository
) : CommandExecutor(), Configurable {

    override fun getFunctionId(executorContext: ExecutorContext): FunctionId {
        return FunctionId.PIDOR
    }

    override fun command(): Command {
        return Command.STATS_WORLD
    }

    override fun execute(executorContext: ExecutorContext): suspend (AbsSender) -> Unit {
        
        val pidorsByChat = repository.getAllPidors(
            startDate = DateConstants.theBirthDayOfFamilyBot
        )
            .map(Pidor::user)
            .formatTopList(
                PluralizedWordsProvider(
                    one = { executorContext.phrase(Phrase.PLURALIZED_COUNT_ONE) },
                    few = { executorContext.phrase(Phrase.PLURALIZED_COUNT_FEW) },
                    many = { executorContext.phrase(Phrase.PLURALIZED_COUNT_MANY) }
                )
            )
            .take(100)

        val title = "${executorContext.phrase(Phrase.PIDOR_STAT_WORLD)}:\n".bold()
        return { it.send(executorContext, title + pidorsByChat.joinToString("\n"), enableHtml = true) }
    }
}
