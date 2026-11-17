<#
.SYNOPSIS
生成控制台管理员密码的 PBKDF2-SHA256 哈希（与 Pbkdf2PasswordHasher 格式一致）。

.EXAMPLE
.\scripts\hash-console-password.ps1 -Password 'your-password'
#>
param(
    [Parameter(Mandatory = $true)][string]$Password,
    [int]$Iterations = 210000
)

$saltBytes = New-Object byte[] 16
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($saltBytes)
} finally {
    $rng.Dispose()
}

$kdf = New-Object Security.Cryptography.Rfc2898DeriveBytes(
    [Text.Encoding]::UTF8.GetBytes($Password), $saltBytes, $Iterations,
    [Security.Cryptography.HashAlgorithmName]::SHA256)
try {
    $hashBytes = $kdf.GetBytes(32)
} finally {
    $kdf.Dispose()
}

$encode = [Convert]::ToBase64String
"pbkdf2-sha256`$$Iterations`$" + $encode.Invoke($saltBytes) + "`$" + $encode.Invoke($hashBytes)
