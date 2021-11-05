package space.yaroslav.familybot.executors.command.stats

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.common.extensions.bold
import space.yaroslav.familybot.common.extensions.capitalized
import space.yaroslav.familybot.common.extensions.dropLastDelimiter
import space.yaroslav.familybot.common.extensions.italic
import space.yaroslav.familybot.common.extensions.send
import space.yaroslav.familybot.common.extensions.startOfCurrentMonth
import space.yaroslav.familybot.common.extensions.toRussian
import space.yaroslav.familybot.executors.Configurable
import space.yaroslav.familybot.executors.command.CommandExecutor
import space.yaroslav.familybot.models.dictionary.Phrase
import space.yaroslav.familybot.models.dictionary.Pluralization
import space.yaroslav.familybot.models.router.ExecutorContext
import space.yaroslav.familybot.models.router.FunctionId
import space.yaroslav.familybot.models.telegram.Command
import space.yaroslav.familybot.models.telegram.Pidor
import space.yaroslav.familybot.models.telegram.User
import space.yaroslav.familybot.repos.CommonRepository
import space.yaroslav.familybot.telegram.FamilyBot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class TopPidorsByMonthsExecutor(
    private val commonRepository: CommonRepository
) : CommandExecutor(), Configurable {

    override fun getFunctionId(executorContext: ExecutorContext): FunctionId {
        return FunctionId.PIDOR
    }

    private class PidorStat(val user: User, val position: Int)

    private val delimiter = "\n========================\n"

    override fun command(): Command {
        return Command.LEADERBOARD
    }

    override fun execute(executorContext: ExecutorContext): suspend (AbsSender) -> Unit {
        
        val result = commonRepository
            .getPidorsByChat(executorContext.chat)
            .filter { it.date.isBefore(startOfCurrentMonth()) }
            .groupBy { map(it.date) }
            .mapValues { monthPidors -> calculateStats(monthPidors.value) }
            .toSortedMap()
            .asIterable()
            .reversed()
            .map(formatLeaderBoard(executorContext))
        if (result.isEmpty()) {
            return {
                it.send(executorContext, executorContext.phrase(Phrase.LEADERBOARD_NONE))
            }
        }
        val message = "${executorContext.phrase(Phrase.LEADERBOARD_TITLE)}:\n".bold()
        return {
            it.send(executorContext, message + "\n" + result.joinToString(delimiter), enableHtml = true)
        }
    }

    private fun formatLeaderBoard(executorContext: ExecutorContext): (Map.Entry<LocalDate, PidorStat>) -> String = {
        val month = it.key.month.toRussian().capitalized()
        val year = it.key.year
        val userName = it.value.user.name.dropLastDelimiter()
        val position = it.value.position
        val leaderboardPhrase = getLeaderboardPhrase(
            Pluralization.getPlur(it.value.position), executorContext
        )
        "$month, $year:\n".italic() + "$userName, $position $leaderboardPhrase"
    }

    private fun map(instant: Instant): LocalDate {
        val time = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return LocalDate.of(time.year, time.month, 1)
    }

    private fun calculateStats(pidors: List<Pidor>): PidorStat {
        val pidor = pidors
            .groupBy { it.user }
            .maxByOrNull { it.value.size }
            ?: throw FamilyBot.InternalException("List of pidors should be not empty to calculate stats")
        return PidorStat(pidor.key, pidors.count { it.user == pidor.key })
    }

    private fun getLeaderboardPhrase(pluralization: Pluralization, executorContext: ExecutorContext): String {
        return when (pluralization) {
            Pluralization.ONE -> executorContext.phrase(Phrase.PLURALIZED_LEADERBOARD_ONE)
            Pluralization.FEW -> executorContext.phrase(Phrase.PLURALIZED_LEADERBOARD_FEW)
            Pluralization.MANY -> executorContext.phrase(Phrase.PLURALIZED_LEADERBOARD_MANY)
        }
    }
}
