package space.yaroslav.familybot.executors.command.nonpublic

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.meta.bots.AbsSender
import space.yaroslav.familybot.common.extensions.parseJson
import space.yaroslav.familybot.common.extensions.send
import space.yaroslav.familybot.executors.command.CommandExecutor
import space.yaroslav.familybot.getLogger
import space.yaroslav.familybot.models.router.ExecutorContext
import space.yaroslav.familybot.models.telegram.Command

@Component
class TopHistoryExecutor : CommandExecutor() {
    private val log = getLogger()
    private val lazyMamoeb: Lazy<Mamoeb?> = lazy {
        runCatching {
            RestTemplate()
                .getForEntity(
                    "https://raw.githubusercontent.com/Mi7teR/mamoeb3000/master/templates.json",
                    String::class.java
                ).body
                ?.parseJson<Mamoeb>()
        }
            .getOrElse {
                log.error("Can't get mamoeb", it)
                null
            }
    }

    override fun command(): Command {
        return Command.TOP_HISTORY
    }

    override fun execute(executorContext: ExecutorContext): suspend (AbsSender) -> Unit {
        val mamoeb = lazyMamoeb.value ?: return {}

        return { sender -> sender.send(executorContext, mamoeb.curses.random()) }
    }
}

data class Mamoeb(
    @JsonProperty("curses") val curses: List<String>
)
