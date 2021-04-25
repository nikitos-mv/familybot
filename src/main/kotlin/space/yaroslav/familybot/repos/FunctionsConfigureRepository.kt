package space.yaroslav.familybot.repos

import io.micrometer.core.annotation.Timed
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import space.yaroslav.familybot.common.Chat
import space.yaroslav.familybot.common.utils.key
import space.yaroslav.familybot.models.FunctionId
import space.yaroslav.familybot.services.settings.EasyKeyValueRepository

@Component
@Primary
class FunctionsConfigureRepository(
    private val keyValueRepository: EasyKeyValueRepository
) {

    @Timed("repository.RedisFunctionsConfigureRepository.isEnabled")
    fun isEnabled(id: FunctionId, chat: Chat): Boolean {
        return isEnabledInternal(id, chat)
    }

    @Timed("repository.RedisFunctionsConfigureRepository.switch")
    suspend fun switch(id: FunctionId, chat: Chat) {
        switchInternal(id, chat)
    }

    suspend fun setStatus(id: FunctionId, chat: Chat, isEnabled: Boolean) {
        if (isEnabledInternal(id, chat) != isEnabled) {
            switchInternal(id, chat)
        }
    }

    private fun isEnabledInternal(
        id: FunctionId,
        chat: Chat
    ): Boolean {
        return keyValueRepository.get(id.easySetting, chat.key()) ?: true
    }

    private fun switchInternal(
        id: FunctionId,
        chat: Chat
    ) {
        val currentValue = keyValueRepository.get(id.easySetting, chat.key()) ?: true
        keyValueRepository.put(id.easySetting, chat.key(), currentValue.not())
    }
}
