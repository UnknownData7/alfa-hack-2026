# Локальный запуск и проверка стенда: docker compose up, ожидание health, смоук-тест сценария S1 через API.
# Логи пишутся в папку logs рядом с проектом. Запуск: start-demo.bat или
#   powershell -ExecutionPolicy Bypass -File scripts\local-check.ps1
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$summary = Join-Path $logs 'summary.log'
"Запуск: $(Get-Date -Format s)" | Out-File $summary -Encoding utf8

function Log($text) {
    Write-Host $text
    $text | Out-File $summary -Append -Encoding utf8
}

if (-not (Test-Path (Join-Path $root '.env'))) {
    Copy-Item (Join-Path $root '.env.example') (Join-Path $root '.env')
    Log 'Создан .env из .env.example'
}

docker version *> (Join-Path $logs 'docker-version.log')
if ($LASTEXITCODE -ne 0) {
    Log 'ОШИБКА: Docker не отвечает. Запустите Docker Desktop, дождитесь статуса Engine running и повторите.'
    exit 1
}
Log 'Docker отвечает'

Log 'Сборка и запуск контейнеров (первый раз 5-10 минут)...'
docker compose up --build -d *> (Join-Path $logs 'compose-up.log')
if ($LASTEXITCODE -ne 0) {
    Log 'ОШИБКА: docker compose up завершился с ошибкой, см. logs\compose-up.log'
    docker compose logs --no-color --tail 300 *> (Join-Path $logs 'compose-logs.log')
    exit 1
}

$port = 3000
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 5
        if ($h.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 4
}
docker compose ps *> (Join-Path $logs 'compose-ps.log')
docker compose logs --no-color --tail 400 backend *> (Join-Path $logs 'backend.log')
docker compose logs --no-color --tail 100 frontend db *> (Join-Path $logs 'frontend-db.log')
docker stats --no-stream *> (Join-Path $logs 'stats.log')
if (-not $ready) {
    Log 'ОШИБКА: backend не стал здоровым за 6 минут, см. logs\backend.log'
    exit 1
}
Log "Стенд поднят: http://localhost:$port"

# Смоук-тест S1 через API
$base = "http://localhost:$port"
function Api($method, $path, $body, $token) {
    # Origin как у браузера: так проверка ловит и ошибки CORS за nginx
    $headers = @{ Origin = $base }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) {
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5))
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    return Invoke-RestMethod @params
}
try {
    Api 'POST' '/api/dev/reset-demo' $null $null | Out-Null
    $token = (Api 'POST' '/api/auth/login' @{ login = 'romashka'; password = 'Demo#2026' } $null).token
    $uwToken = (Api 'POST' '/api/auth/login' @{ login = 'uw1'; password = 'Demo#2026' } $null).token
    $queue = Api 'GET' '/api/bank/tasks' $null $uwToken
    Log "Банк: вход uw1 выполнен, задач в очереди: $($queue.Count)"
    $draft = Api 'POST' '/api/applications' @{ productCode = 'WORKING_EXPRESS'; purpose = 'WORKING_CAPITAL'; amount = 1200000; termMonths = 9; consentDataProcessing = $true; consentCreditBureau = $true } $token
    $id = $draft.id
    $pdf = Join-Path $root 'postman\files\statement.pdf'
    curl.exe -s -o NUL -w "upload=%{http_code}`n" -H "Origin: $base" -H "Authorization: Bearer $token" -F "docType=BANK_STATEMENT" -F "file=@$pdf;type=application/pdf" "$base/api/applications/$id/documents" | Out-File $summary -Append -Encoding utf8
    Api 'POST' "/api/applications/$id/submit" $null $token | Out-Null
    $app = $null
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        $app = Api 'GET' "/api/applications/$id" $null $token
        if ($app.status -in @('OFFER_MADE', 'DECLINED', 'MANUAL_REVIEW')) { break }
    }
    Log "S1: статус $($app.status), сумма $($app.offer.amount), ставка $($app.offer.ratePct), платёж $($app.offer.monthlyPayment)"
    Api 'POST' "/api/applications/$id/offer/accept" $null $token | Out-Null
    $issued = Api 'POST' "/api/applications/$id/sign" @{ termsAccepted = $true } $token
    Log "S1: итоговый статус $($issued.status), выдача смоделирована: $($issued.disbursement.simulated)"
    if ($issued.status -eq 'ISSUED') { Log 'СМОУК-ТЕСТ S1 ПРОЙДЕН' } else { Log 'СМОУК-ТЕСТ S1 НЕ ПРОЙДЕН' }
    Api 'POST' '/api/dev/reset-demo' $null $null | Out-Null
} catch {
    Log "ОШИБКА смоук-теста: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Log $_.ErrorDetails.Message }
}
docker compose logs --no-color --tail 400 backend *> (Join-Path $logs 'backend.log')
Log 'Готово. Откройте http://localhost:3000'
