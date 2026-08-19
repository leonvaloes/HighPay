param(
    [int] $PaymentServiceInstances = 2,
    [int] $VirtualUsers = 10,
    [string] $Duration = "1m"
)

$ErrorActionPreference = "Stop"

if (-not $env:HIGHPAY_INTERNAL_AUTH_TOKEN) {
    throw "Set HIGHPAY_INTERNAL_AUTH_TOKEN before running the load test"
}

if (-not $env:GRAFANA_ADMIN_USER) {
    $env:GRAFANA_ADMIN_USER = "admin"
}

if (-not $env:GRAFANA_ADMIN_PASSWORD) {
    $env:GRAFANA_ADMIN_PASSWORD = "local-grafana-$([guid]::NewGuid())"
}

docker compose up -d --build `
    --scale payment-service=$PaymentServiceInstances

$env:VUS = "$VirtualUsers"
$env:DURATION = $Duration
k6 run .\scripts\load-test-k6.js
