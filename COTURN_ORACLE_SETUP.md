# Guía de Configuración de Servidor TURN/STUN (Coturn) en Oracle Cloud

Esta guía documenta los pasos exactos que seguimos para configurar con éxito el servidor **Coturn** en tu instancia de Oracle Cloud. Esta configuración fue crucial para lograr la comunicación WebRTC (audio y vídeo) atravesando redes y firewalls estrictos gracias a que habilitamos el tráfico sobre TCP.

---

## 1. Reglas de Red en Oracle Cloud (Security Lists)

Antes de tocar la máquina virtual, debes asegurarte de que Oracle permite el tráfico entrante a tu servidor.
Ve al panel web de Oracle Cloud, navega a tu Instancia > Virtual Cloud Network (VCN) > Subnets > Default Security List, y añade las siguientes **Ingress Rules** (Reglas de entrada):

- **TURN Peticiones**: Puerto `3478` (Ambos protocolos: UDP y TCP).
- **TURN Seguro (Opcional)**: Puerto `5349` (TCP).
- **Rango de Relés (Relay Ports)**: Rango de puertos `49152-65535` (UDP).

> *Importante:* Asegúrate de que el "Source CIDR" esté configurado como `0.0.0.0/0` (permitir desde cualquier lugar).

---

## 2. El paso clave: Sortear nftables y Configurar UFW

En las versiones modernas de Ubuntu (como la 24.04) en Oracle Cloud, `iptables` sirve solo de fachada y es superado por un motor más potente llamado `nftables` que trae bloqueos severos de fábrica. Esta fue la pesadilla principal y así la resolvimos:

**A. Limpiar el muro nativo de Oracle (`nftables`)**
Esto elimina las reglas invisibles de fábrica que tiraban a la basura los paquetes WebRTC milisegundos antes de procesarlos:
```bash
sudo nft flush ruleset
```

**B. Evitar el "Filtro de Camino Inverso" (rp_filter)**
Para evitar que el núcleo de Linux destruya paquetes de vuelta por un exceso de asimetría de red:
```bash
sudo sysctl -w net.ipv4.conf.all.rp_filter=0
sudo sysctl -w net.ipv4.conf.enp0s6.rp_filter=0
```
*(Nota: la interfaz de red `enp0s6` podría llamarse distinto en una nueva máquina, si falla verifícalo lanzando `ip a`).*

**C. Instalar y abrir los puertos en la capa correcta (UFW)**
En lugar de `iptables`, usamos el gestor nativo UFW. Es vital permitir el puerto 22 antes de encenderlo para no quedarte bloqueado fuera del servidor:
```bash
sudo apt update && sudo apt install ufw -y
sudo ufw allow 22/tcp        # SSH, no quitar nunca
sudo ufw allow 80/tcp        # Web
sudo ufw allow 443/tcp       # SSL
sudo ufw allow 3478          # Peticiones TURN (Ambos protocolos)
sudo ufw allow 49152:65535/udp # Rango de relés WebRTC
sudo ufw --force enable
```

---

## 3. Instalación de Coturn

Instala el paquete oficial desde los repositorios de Ubuntu:

```bash
sudo apt update
sudo apt install coturn
```

Para asegurarte de que el servicio arranque de forma automática, edita el archivo interno de demonio:

```bash
sudo nano /etc/default/coturn
```
Quita el comentario (`#`) de la línea y déjala así:
`TURNSERVER_ENABLED=1`

---

## 4. Archivo de Configuración (`/etc/turnserver.conf`)

Aquí está la magia. Renombra el archivo original por si quieres un backup, y crea uno nuevo.

```bash
sudo mv /etc/turnserver.conf /etc/turnserver.conf.backup
sudo nano /etc/turnserver.conf
```

**Pega esta configuración adaptada (asegúrate de cambiar la IP externa por la IP pública que tenga tu servidor de Oracle en cada momento, y la IP interna si difiere):**

```ini
# --- Puertos de escucha ---
listening-port=3478
tls-listening-port=5349

# IP sobre la que escucha dentro del servidor (pon 0.0.0.0 para todas las interfaces)
listening-ip=0.0.0.0

# -- Direcciones IP --
# Rellena tu IP PÚBLICA de Oracle aquí
external-ip=TUP.IP.PUB.LICA

# (Opcional) Si la instancia de Oracle tiene una IP privada estática asociada a la interfaz
# relay-ip=TU.IP.PRI.VADA

# Rango de puertos dinámicos para retransmitir el flujo de vídeo y audio WebRTC
min-port=49152
max-port=65535

# --- Autenticación y Usuarios ---
# Usa los mecanismos a largo plazo y define el dominio o identificador
lt-cred-mech
realm=6a8tenisdemesa.com

# Usuario y contraseña en texto plano para los clientes.
# Formato: user=USUARIO:CONTRASEÑA
user=c50_robot:mypassword123C50

# --- Optimizaciones y Log ---
log-file=/var/log/turnserver.log
syslog
no-stdout-log
```

---

## 5. Aplicar y Reiniciar

Después de guardar el archivo, reinicia el servicio y comprueba que está corriendo:

```bash
sudo systemctl restart coturn
sudo systemctl status coturn
```

*(El estado debe indicar "active (running)").*

---

## 6. La Clave de nuestro Éxito: El Bypass para Firewalls Restrictivos

Al probar nuestro robot `C-50` te encontraste con redes corporativas/públicas que bloqueaban totalmente el tráfico UDP desconocido.

El salvavidas fue añadir el sufijo `?transport=tcp` desde nuestro código en las apps en Android (Pilot y Brain). Como arriba le dijimos a Coturn que también escuchara en `3478 TCP` (`listening-port=3478`), cuando el móvil no lograba enviar por UDP, WebRTC automáticamente hacía fallback a este servidor mediante TCP garantizado.

Tu array de WebRTC en Kotlin debe verse **siempre** así:

```kotlin
private val iceServers = listOf(
    // STUN Clásico para descubrir IPs si no hay firewall restrictivo
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    
    // TURN sobre UDP (Ruta rápida)
    PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478")
        .setUsername("c50_robot")
        .setPassword("mypassword123C50")
        .createIceServer(),
        
    // TURN sobre TCP (El Bypass para Redes Restrictivas)
    PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478?transport=tcp")
        .setUsername("c50_robot")
        .setPassword("mypassword123C50")
        .createIceServer()
)
```

## 7. Pruebas

Si alguna vez rehaces el servidor, siempre puedes probar tus credenciales entrando en la página oficial del test de WebRTC de Google:
`https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/`

Pones tu URI TCP (`turn:6a8tenisdemesa.com:3478?transport=tcp`), rellenas tu usuario y contraseña, le das a "Gather candidates" y debe salirte al menos que ha encontrado candidatos del tipo **"relay"**.