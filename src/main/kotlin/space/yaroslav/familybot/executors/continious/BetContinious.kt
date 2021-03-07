package space.yaroslav.familybot.executors.continious

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendDice
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.common.CommandByUser
import space.yaroslav.familybot.common.Pidor
import space.yaroslav.familybot.common.Pluralization
import space.yaroslav.familybot.common.User
import space.yaroslav.familybot.common.utils.send
import space.yaroslav.familybot.common.utils.toUser
import space.yaroslav.familybot.models.Command
import space.yaroslav.familybot.models.Phrase
import space.yaroslav.familybot.repos.ifaces.CommandHistoryRepository
import space.yaroslav.familybot.repos.ifaces.CommonRepository
import space.yaroslav.familybot.services.PidorCompetitionService
import space.yaroslav.familybot.services.dictionary.Dictionary
import space.yaroslav.familybot.telegram.BotConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Component
class BetContinious(
    private val dictionary: Dictionary,
    private val commandHistoryRepository: CommandHistoryRepository,
    private val pidorRepository: CommonRepository,
    private val pidorCompetitionService: PidorCompetitionService,
    private val botConfig: BotConfig
) : ContiniousConversation(botConfig) {
    private val diceNumbers = listOf(1, 2, 3, 4, 5, 6)
    override fun getDialogMessage() = dictionary.get(Phrase.BET_INITIAL_MESSAGE)

    override fun command() = Command.BET

    override fun canExecute(message: Message): Boolean {
        return message.isReply && message.replyToMessage.from.userName == botConfig.botname && message.replyToMessage.text ?: "" == getDialogMessage()
    }

    override fun execute(update: Update): suspend (AbsSender) -> Unit {
        val now = LocalDate.now()
        val user = update.toUser()
        val chatId = update.message.chatId
        val commands = commandHistoryRepository.get(
            user,
            LocalDateTime.of(LocalDate.of(now.year, now.month, 1), LocalTime.MIDNIGHT)
                .toInstant(ZoneOffset.UTC)
        )
        if (isBetAlreadyDone(commands)) {
            return {
                it.send(update, dictionary.get(Phrase.BET_ALREADY_WAS), shouldTypeBeforeSend = true)
            }
        }
        val number = extractBetNumber(update)
        if (number == null || number !in 1..3) {
            return {
                it.send(update, dictionary.get(Phrase.BET_BREAKING_THE_RULES_FIRST), shouldTypeBeforeSend = true)
                it.send(update, dictionary.get(Phrase.BET_BREAKING_THE_RULES_SECOND), shouldTypeBeforeSend = true)
            }
        }
        val winnableNumbers = diceNumbers.shuffled().subList(0, 3)
        return {
            it.send(
                update,
                "${dictionary.get(Phrase.BET_WINNABLE_NUMBERS_ANNOUNCEMENT)} ${formatWinnableNumbers(winnableNumbers)}",
                shouldTypeBeforeSend = true
            )
            it.send(update, dictionary.get(Phrase.BET_ZATRAVOCHKA), shouldTypeBeforeSend = true)
            val diceMessage = it.execute(SendDice(chatId.toString(), "\uD83C\uDFB2"))
            delay(4000)
            val isItWinner = winnableNumbers.contains(diceMessage.dice.value)
            if (isItWinner) {
                GlobalScope.launch { repeat(number) { pidorRepository.removePidorRecord(user) } }
                it.send(update, dictionary.get(Phrase.BET_WIN), shouldTypeBeforeSend = true)
                it.send(update, winEndPhrase(number), shouldTypeBeforeSend = true)
            } else {
                GlobalScope.launch { addPidorsMultiplyTimesWithDayShift(number, user) }
                it.send(update, dictionary.get(Phrase.BET_LOSE), shouldTypeBeforeSend = true)
                it.send(update, explainPhrase(number), shouldTypeBeforeSend = true)
            }
            delay(2000)
            pidorCompetitionService.pidorCompetition(update)?.invoke(it)
        }
    }

    private fun addPidorsMultiplyTimesWithDayShift(number: Int, user: User) {
        var i: Int = number
        while (i != 0) {
            pidorRepository.addPidor(
                Pidor(
                    user,
                    LocalDateTime
                        .now()
                        .toLocalDate()
                        .atStartOfDay()
                        .plusDays(i.toLong())
                        .toInstant(ZoneOffset.UTC)
                )
            )
            i--
        }
    }

    private fun extractBetNumber(update: Update) =
        update.message.text.split(" ")[0].toIntOrNull()

    private fun isBetAlreadyDone(commands: List<CommandByUser>) =
        commands.any { it.command == command() }

    private fun winEndPhrase(betNumber: Int): String {
        val plur = Pluralization.getPlur(betNumber)
        val winPhraseTemplate = dictionary.get(Phrase.BET_WIN_END)
        return when (plur) {
            Pluralization.ONE -> {
                winPhraseTemplate
                    .replace("$0", betNumber.toString())
                    .replace("$1", dictionary.get(Phrase.PLURALIZED_PIDORSKOE_ONE))
                    .replace("$2", dictionary.get(Phrase.PLURALIZED_OCHKO_ONE))
            }
            Pluralization.FEW -> {
                winPhraseTemplate
                    .replace("$0", betNumber.toString())
                    .replace("$1", dictionary.get(Phrase.PLURALIZED_PIDORSKOE_FEW))
                    .replace("$2", dictionary.get(Phrase.PLURALIZED_OCHKO_FEW))
            }
            Pluralization.MANY -> {
                winPhraseTemplate
                    .replace("$0", betNumber.toString())
                    .replace("$1", dictionary.get(Phrase.PLURALIZED_PIDORSKOE_MANY))
                    .replace("$2", dictionary.get(Phrase.PLURALIZED_OCHKO_MANY))
            }
        }
    }

    private fun explainPhrase(betNumber: Int): String {
        val plur = Pluralization.getPlur(betNumber)
        val explainTemplate = dictionary.get(Phrase.BET_EXPLAIN)
        return when (plur) {
            Pluralization.ONE -> {
                dictionary.get(Phrase.BET_EXPLAIN_SINGLE_DAY)
            }
            Pluralization.FEW -> {
                explainTemplate
                    .replace("$0", betNumber.toString())
                    .replace("$1", dictionary.get(Phrase.PLURALIZED_NEXT_FEW))
                    .replace("$2", dictionary.get(Phrase.PLURALIZED_DAY_FEW))
            }
            Pluralization.MANY -> {
                explainTemplate
                    .replace("$0", betNumber.toString())
                    .replace("$1", dictionary.get(Phrase.PLURALIZED_NEXT_MANY))
                    .replace("$2", dictionary.get(Phrase.PLURALIZED_DAY_MANY))
            }
        }
    }

    private fun formatWinnableNumbers(numbers: List<Int>): String {
        val orderedNumbers = numbers.sorted()
        return "${orderedNumbers[0]}, ${orderedNumbers[1]} и ${orderedNumbers[2]}"
    }
}
