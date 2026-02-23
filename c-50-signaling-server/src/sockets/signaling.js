/**
 * Maneja la lógica de señalización WebRTC a través de Socket.io
 * @param {import('socket.io').Server} io - Instancia del servidor Socket.io
 */
export const setupSignaling = (io) => {
  io.on('connection', (socket) => {
    console.log(`[+] Nuevo cliente conectado: ${socket.id}`);

    // Unirse a una sala específica (ej. 'c-50-room')
    socket.on('join-room', (roomId) => {
      socket.join(roomId);
      console.log(`[>] Cliente ${socket.id} se unió a la sala: ${roomId}`);
      
      // Notificar a los demás en la sala que alguien nuevo entró
      socket.to(roomId).emit('user-joined', socket.id);
    });

    // Reenviar la oferta WebRTC (SDP Offer)
    socket.on('offer', (data) => {
      console.log(`[>] Oferta recibida de ${socket.id} para la sala ${data.roomId}`);
      socket.to(data.roomId).emit('offer', {
        senderId: socket.id,
        offer: data.offer
      });
    });

    // Reenviar la respuesta WebRTC (SDP Answer)
    socket.on('answer', (data) => {
      console.log(`[>] Respuesta recibida de ${socket.id} para la sala ${data.roomId}`);
      socket.to(data.roomId).emit('answer', {
        senderId: socket.id,
        answer: data.answer
      });
    });

    // Reenviar los candidatos ICE (Interactive Connectivity Establishment)
    socket.on('ice-candidate', (data) => {
      console.log(`[>] ICE Candidate de ${socket.id} para la sala ${data.roomId}`);
      socket.to(data.roomId).emit('ice-candidate', {
        senderId: socket.id,
        candidate: data.candidate
      });
    });

    // Manejar desconexión
    socket.on('disconnect', () => {
      console.log(`[-] Cliente desconectado: ${socket.id}`);
      // Opcional: Notificar a las salas a las que pertenecía
    });
  });
};
