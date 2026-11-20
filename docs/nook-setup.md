# Setup de los Nooks con larapaper (operación y cambios futuros)

Estado desde **2026-11-20**. Sirve para cambiar URL, credenciales, wifi o reemplazar un Nook sin repetir el diagnóstico del incidente de noviembre.

## Arquitectura

```
Nook casa   (red "allende")          ─┐
                                       ├─ http://trmnl.peu.net/api/display
Nook taller (red "que miras piscui") ─┘
                    │
                    ▼
   Cloudflare DNS  trmnl.peu.net → A 129.213.40.115   (nube GRIS, sin proxy)
                    │
                    ▼
   VPS Oracle (dev-v2, opc@129.213.40.115)
     Caddy en ~/umami/Caddyfile:  http://trmnl.peu.net { reverse_proxy 10.241.117.135:4567 }
     (el mismo Caddy sirve analytics.peu.net → umami; no tocar ese bloque)
                    │
                    ▼  ZeroTier
   Debiandev 192.168.68.117  →  larapaper-app-1 (Docker, 4567) → /api/display
```

**Por qué http plano:** el Android 2.3 del Nook no confía en las CA roots modernas. El bloque del Caddyfile debe llevar el esquema `http://` explícito; si se le pone HTTPS, el Nook no valida el certificado.

## Identidades

| Nook | `api_id` | `api_token` | `name` en larapaper |
|---|---|---|---|
| Casa | `nook2` | `1becc27c-0fbf-47c2-bea3-176e6aacb46c` | Nook2 (device 2) |
| Taller | `nook1` | `e578912a-62fc-46c4-8413-6e333eaeb42a` | Nook1 (device 1) |

Cada Nook debe tener credenciales **distintas**: si comparten las de uno, larapaper hace dos renders por ciclo para el mismo dispositivo y las pantallas se pisan.

## Cambiar la URL o las credenciales de un Nook (ADB por USB)

Conectar el Nook por USB a Debiandev (la regla udev `/etc/udev/rules.d/51-android-nook.rules` ya está instalada; si `adb devices` dice "no permissions", reinstalarla y `sudo udevadm control --reload-rules && sudo udevadm trigger`).

```bash
adb devices                                  # anotar el serial: 3014650010823003 (casa) / 3013450076503007 (taller)
adb -s <serial> pull /data/data/com.bpmct.trmnl_nook_simple_touch/shared_prefs/trmnl_prefs.xml /tmp/prefs.xml
# editar api_base_url / api_id / api_token en /tmp/prefs.xml
adb -s <serial> push /tmp/prefs.xml /data/data/com.bpmct.trmnl_nook_simple_touch/shared_prefs/trmnl_prefs.xml
PID=$(adb -s <serial> shell "ps | grep trmnl_nook" | awk '{print $2}' | head -1)
adb -s <serial> shell "kill $PID"
adb -s <serial> shell "am start -n com.bpmct.trmnl_nook_simple_touch/.DisplayActivity"
```

- **`am force-stop` no existe en Android 2.3** — hay que matar el proceso con `kill`.
- El launcher es **`.DisplayActivity`** (no `MainActivity`, que era el nombre viejo).
- Si la app ya estaba corriendo, `am start` solo trae la ventana al frente y **no relee las prefs** — matarla primero.
- Alternativa sin editar a mano: `tools/nook-adb.sh --ip <ip> set-preset <nombre>` con un archivo `prefs/<nombre>.args` (hace pull-modify-push de solo las claves del preset).

### Verificar que el fetch entró

```bash
docker logs -f larapaper-app-1 2>&1 | grep TRMNL-Nook
```

Un ciclo sano es `GET /api/display 200` cada ~40 min con el `ID` correcto. (Los 200 de imágenes estáticas no se loguean; solo los 404 pasan por Laravel.)

## Cambiar o agregar red wifi

El Nook no tiene teclado cómodo; todo por ADB. Método probado:

1. Crear el archivo local con el formato de `/data/misc/wifi/wpa_supplicant.conf`:

   ```
   ctrl_interface=tiwlan0
   update_config=1
   eapol_version=1
   ap_scan=1
   fast_reauth=1
   network={
           ssid="NOMBRE"
           psk="passphrase o PMK hex"
           key_mgmt=WPA-PSK
   }
   ```

