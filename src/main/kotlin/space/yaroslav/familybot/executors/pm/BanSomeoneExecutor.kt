package space.yaroslav.familybot.executors.pm

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.common.extensions.getMessageTokens
import space.yaroslav.familybot.common.extensions.key
import space.yaroslav.familybot.common.extensions.send
import space.yaroslav.familybot.models.router.ExecutorContext
import space.yaroslav.familybot.repos.CommonRepository
import space.yaroslav.familybot.services.misc.BanService
import space.yaroslav.familybot.telegram.BotConfig

@Component
class BanSomeoneExecutor(
    private val banService: BanService,
    private val commonRepository: CommonRepository,
    botConfig: BotConfig
) : OnlyBotOwnerExecutor(botConfig) {

    private val banPrefix = "ban|"

    override fun execute(executorContext: ExecutorContext): suspend (AbsSender) -> Unit {

        val command = executorContext.update.getMessageTokens(delimiter = "|")
        val identification = command[1]
        val isUnban = command.getOrNull(3) == "unban"
        val isForever = command.getOrNull(3) == "forever"
        val chats = commonRepository.getChats()

        val chat = chats.find { it.name == identification || it.id == identification.toLongOrNull() }

        val description = command[2]
        if (chat != null) {
            return {
                if (isUnban) {
                    banService.removeBan(chat.key())
                    it.send(executorContext, "Unbanned chat: $chat")
                } else {
                    banService.banChat(chat, description, isForever)
                    it.send(executorContext, "Banned chat: $chat")
                }
            }
        }

        val user = chats
            .asSequence()
            .flatMap { commonRepository.getUsers(it, activeOnly = true).asSequence() }
            .firstOrNull { identification in listOf(it.name, it.nickname, it.id.toString()) }

        if (user != null) {
            return {
                if (isUnban) {
                    banService.removeBan(user.key())
                    it.send(executorContext, "Unbanned user: $user")
                } else {
                    banService.banUser(user, description, isForever)
                    it.send(executorContext, "Banned user: $user")
                }
            }
        }

        return {
            it.send(executorContext, "No one found")
        }
    }

    override fun getMessagePrefix() = banPrefix
}
