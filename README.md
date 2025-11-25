# soulbound-mmorpg (MVP)

Monorepo cliente-servidor para un MMORPG 3D mínimo:
- **Servidor:** Spring Boot + WebSocket. Spawnea goblins, gestiona jugadores/XP y resuelve ataques.
- **Cliente:** jMonkeyEngine 3.6. Mapa plano 3D, cubo azul como jugador y cubos rojos como monstruos.

## Ejecución rápida (PowerShell en Windows)
1) Compilar ambos módulos (requiere Gradle):
```powershell
gradle build
```
2) Levantar el servidor:
```powershell
gradle :server:bootRun
```
3) En otra terminal abrir el cliente (abre ventana 3D):
```powershell
gradle :client:run --args="-- TuNombre"
```

### Script de inicio / fin (opcional)
```powershell
# inicia server + cliente en segundo plano (usa PLAYER_NAME opcional)
scripts/start.ps1 -PlayerName "TuNombre"

# para detener ambos
scripts/stop.ps1
```
Los PIDs se guardan en `scripts/pids.json` y los logs en `scripts/*.log`.

## Controles y flujo
- Tecla `SPACE`: ataca al monstruo más cercano. Al matar uno ganas XP (25) y puedes subir de nivel.
- Cada 5s el servidor genera un goblin en una posición aleatoria.
- El HUD muestra jugador, nivel, XP y los monstruos vivos.

## Notas y limitaciones
- No hay persistencia ni autenticación; todo es en memoria y sin seguridad.
- jMonkeyEngine necesita soporte OpenGL en desktop.
- Balística/colisiones son simplificadas: ataque autoselecciona el objetivo más cercano.
