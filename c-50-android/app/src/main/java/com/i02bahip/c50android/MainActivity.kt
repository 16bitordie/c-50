package com.i02bahip.c50android
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier 
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning // Test with something known
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            initApp()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initApp()
        }
    }


    // Instancias de nuestros gestores de conexión
    private lateinit var signalingClient: SignalingClient
    private lateinit var webrtcManager: WebrtcManager

    // EglBase es el motor gráfico interno de WebRTC
    private val eglBase = EglBase.create()

    // Variable de estado de Compose. Cuando reciba una pista de vídeo (VideoTrack),
    // la interfaz se actualizará automáticamente.
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
    }

    private fun initApp() {
        // Mantener la pantalla siempre encendida mientras la app esté abierta
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1. Inicializamos el Signaling Client con los datos correctos
        signalingClient = SignalingClient(
            url = "https://6a8tenisdemesa.com",
            roomName = "c-50-room"
        )

        // 2. Inicializamos el WebRTC Manager pasándole contexto y el signaling
        webrtcManager = WebrtcManager(
            context = this,
            signalingClient = signalingClient
        )

        // Asignamos el callback para la UI (como variable, no en constructor)
        webrtcManager.onVideoTrackReceived = { track ->
            // ¡Magia! Cuando el Pi envíe el vídeo, se asigna aquí y Compose redibuja
            remoteVideoTrack = track
        }

        // 3. Conectamos al servidor de Signaling y cuando estemos dentro de la sala, llamamos
        signalingClient.onConnected = {
            webrtcManager.startCall()
        }
        signalingClient.connect()

        // 4. Mostramos la interfaz de usuario
        setContent {
            // Un Composable principal que envuelve el video y los controles
            Box(modifier = Modifier.fillMaxSize()) {
                C50VideoRenderer(
                    videoTrack = remoteVideoTrack,
                    eglBaseContext = eglBase.eglBaseContext
                )
                
                // Controles superpuestos (Joystick a la izquierda)
                AnalogJoystick(
                    modifier = Modifier.align(Alignment.BottomStart).padding(32.dp),
                    onCommand = { cmd -> webrtcManager.sendCommand(cmd) }
                )
                
                // Botón Push-To-Talk (PTT) a la derecha
                PushToTalkButton(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
                    onStateChange = { isTalking ->
                        webrtcManager.setMicrophoneEnabled(isTalking)
                    }
                )
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Limpiamos los recursos para que no se quede la red o vista pillada
        signalingClient.disconnect()
        // webrtcManager.disconnect() <-- ¡Bórrala! La implementaremos luego
        eglBase.release()
    }
}

@Composable
fun C50VideoRenderer(videoTrack: VideoTrack?, eglBaseContext: EglBase.Context) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // Inicializamos el renderizador con el motor gráfico
                init(eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false) // En el robot no queremos efecto espejo
            }
        },
        modifier = Modifier.fillMaxSize(), // Que ocupe toda la pantalla
        update = { view ->
            // Si el videoTrack ya no es nulo (nos llegó desde la Pi), lo inyectamos a la vista
            videoTrack?.addSink(view)
        }
    )
}

@Composable
fun AnalogJoystick(modifier: Modifier = Modifier, onCommand: (String) -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val maxRadius = 150f // Límite del joystick

    Box(
        modifier = modifier
            .size(150.dp)
            .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Vuelve al centro al soltar
                        offsetX = 0f
                        offsetY = 0f
                        onCommand("JOY:0,0")
                    }
                ) { change, dragAmount ->
                    change.consume()
                    var newX = offsetX + dragAmount.x
                    var newY = offsetY + dragAmount.y
                    
                    val distance = sqrt(newX.pow(2) + newY.pow(2))
                    
                    // Restringir el círculo interno para que no se salga de su base
                    if (distance > maxRadius) {
                        newX = (newX / distance) * maxRadius
                        newY = (newY / distance) * maxRadius
                    }
                    
                    offsetX = newX
                    offsetY = newY

                    // Convertir el desplazamiento a valores analógicos de -100 a +100
                    // Invertimos el eje Y para que "hacia arriba" sea positivo
                    val xPercent = (offsetX / maxRadius * 100).toInt()
                    val yPercent = (-offsetY / maxRadius * 100).toInt()
                    
                    onCommand("JOY:$xPercent,$yPercent")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // El "palo" del joystick (círculo interno)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(60.dp)
                .background(Color.DarkGray, CircleShape)
        )
    }
}

@Composable
fun PushToTalkButton(modifier: Modifier = Modifier, onStateChange: (Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(100.dp)
            .background(
                if (isPressed) Color.Green.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.4f),
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onStateChange(true)
                        tryAwaitRelease()
                        isPressed = false
                        onStateChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPressed) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = if (isPressed) "Hablando" else "Pulsa para hablar",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
    }
}
