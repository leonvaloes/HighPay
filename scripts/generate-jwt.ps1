param(
    [string] $Subject = "merchant-001",
    [string] $Issuer = "highpay-local",
    [string] $Scope = "payments:read payments:write",
    [string] $Secret = $env:HIGHPAY_PUBLIC_AUTH_JWT_SECRET,
    [int] $ExpiresInSeconds = 3600
)

$ErrorActionPreference = "Stop"

if (-not $Secret -or $Secret.Length -lt 32) {
    throw "Secret must have at least 32 characters. Set HIGHPAY_PUBLIC_AUTH_JWT_SECRET or pass -Secret."
}

function ConvertTo-Base64Url {
    param([byte[]] $Bytes)

    return [Convert]::ToBase64String($Bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

$header = @{
    alg = "HS256"
    typ = "JWT"
} | ConvertTo-Json -Compress

$payload = @{
    sub = $Subject
    iss = $Issuer
    scope = $Scope
    exp = [int][double]::Parse((Get-Date -Date (Get-Date).ToUniversalTime().AddSeconds($ExpiresInSeconds) -UFormat %s))
} | ConvertTo-Json -Compress

$encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
$encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
$unsignedToken = "$encodedHeader.$encodedPayload"

$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
$signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsignedToken)))

"$unsignedToken.$signature"
