<#
.SYNOPSIS
    CLI wrapper for CLDPlatform DaemonServer.
.EXAMPLE
    .\cld cu list
    .\cld cu create jar target\app.jar
    .\cld cu run <unit-id>
#>
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

if (-not $CommandArgs -or $CommandArgs.Count -eq 0) {
    Write-Host "===================================================" -ForegroundColor Cyan
    Write-Host "               CLD Platform CLI                    " -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan
    Write-Host "Usage:"
    Write-Host "  cld --help"
    Write-Host "  cld cu --help"
    Write-Host "  cld cu list                 (CMD: cld, PowerShell: .\cld)"
    Write-Host "  cld cu create"
    Write-Host "  cld cu create [jar|js] <file-path>"
    Write-Host "  cld cu create --capacity <MICRO|MEDIUM|LARGE|ULTRA> [--name <name>]"
    Write-Host "  cld cu assign <unit-id> [jar|js] <file-path>"
    Write-Host "  cld cu run <unit-id>"
    Write-Host "  cld cu status <unit-id>"
    Write-Host "  cld cu stop <unit-id>"
    Write-Host "  cld cu release <unit-id>"
    Write-Host "  cld cu kill <unit-id>"
    Write-Host "===================================================" -ForegroundColor Cyan
    exit 0
}

$hasClDPrefix = $CommandArgs.Count -gt 0 -and $CommandArgs[0].Equals('cld', [StringComparison]::OrdinalIgnoreCase)
$escapedArgs = $CommandArgs | ForEach-Object {
    '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"'
}
$cmdLine = ($escapedArgs -join ' ').Trim()
if (-not $hasClDPrefix) {
    $cmdLine = 'cld ' + $cmdLine
}

try {
    $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 9090)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $reader = New-Object System.IO.StreamReader($stream)

    $writer.WriteLine($cmdLine)
    $writer.Flush()

    # Signal EOF for sending to receive full multi-line response
    $client.Client.Shutdown([System.Net.Sockets.SocketShutdown]::Send)

    $response = $reader.ReadToEnd()
    if (-not [string]::IsNullOrEmpty($response)) {
        Write-Output $response.Trim()
    }

    $client.Close()
} catch {
    Write-Host "Error: Could not connect to CLD DaemonServer on port 9090. Is the application running?" -ForegroundColor Red
    exit 1
}
