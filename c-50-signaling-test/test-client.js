import { io } from 'socket.io-client';

// ============================================================================
// CONFIGURACIÓN DEL CLIENTE DE PRUEBA
// ============================================================================
// Cambia esta URL por la de tu servidor en Oracle Cloud (ej. 'https://tu-dominio.com:3000')
// Si usas Nginx como proxy inverso, probablemente no necesites el puerto 3000 aquí.
const SERVER_URL = 'https://6a8tenisdemesa.com'; 
const ROOM_ID = 'c-50-room';

// Obtener el nombre del cliente desde los argumentos de la línea de comandos
const clientName = process.argv[2] || 'Cliente-Desconocido';

console.log(`\n[${clientName}] Iniciando cliente de prueba...`);
console.log(`[${clientName}] Conectando a: ${SERVER_URL}\n`);

// ============================================================================
// INICIALIZACIÓN DE SOCKET.IO
// ============================================================================
const socket = io(SERVER_URL, {
  reconnectionDelayMax: 10000,
});

// ============================================================================
// MANEJO DE EVENTOS DE CONEXIÓN
// ============================================================================
socket.on('connect', () => {
  console.log(`✅ [${clientName}] Conectado al servidor con ID: ${socket.id}`);
  
  // Unirse a la sala de pruebas
  console.log(`[${clientName}] Uniéndose a la sala: ${ROOM_ID}...`);
  socket.emit('join-room', ROOM_ID);

  // Simular el envío de una oferta WebRTC (SDP Offer) después de 2 segundos
  // Solo el 'client1' enviará la oferta inicial para simular el flujo real
  if (clientName === 'client1') {
    setTimeout(() => {
      console.log(`\n📤 [${clientName}] Enviando oferta WebRTC simulada...`);
      
      // IMPORTANTE: Para que aiortc no falle con "None is not in list",
      // la oferta simulada debe indicar explícitamente que quiere enviar/recibir video.
      // Este es un SDP mínimo válido que incluye una línea de media (m=video)
      // y las credenciales ICE obligatorias (ice-ufrag y ice-pwd).
      const fakeSdp = `v=0\r
o=- 123456 2 IN IP4 127.0.0.1\r
s=-\r
t=0 0\r
a=ice-ufrag:fakeufrag12345678\r
a=ice-pwd:fakepassword12345678901234567890\r
a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00\r
a=setup:actpass\r
m=video 9 UDP/TLS/RTP/SAVPF 96\r
c=IN IP4 0.0.0.0\r
a=sendrecv\r
a=rtcp-mux\r
a=rtpmap:96 VP8/90000\r
`;

      socket.emit('offer', {
        roomId: ROOM_ID,
        offer: { type: 'offer', sdp: fakeSdp }
      });
    }, 2000);
  }
});

socket.on('disconnect', () => {
  console.log(`❌ [${clientName}] Desconectado del servidor.`);
});

socket.on('connect_error', (error) => {
  console.error(`⚠️ [${clientName}] Error de conexión:`, error.message);
});

// ============================================================================
// MANEJO DE EVENTOS DE SEÑALIZACIÓN (WEBRTC)
// ============================================================================

// Alguien nuevo entró a la sala
socket.on('user-joined', (userId) => {
  console.log(`👋 [${clientName}] Un nuevo usuario se unió a la sala: ${userId}`);
});

// Recibir una oferta (SDP Offer)
socket.on('offer', (data) => {
  console.log(`\n📥 [${clientName}] Oferta recibida de: ${data.senderId}`);
  console.log(`   Contenido:`, data.offer);

  // Simular la respuesta a la oferta (SDP Answer)
  console.log(`📤 [${clientName}] Enviando respuesta WebRTC simulada...`);
  socket.emit('answer', {
    roomId: ROOM_ID,
    answer: { type: 'answer', sdp: 'v=0\r\no=- 654321 2 IN IP4 127.0.0.1\r\n...' }
  });
});

// Recibir una respuesta (SDP Answer)
socket.on('answer', (data) => {
  console.log(`\n📥 [${clientName}] Respuesta recibida de: ${data.senderId}`);
  console.log(`   Contenido:`, data.answer);

  // Simular el envío de candidatos ICE una vez establecida la conexión básica
  console.log(`📤 [${clientName}] Enviando candidato ICE simulado...`);
  socket.emit('ice-candidate', {
    roomId: ROOM_ID,
    candidate: { candidate: 'candidate:1 1 UDP 2130706431 192.168.1.50 50000 typ host', sdpMid: '0', sdpMLineIndex: 0 }
  });
});

// Recibir candidatos ICE
socket.on('ice-candidate', (data) => {
  console.log(`\n📥 [${clientName}] Candidato ICE recibido de: ${data.senderId}`);
  console.log(`   Contenido:`, data.candidate);
  console.log(`\n🎉 [${clientName}] ¡Flujo de señalización completado con éxito!`);
});
