param(
    [Parameter(Mandatory = $true)][string]$BaselinePlugins,
    [ValidatePattern('^[0-9a-fA-F]{7,40}$')][string]$BaselineRevision = '81526a7',
    [int]$Port = 7793,
    [switch]$Production
)

$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSScriptRoot 'build\runtime'
$baseline = (Resolve-Path -LiteralPath $BaselinePlugins).Path
$work = Join-Path $PSScriptRoot ('build\compatibility-' + [Guid]::NewGuid().ToString('N'))
$reportName = if ($Production) { 'legacy-plugin-compatibility-production' } else { 'legacy-plugin-compatibility' }
if ($BaselineRevision -ne '81526a7') { $reportName += '-' + $BaselineRevision }
$reports = Join-Path $PSScriptRoot ('build\reports\' + $reportName)
$archives = @(Get-ChildItem -LiteralPath $baseline -File -Filter '*.zip')
if ($archives.Count -ne 4) { throw 'Expected four prebuilt baseline plugin zips.' }
New-Item -ItemType Directory -Path $work, $reports, (Join-Path $work 'plugins') -Force | Out-Null
# 使用当前框架文件和原始旧插件，不在基线目录重编译或覆盖任何文件。
Copy-Item -LiteralPath (Join-Path $runtime 'lib'), (Join-Path $runtime 'config') -Destination $work -Recurse
Get-ChildItem -LiteralPath $baseline -File | Where-Object { $_.Name -match '\.zip($|\.)' } |
    ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $work 'plugins') }
$pluginHashes = @($archives | ForEach-Object {
    [ordered]@{ name = $_.Name; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
$frameworkHashes = @(Get-ChildItem -LiteralPath (Join-Path $work 'lib') -Filter 'pf4boot-*.jar' | ForEach-Object {
    [ordered]@{ name = $_.Name; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})

# 保存旧插件的实际类签名，避免把新版夹具误作旧二进制验收。
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $baseline 'plugin-unrelated-service-3.3.0-SNAPSHOT.zip'))
try {
    $entry = @($zip.Entries | Where-Object { $_.FullName -match '/plugin-unrelated-service-.*\.jar$' })[0]
    $stream = $entry.Open()
    $output = [System.IO.File]::Create((Join-Path $work 'baseline-unrelated.jar'))
    try { $stream.CopyTo($output) } finally { $output.Dispose(); $stream.Dispose() }
} finally { $zip.Dispose() }
$signature = @(& javap -classpath (Join-Path $work 'baseline-unrelated.jar') net.xdob.sample.unrelated.UnrelatedServicePlugin)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect baseline plugin class.' }

$report = [ordered]@{
    status = 'FAILED'; baselineRevision = $BaselineRevision; baselineClass = $signature
    profile = $(if ($Production) { 'production' } else { 'default' })
    plugins = $pluginHashes; framework = $frameworkHashes; workDirectory = $work; failure = $null
}
$process = $null
try {
    $arguments = @('-XX:SoftRefLRUPolicyMSPerMB=0', ('-Duser.home="' + (Join-Path $work 'home') + '"'),
        '-Dsample.cleanup-probe-enabled=true', '-cp', 'lib/*', 'net.xdob.sample.host.CrossPluginJpaSampleHost',
        "--server.port=$Port",
        '--sample.lifecycle-probe-enabled=true', '--spring.pf4boot.management.http.token=sample-token')
    if ($Production) {
        $arguments += '--spring.config.location=config/application.yml,config/application-production.yml', '--spring.profiles.active=production'
    } else { $arguments += '--spring.config.location=config/application.yml' }
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments -WorkingDirectory $work `
        -RedirectStandardOutput (Join-Path $work 'stdout.log') -RedirectStandardError (Join-Path $work 'stderr.log') `
        -PassThru -WindowStyle Hidden
    $base = "http://127.0.0.1:$Port"
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { throw "Legacy host exited; inspect $work" }
        try {
            $null = Invoke-RestMethod "$base/api/sample/workflow/summary" -TimeoutSec 2
            $health = Invoke-RestMethod "$base/api/sample/unrelated/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (!$ready) { throw "Legacy host not ready; inspect $work" }
    $headers = @{ 'X-PF4Boot-Admin-Token' = 'sample-token' }
    $lifecycle = Invoke-RestMethod "$base/api/sample/lifecycle-probe" -Method Post -Headers $headers `
        -ContentType 'application/json' -Body '{}' -TimeoutSec 90
    if ($lifecycle.restarts -ne 20 -or $lifecycle.reloads -ne 10 -or
        $lifecycle.collectedLoaders -ne 10 -or $lifecycle.failureRecovery -ne 'PASSED') {
        throw 'Legacy plugin lifecycle checks did not complete.'
    }
    $null = Invoke-RestMethod "$base/api/sample/workflow/summary" -TimeoutSec 5
    $health = Invoke-RestMethod "$base/api/sample/unrelated/health" -TimeoutSec 5
    if ($health.status -ne 'UP') { throw 'Legacy plugin business endpoint unavailable after reload.' }
    $report.lifecycle = $lifecycle
    $report.status = 'PASSED'
} catch {
    $report.failure = $_.Exception.Message
    throw
} finally {
    if ($null -ne $process -and !$process.HasExited) { Stop-Process -Id $process.Id -Force; $process.WaitForExit(5000) | Out-Null }
    $json = $report | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText((Join-Path $reports 'result.json'), $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output $json
}
