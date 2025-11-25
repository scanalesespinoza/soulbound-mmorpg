$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$pidFile = Join-Path $scriptRoot "pids.json"

if (-not (Test-Path $pidFile)) {
    Write-Host "No hay $pidFile, nada que detener."
    exit 0
}

try {
    $pids = Get-Content $pidFile | ConvertFrom-Json
} catch {
    Write-Warning "No se pudo leer ${pidFile}: $_"
    exit 1
}

foreach ($key in @("client", "server")) {
    $procId = $pids.$key
    if ($procId -and (Get-Process -Id $procId -ErrorAction SilentlyContinue)) {
        Write-Host "Deteniendo $key (PID $procId)..."
        try {
            Stop-Process -Id $procId -ErrorAction SilentlyContinue -Force -IncludeChildren
        } catch {
            Stop-Process -Id $procId -ErrorAction SilentlyContinue -Force
        }
    }
}

try {
    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        $pid8080 = $listener.OwningProcess
        if ($pid8080 -and (Get-Process -Id $pid8080 -ErrorAction SilentlyContinue)) {
            Write-Host "Deteniendo proceso en puerto 8080 (PID $pid8080)..."
            Stop-Process -Id $pid8080 -Force -ErrorAction SilentlyContinue
        }
    }
} catch {
    Write-Warning "No se pudo inspeccionar el puerto 8080: $_"
}

Remove-Item $pidFile -ErrorAction SilentlyContinue
Write-Host "Procesos detenidos y $pidFile eliminado."
