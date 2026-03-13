package com.i02bahip.c50brain

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SignalingClient(private val url: String, private val roomName: String) {

    private val TAG = "C50-Brain-Signaling"
    private var socket: Socket? = null

    var onConnected: (() -> Unit)? = null
    var onOfferReceived: ((JSONObject) -> Unit)? = null
    var onAnswerReceived: ((JSONObject) -> Unit)? = null
    var onIceCandidateReceived: ((JSONObject) -> Unit)? = null

    fun connect() {
        try {
            socket = IO.socket(url)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Conectado al servidor de señalización (Modo Cerebro)")
                socket?.emit("join-room", roomName)
                onConnected?.invoke()
            }

            socket?.on("offer") { args ->
                Log.d(TAG, "Oferta recibida del piloto")
                val data = args[0] as JSONObject
                onOfferReceived?.invoke(data)
            }

            socket?.on("answer") { args ->
                Log.d(TAG, "Respuesta recibida")
                val data = args[0] as JSONObject
                onAnswerReceived?.invoke(data)
            }

            socket?.on("ice-candidate") { args ->
                Log.d(TAG, "Candidato ICE recibido del piloto")
                val data = args[0] as JSONObject
                onIceCandidateReceived?.invoke(data)
            }

            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "URL del servidor no válida: ${e.message}")
        }
    }

    fun sendOffer(offer: JSONObject) {
        socket?.emit("offer", offer)
    }

    fun sendAnswer(answer: JSONObject) {
        Log.d(TAG, "Enviando Answer al piloto...")
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
