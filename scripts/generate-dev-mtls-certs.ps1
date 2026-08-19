param(
    [string] $OutputDirectory = "secrets/mtls",
    [string] $PaymentServicePassword = "change_me_payment_service_keystore_password",
    [string] $PaymentProcessorPassword = "change_me_payment_processor_keystore_password",
    [string] $TruststorePassword = "change_me_highpay_truststore_password"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$caKeyStore = Join-Path $OutputDirectory "highpay-ca.p12"
$caCert = Join-Path $OutputDirectory "highpay-ca.crt"
$paymentServiceStore = Join-Path $OutputDirectory "payment-service.p12"
$paymentServiceCsr = Join-Path $OutputDirectory "payment-service.csr"
$paymentServiceCert = Join-Path $OutputDirectory "payment-service.crt"
$paymentProcessorStore = Join-Path $OutputDirectory "payment-processor.p12"
$paymentProcessorCsr = Join-Path $OutputDirectory "payment-processor.csr"
$paymentProcessorCert = Join-Path $OutputDirectory "payment-processor.crt"
$trustStore = Join-Path $OutputDirectory "highpay-truststore.p12"

Remove-Item -Force `
    $caKeyStore, $caCert, `
    $paymentServiceStore, $paymentServiceCsr, $paymentServiceCert, `
    $paymentProcessorStore, $paymentProcessorCsr, $paymentProcessorCert, `
    $trustStore `
    -ErrorAction SilentlyContinue

keytool -genkeypair `
    -alias highpay-ca `
    -keyalg RSA `
    -keysize 4096 `
    -validity 3650 `
    -dname "CN=HighPay Dev CA,O=HighPay,L=Local,C=BR" `
    -ext bc:c `
    -keystore $caKeyStore `
    -storetype PKCS12 `
    -storepass $TruststorePassword `
    -keypass $TruststorePassword

keytool -exportcert `
    -alias highpay-ca `
    -rfc `
    -keystore $caKeyStore `
    -storepass $TruststorePassword `
    -file $caCert

keytool -genkeypair `
    -alias payment-service `
    -keyalg RSA `
    -keysize 2048 `
    -validity 825 `
    -dname "CN=payment-service,O=HighPay,L=Local,C=BR" `
    -ext "SAN=dns:payment-service,dns:localhost,ip:127.0.0.1" `
    -keystore $paymentServiceStore `
    -storetype PKCS12 `
    -storepass $PaymentServicePassword `
    -keypass $PaymentServicePassword

keytool -certreq `
    -alias payment-service `
    -keystore $paymentServiceStore `
    -storepass $PaymentServicePassword `
    -file $paymentServiceCsr

keytool -gencert `
    -alias highpay-ca `
    -keystore $caKeyStore `
    -storepass $TruststorePassword `
    -infile $paymentServiceCsr `
    -outfile $paymentServiceCert `
    -rfc `
    -validity 825 `
    -ext "SAN=dns:payment-service,dns:localhost,ip:127.0.0.1" `
    -ext "KU=digitalSignature,keyEncipherment" `
    -ext "EKU=serverAuth"

keytool -importcert `
    -alias highpay-ca `
    -keystore $paymentServiceStore `
    -storepass $PaymentServicePassword `
    -file $caCert `
    -noprompt

keytool -importcert `
    -alias payment-service `
    -keystore $paymentServiceStore `
    -storepass $PaymentServicePassword `
    -file $paymentServiceCert

keytool -genkeypair `
    -alias payment-processor `
    -keyalg RSA `
    -keysize 2048 `
    -validity 825 `
    -dname "CN=payment-processor,O=HighPay,L=Local,C=BR" `
    -keystore $paymentProcessorStore `
    -storetype PKCS12 `
    -storepass $PaymentProcessorPassword `
    -keypass $PaymentProcessorPassword

keytool -certreq `
    -alias payment-processor `
    -keystore $paymentProcessorStore `
    -storepass $PaymentProcessorPassword `
    -file $paymentProcessorCsr

keytool -gencert `
    -alias highpay-ca `
    -keystore $caKeyStore `
    -storepass $TruststorePassword `
    -infile $paymentProcessorCsr `
    -outfile $paymentProcessorCert `
    -rfc `
    -validity 825 `
    -ext "KU=digitalSignature,keyEncipherment" `
    -ext "EKU=clientAuth"

keytool -importcert `
    -alias highpay-ca `
    -keystore $paymentProcessorStore `
    -storepass $PaymentProcessorPassword `
    -file $caCert `
    -noprompt

keytool -importcert `
    -alias payment-processor `
    -keystore $paymentProcessorStore `
    -storepass $PaymentProcessorPassword `
    -file $paymentProcessorCert

keytool -importcert `
    -alias highpay-ca `
    -keystore $trustStore `
    -storetype PKCS12 `
    -storepass $TruststorePassword `
    -file $caCert `
    -noprompt

Write-Host "Generated development mTLS material in $OutputDirectory"
