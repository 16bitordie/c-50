package com.i02bahip.c50android
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SignalingClient(private val url: String, private val roomName: String) {

    private val TAG = "SignalingClient"
    private var socket: Socket? = null

    // Callbacks para avisar a la interfaz de usuario / motor WebRTC
    var onConnected: (() -> Unit)? = null
    var onOfferReceived: ((JSONObject) -> Unit)? = null
    var onAnswerReceived: ((JSONObject) -> Unit)? = null
    var onIceCandidateReceived: ((JSONObject) -> Unit)? = null

    fun connect() {
        try {
            // Inicializar Socket.io
            socket = IO.socket(url)

            // Manejar eventos de conexión
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Conectado al servidor de señalización!")

                // Unirse a la sala nada más conectar (igual que hace la Raspberry)
                val joinData = JSONObject().apply {
                    put("room", roomName)
                    // En un futuro podríamos añadir un rol: put("role", "controller")
                }
                socket?.emit("join-room", roomName)
                onConnected?.invoke()
            }

            // Manejar la recepción de una oferta (si la Raspberry nos mandara una, que no es el caso normal)
            socket?.on("offer") { args ->
                Log.d(TAG, "Oferta recibida")
                val data = args[0] as JSONObject
                onOfferReceived?.invoke(data)
            }

            // Manejar la recepción de una respuesta (¡Esto es lo que esperamos de la Raspberry!)
            socket?.on("answer") { args ->
                Log.d(TAG, "Respuesta (Answer) recibida de la Raspberry")
                val data = args[0] as JSONObject
                onAnswerReceived?.invoke(data)
            }

            // Manejar candidatos ICE
            socket?.on("ice-candidate") { args ->
                Log.d(TAG, "Candidato ICE recibido")
                val data = args[0] as JSONObject
                onIceCandidateReceived?.invoke(data)
            }

            // Iniciar la conexión real
            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "URL del servidor no válida: ${e.message}")
        }
    }

    // Funciones para enviar datos A LA RASPBERRY
    fun sendOffer(offer: JSONObject) {
        Log.d(TAG, "Enviando Offer a la sala...")
        socket?.emit("offer", offer)
    }

    fun sendAnswer(answer: JSONObject) {
        Log.d(TAG, "Enviando Answer a la sala...")
        socket?.emit("answer", answer)
    }

    fun sendIceCandidate(candidate: JSONObject) {
        Log.d(TAG, "Enviando candidato ICE a la sala...")
        socket?.emit("ice-candidate", candidate)
    }

    fun disconnect() {
        socket?.emit("leave", JSONObject().apply { put("room", roomName) })
        socket?.disconnect()
        socket?.off()
    }
}
