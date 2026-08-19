param(
    [int] $TimeoutSeconds = 120,
    [switch] $SkipBuild
)

$ErrorActionPreference = "Stop"

if (-not $env:GRAFANA_ADMIN_USER) {
    $env:GRAFANA_ADMIN_USER = "admin"
}

if (-not $env:GRAFANA_ADMIN_PASSWORD) {
    $env:GRAFANA_ADMIN_PASSWORD = "local-grafana-$([guid]::NewGuid())"
}

function Wait-Health {
    param(
        [string] $Name,
        [string] $Url
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 3
            if ($response.status -eq "UP") {
                Write-Host "$Name health is UP"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name did not become healthy within $TimeoutSeconds seconds"
}

function Wait-Payment-FinalStatus {
    param(
        [string] $PaymentId
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $payment = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/payments/$PaymentId" -Method Get -TimeoutSec 5

        if ($payment.status -in @("APPROVED", "REJECTED", "FAILED")) {
            return $payment
        }

        Start-Sleep -Seconds 2
    }

    throw "Payment $PaymentId did not reach a final status within $TimeoutSeconds seconds"
}

if ($SkipBuild) {
    docker compose up -d
} else {
    docker compose up -d --build
}

Wait-Health -Name "provider-simulator" -Url "http://localhost:8083/actuator/health"
Wait-Health -Name "payment-service" -Url "http://localhost:8081/actuator/health"
Wait-Health -Name "payment-processor" -Url "http://localhost:8082/actuator/health"

$body = @{
    merchantId = "merchant-e2e"
    amount = 100.00
    currency = "BRL"
    paymentMethod = "PIX"
} | ConvertTo-Json

$createdPayment = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/payments" `
    -Method Post `
    -Headers @{
        "Idempotency-Key" = "e2e-$([guid]::NewGuid())"
        "X-Correlation-Id" = "e2e-$([guid]::NewGuid())"
    } `
    -ContentType "application/json" `
    -Body $body `
    -TimeoutSec 10

$finalPayment = Wait-Payment-FinalStatus -PaymentId $createdPayment.id

Write-Host "E2E payment $($finalPayment.id) finished with status $($finalPayment.status)"

if ($finalPayment.status -ne "APPROVED") {
    throw "Expected APPROVED, got $($finalPayment.status)"
}