2. `adb push` a `/data/misc/wifi/wpa_supplicant.conf` y **arreglar el ownership**, esto es lo que más falla:

   ```bash
   adb shell "busybox chown 1000:1010 /data/misc/wifi/wpa_supplicant.conf; busybox chmod 660 /data/misc/wifi/wpa_supplicant.conf"
   ```

   Tras cualquier `adb push` el archivo queda `root:root` y el driver responde `Cannot access "…wpa_supplicant.conf": Permission denied` → `Failed to start supplicant daemon`. El `chown` del toolbox propio no acepta ni nombres ni números; **usar `busybox chown`** (busybox está en `/system/xbin`).

3. `adb shell "svc wifi disable"` → esperar 3 s → `adb shell "svc wifi enable"` → esperar ~10 s → `adb shell netcfg` (tiene que mostrar `tiwlan0 UP <ip>`).

Notas:
- La PSK de la red `allende` es un PMK hex de 64 caracteres **sin comillas**; el resto de redes usan passphrase entre comillas. Copiar tal cual del origen que funcione.
- No dejar líneas `EOF` suelto ni bloques repetidos: corrompen el archivo (pasó al configurarlo la primera vez).
- La app muestra el overlay "sin wifi" si chequeó con la radio apagada y no se recupera sola: asociar el wifi y **reiniciar la app**.

## Si Caddy o el DNS se rompen

```bash
# en la VPS
sudo docker exec umami-caddy-1 caddy validate --config /etc/caddy/Caddyfile   # valida sin aplicar
sudo docker exec umami-caddy-1 caddy reload --config /etc/caddy/Caddyfile     # aplica sin downtime
curl -s -o /dev/null -w '%{http_code}\n' https://analytics.peu.net/           # umami debe dar 200
```

Rollback: `cp ~/umami/Caddyfile.bak ~/umami/Caddyfile` + reload. El dominio es Cloudflare en modo gris: si aparece proxy naranja, los Nooks no llegan.

Verificación de punta a punta sin tocar los Nooks:

```bash
curl -H 'ID: nook2' -H 'access-token: 1becc27c-0fbf-47c2-bea3-176e6aacb46c' http://trmnl.peu.net/api/display
```

## Reemplazar un Nook (físico nuevo)

1. Registrar el dispositivo en larapaper (devices) y obtener su `api_id`/`api_token`. Nunca reutilizar los de otro Nook activo.
2. Configurar ese Nook por ADB con su URL (`http://trmnl.peu.net/api`) y credenciales.
3. Verificar el primer ciclo en `docker logs`.

## Diagnóstico rápido de síntomas

| Síntoma | Probable causa | Dónde mirar |
|---|---|---|
| Pantalla "tap to retry" / timeout | VLAN de salida, DNS o Caddy caído | `dig trmnl.peu.net`, `caddy reload`, `curl` de prueba |
| Fetches 200 pero no cambia imagen | imagen borrada antes de descargarse | `cleanupFolder()` (gracia 15 min, ver repo larapaper) |
| Dos pantallas con el mismo render | credenciales duplicadas | `api_id` en prefs de cada Nook |
| "sin wifi" en pantalla | supplicant no arranca / radio apagada al chequeo | `logcat`, ownership del wpa_supplicant, reiniciar app |
| Ciclos con `ID` equivocado | prefs apuntan a otro device | `trmnl_prefs.xml` |

## Historial del incidente (nov 2026)

El "retry permanente" tuvo dos causas superpuestas:

1. **localtunnel** (`trmnl-nook-2026.loca.lt`) como frente público: el cliente `lt` crasheaba y la reconexión con subdomain fijo tardaba horas; una vez el subdomain cayó en uno aleatorio. **Retirado**; reemplazado por la VPS + ZeroTier.
2. **`ImageGenerationService::cleanupFolder()`** en larapaper borraba imágenes recién generadas sin periodo de gracia (carrera con polls duplicados), causando 404 en la descarga de imagen. Parcheado con 15 min de gracia.

Ambos Nooks estaban configurados con credenciales de Nook2 (heredado de un setup anterior), lo que duplicaba renders; ahora tienen identidad propia.
