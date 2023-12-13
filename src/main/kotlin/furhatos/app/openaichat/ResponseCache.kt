package furhatos.app.openaichat

import furhatos.app.openaichat.setting.Persona
import java.io.File

class ResponseCache {
    private val cacheKind = mutableMapOf<String, String>()
    private val cacheEvil = mutableMapOf<String, String>()

    private val cacheKindFile = File("cacheKind.txt")
    private val cacheEvilFile = File("cacheEvil.txt")

    init {
        loadCacheFromFile()
    }

    private fun loadCacheFromFile() {
        if (cacheKindFile.exists()) {
            cacheKindFile.forEachLine {
                val (key, value) = it.split("::")
                cacheKind[key] = value
            }
        }

        if (cacheEvilFile.exists()) {
            cacheEvilFile.forEachLine {
                val (key, value) = it.split("::")
                cacheEvil[key] = value
            }
        }
    }

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

        val cache = when (persona.name) {
            "Angel" -> cacheKind
            "Demon" -> cacheEvil
            else -> return
        }

        if (key !in cache) {
            cache[key] = response
            val cacheFile = if (persona.name == "Angel") cacheKindFile else cacheEvilFile

            cacheFile.appendText("$key::$response\n")
        }
    }
}