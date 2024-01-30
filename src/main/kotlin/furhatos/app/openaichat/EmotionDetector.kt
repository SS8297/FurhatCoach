package furhatos.app.openaichat

import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import com.google.gson.JsonParser
import java.util.*

class EmotionDetector {
    private val host = "localhost"
    private val _port = 9999
    private val timeout = 5000
    private val readTimeout = 5000
    private var lastEmotion: String? = null

    fun getEmotion(): String {
        var response = ""

        try {
            println("Attempting to connect to the server...")
            Socket().apply {
                connect(InetSocketAddress(host, _port), timeout)
                soTimeout = readTimeout
                println("Connected to the server.")

                getOutputStream().use { output ->
                    PrintWriter(output, true).use { out ->
                        getInputStream().use { input ->
                            BufferedReader(InputStreamReader(input)).use { reader ->
                                println("Sending request to the server...")
                                out.println("Request emotion")
                                println("Awaiting response from the server...")
                                response = reader.readLine() ?: "No response received"
                                println("Response received: $response")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
        val jsonResponse = JsonParser.parseString(response).asJsonObject
        val state = jsonResponse.get("patientState").asString
        // Check if the emotion has changed
        if (state.equals(lastEmotion, ignoreCase = true)) {
            println("Same emotion as before, no API call needed")
            return state
        }
        lastEmotion = state
        return state.uppercase(Locale.getDefault())
    }
}
