package furhatos.app.openaichat

import furhatos.app.openaichat.setting.Persona

class ResponseCache {
    private val cacheKind = mutableMapOf<String, String>()
    private val cacheEvil = mutableMapOf<String, String>()

    fun getCachedResponse(state: String, persona: Persona): String? {
        val key = "${state}_${persona.name}"
        return when (persona.name) {
            "Angel" -> cacheKind[key]
            "Demon" -> cacheEvil[key]
            else -> null
        }
    }

    fun putResponseInCache(state: String, persona: Persona, response: String) {
        val key = "${state}_${persona.name}"
        when (persona.name) {
            "Angel" -> cacheKind[key] = response
            "Demon" -> cacheEvil[key] = response
        }
    }
}