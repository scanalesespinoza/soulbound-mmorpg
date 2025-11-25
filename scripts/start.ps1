param(
    [string]$PlayerName = "Player"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Split-Path $scriptRoot
$pidFile = Join-Path $scriptRoot "pids.json"

if (Test-Path $pidFile) {
    Write-Warning "Ya existe $pidFile. Ejecutando stop.ps1 para limpiar."
    & "$scriptRoot/stop.ps1"
}

Set-Location $repoRoot

# Si hay algo ocupando el 8080, lo detenemos para evitar fallos de arranque
try {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        $pid8080 = $listener.OwningProcess
        if ($pid8080 -and (Get-Process -Id $pid8080 -ErrorAction SilentlyContinue)) {
            Write-Host "Liberando puerto 8080 (PID $pid8080)..."
            Stop-Process -Id $pid8080 -Force -ErrorAction SilentlyContinue
        }
    }
} catch {}

function Start-GradleComponent {
    param(
        [string]$name,
        [string[]]$arguments
    )
    $outLog = Join-Path $scriptRoot "$name.out.log"
    $errLog = Join-Path $scriptRoot "$name.err.log"
    $proc = Start-Process -FilePath "gradle" `
        -ArgumentList $arguments `
        -WorkingDirectory $repoRoot `
        -PassThru `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -WindowStyle Hidden
    return $proc.Id
}

$serverPid = Start-GradleComponent -name "server" -arguments @(":server:bootRun")
# Espera a que el servidor escuche en 8080 antes de lanzar el cliente
$maxWait = 15
$waited = 0
while ($waited -lt $maxWait) {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($listener) { break }
    Start-Sleep -Seconds 1
    $waited++
}
$clientArgs = @(":client:run", "--args=`"-- $PlayerName`"")
$clientPid = Start-GradleComponent -name "client" -arguments $clientArgs

$pids = [ordered]@{
    server   = $serverPid
    client   = $clientPid
    started  = (Get-Date).ToString("s")
}

$pids | ConvertTo-Json | Set-Content -Path $pidFile -Encoding UTF8

Write-Host "Servidor iniciado (PID $serverPid) y cliente iniciado (PID $clientPid)."
Write-Host "Logs: $(Join-Path $scriptRoot 'server.out.log'), $(Join-Path $scriptRoot 'client.out.log')"
Write-Host "Para detener, ejecuta scripts/stop.ps1"
