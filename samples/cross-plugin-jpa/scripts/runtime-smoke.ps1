param(
  [int] $Port = 7791,
  [string] $Token = "sample-token",
  [int] $TimeoutSeconds = 120,
  [switch] $SkipAssemble
)

$ErrorActionPreference = "Stop"

function Write-Smoke($Message) {
  Write-Output $Message
}

function Invoke-SmokeRequest {
  param(
    [string] $Method,
    [string] $Path,
    [hashtable] $Headers = @{},
    [object] $Body = $null
  )
  $uri = "http://127.0.0.1:$Port$Path"
  $parameters = @{
    UseBasicParsing = $true
    Method = $Method
    Uri = $uri
    TimeoutSec = 20
    Headers = $Headers
  }
  if ($null -ne $Body) {
    $parameters["ContentType"] = "application/json"
    $parameters["Body"] = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  try {
    $response = Invoke-WebRequest @parameters
    return [pscustomobject]@{
      StatusCode = $response.StatusCode
      Content = $response.Content
      Json = if ([string]::IsNullOrWhiteSpace($response.Content)) { $null } else { $response.Content | ConvertFrom-Json }
    }
  } catch {
    $status = 0
    $content = $_.Exception.Message
    if ($_.Exception.Response -ne $null) {
      $status = [int]$_.Exception.Response.StatusCode
      try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()
      } catch {
      }
    }
    return [pscustomobject]@{
      StatusCode = $status
      Content = $content
      Json = $null
    }
  }
}

function Wait-HostReady {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $response = Invoke-SmokeRequest -Method "GET" -Path "/api/sample/workflow/summary"
    if ($response.StatusCode -eq 200) {
      Write-Smoke "SMOKE_HOST_READY port=$Port"
      return
    }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)
  throw "PFS-001 host not ready within $TimeoutSeconds seconds"
}

function Assert-Status {
  param(
    [object] $Response,
    [int[]] $Expected,
    [string] $Label
  )
  if ($Expected -notcontains $Response.StatusCode) {
    throw "$Label expected HTTP $($Expected -join '/') but got $($Response.StatusCode): $($Response.Content)"
  }
}

function Assert-AdminSuccess {
  param(
    [object] $Response,
    [string] $Label
  )
  Assert-Status -Response $Response -Expected @(200) -Label $Label
  if ($null -eq $Response.Json -or $Response.Json.success -ne $true) {
    throw "$Label expected management success response: $($Response.Content)"
  }
}

function Quote-CmdArg($Value) {
  return '"' + ($Value -replace '"', '\"') + '"'
}

function Stop-PortProcess {
  param([int] $TargetPort)
  $lines = netstat -ano | Select-String ":$TargetPort"
  foreach ($line in $lines) {
    if ($line.ToString() -notmatch "LISTENING") {
      continue
    }
    $parts = ($line.ToString() -split '\s+') | Where-Object { $_ }
    if ($parts.Length -ge 5) {
      $processId = $parts[-1]
      if ($processId -match '^\d+$') {
        & cmd.exe /c "taskkill /PID $processId /F >nul 2>nul"
      }
    }
  }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sampleRoot = Resolve-Path (Join-Path $scriptRoot "..")
$repoRoot = Resolve-Path (Join-Path $sampleRoot "..\..")
$gradle = Join-Path $repoRoot "gradlew.bat"
$runtime = Join-Path $sampleRoot "app-run\build\runtime"
$smokeDir = Join-Path $sampleRoot "app-run\build\tmp\runtime-smoke"
$logDir = Join-Path $smokeDir "logs"
$homeDir = Join-Path $smokeDir "home"
$operationStore = Join-Path $smokeDir "operations"
$stdoutLog = Join-Path $logDir "stdout.log"
$stderrLog = Join-Path $logDir "stderr.log"
$badZip = $null

try {
  if (Test-Path $smokeDir) {
    Remove-Item -LiteralPath $smokeDir -Recurse -Force
  }
  New-Item -ItemType Directory -Force -Path $logDir, $homeDir, $operationStore | Out-Null

  if (-not $SkipAssemble) {
    & $gradle ":samples:cross-plugin-jpa:app-run:assembleSampleRuntime"
    if ($LASTEXITCODE -ne 0) {
      throw "Gradle assembleSampleRuntime failed with exit code $LASTEXITCODE"
    }
  } elseif (-not (Test-Path (Join-Path $runtime "lib"))) {
    throw "SkipAssemble was requested but runtime lib directory does not exist: $runtime"
  }

  $staleBadZip = Join-Path $runtime "plugins\invalid-plugin.zip"
  if (Test-Path $staleBadZip) {
    Remove-Item -LiteralPath $staleBadZip -Force
  }

  $pluginZips = Get-ChildItem -LiteralPath (Join-Path $runtime "plugins") -Filter "*.zip"
  Write-Smoke "SMOKE_PLUGIN_ZIPS count=$($pluginZips.Count)"
  if ($pluginZips.Count -lt 3) {
    throw "Expected at least 3 plugin zips"
  }

  $javaArgs = @(
    "-Duser.home=$homeDir",
    "-cp",
    "lib/*",
    "net.xdob.sample.host.CrossPluginJpaSampleHost",
    "--spring.config.location=config/application.yml",
    "--server.port=$Port",
    "--spring.pf4boot.management.http.token=$Token",
    "--spring.pf4boot.management.http.operation-store.type=file",
    "--spring.pf4boot.management.http.operation-store.directory=$operationStore"
  )
  $javaExe = (Get-Command java).Source
  $commandLine = "cd /d $(Quote-CmdArg $runtime) && $(Quote-CmdArg $javaExe) " `
      + (($javaArgs | ForEach-Object { Quote-CmdArg $_ }) -join " ") `
      + " > $(Quote-CmdArg $stdoutLog) 2> $(Quote-CmdArg $stderrLog)"
  & cmd.exe /c "start """" /b cmd.exe /c ""$commandLine"""

  Wait-HostReady

  $normalUser = "smoke-ok-$([System.Guid]::NewGuid().ToString('N').Substring(0, 8))"
  $normal = Invoke-SmokeRequest -Method "GET" -Path "/api/sample/workflow/place?username=$normalUser&password=123&bookName=RuntimeBook"
  Assert-Status -Response $normal -Expected @(200) -Label "workflow normal"
  Write-Smoke "SMOKE_WORKFLOW_OK status=$($normal.StatusCode)"

  $beforeFailure = Invoke-SmokeRequest -Method "GET" -Path "/api/sample/workflow/summary"
  Assert-Status -Response $beforeFailure -Expected @(200) -Label "summary before failure"
  $failUser = "smoke-fail-$([System.Guid]::NewGuid().ToString('N').Substring(0, 8))"
  $failure = Invoke-SmokeRequest -Method "GET" -Path "/api/sample/workflow/place?username=$failUser&password=123&bookName=RollbackBook&failAfterAudit=true"
  Assert-Status -Response $failure -Expected @(500) -Label "workflow forced failure"
  $afterFailure = Invoke-SmokeRequest -Method "GET" -Path "/api/sample/workflow/summary"
  Assert-Status -Response $afterFailure -Expected @(200) -Label "summary after failure"
  if ($afterFailure.Json.users -ne $beforeFailure.Json.users -or $afterFailure.Json.books -ne $beforeFailure.Json.books) {
    throw "workflow rollback did not keep user/book counts stable"
  }
  if ([int64]$afterFailure.Json.audits -lt ([int64]$beforeFailure.Json.audits + 1)) {
    throw "workflow failure did not keep REQUIRES_NEW audit evidence"
  }
  Write-Smoke "SMOKE_WORKFLOW_ROLLBACK status=$($failure.StatusCode)"

  $adminHeaders = @{
    "X-PF4Boot-Admin-Token" = $Token
  }
  $plugins = Invoke-SmokeRequest -Method "GET" -Path "/pf4boot/admin/plugins" -Headers $adminHeaders
  Assert-AdminSuccess -Response $plugins -Label "management plugin list"

  $planHeaders = @{
    "X-PF4Boot-Admin-Token" = $Token
    "X-Idempotency-Key" = "runtime-smoke-plan"
  }
  $planBody = @{
    pluginId = "sample-workflow"
    stagedPluginPath = "plugin-workflow-3.0.0-SNAPSHOT.zip"
    dryRun = $true
  }
  $plan = Invoke-SmokeRequest -Method "POST" -Path "/pf4boot/admin/deployments/plan" -Headers $planHeaders -Body $planBody
  Assert-AdminSuccess -Response $plan -Label "management deployment plan"
  $planReplay = Invoke-SmokeRequest -Method "POST" -Path "/pf4boot/admin/deployments/plan" -Headers $planHeaders -Body $planBody
  Assert-AdminSuccess -Response $planReplay -Label "management deployment plan replay"
  if ($planReplay.Json.operationId -ne $plan.Json.operationId) {
    throw "idempotency replay did not return the original operation id"
  }
  Write-Smoke "SMOKE_MANAGEMENT_OPERATION operationId=$($plan.Json.operationId)"
  Write-Smoke "SMOKE_IDEMPOTENCY_REPLAY operationId=$($planReplay.Json.operationId)"

  $badHeaders = @{
    "X-PF4Boot-Admin-Token" = $Token
    "X-Idempotency-Key" = "runtime-smoke-bad-plan"
  }
  $badBody = @{
    pluginId = "missing-workflow"
    stagedPluginPath = "plugin-workflow-3.0.0-SNAPSHOT.zip"
    dryRun = $true
  }
  $badPlan = Invoke-SmokeRequest -Method "POST" -Path "/pf4boot/admin/deployments/plan" -Headers $badHeaders -Body $badBody
  Assert-Status -Response $badPlan -Expected @(200) -Label "bad deployment plan"
  if ($null -eq $badPlan.Json -or $badPlan.Json.success -ne $false) {
    throw "bad deployment plan should return a failed management response"
  }
  $deployments = Invoke-SmokeRequest -Method "GET" -Path "/pf4boot/admin/deployments" -Headers $adminHeaders
  Assert-AdminSuccess -Response $deployments -Label "deployment records"
  Write-Smoke "SMOKE_FAILURE_CASE code=$($badPlan.Json.code)"

  $governance = Invoke-SmokeRequest -Method "GET" -Path "/actuator/pf4bootgovernance"
  Assert-Status -Response $governance -Expected @(200) -Label "actuator governance"
  $metrics = Invoke-SmokeRequest -Method "GET" -Path "/actuator/metrics/pf4boot.management.request.total"
  Assert-Status -Response $metrics -Expected @(200) -Label "actuator management metric"
  Write-Smoke "SMOKE_ACTUATOR_GOVERNANCE status=$($governance.StatusCode)"

  Write-Smoke "SMOKE_CLEANUP_OK"
} catch {
  Write-Output "SMOKE_FAILED $($_.Exception.Message)"
  if (Test-Path $stdoutLog) {
    Write-Output "---- stdout tail ----"
    Get-Content -Tail 120 -LiteralPath $stdoutLog
  }
  if (Test-Path $stderrLog) {
    Write-Output "---- stderr tail ----"
    Get-Content -Tail 120 -LiteralPath $stderrLog
  }
  throw
} finally {
  Stop-PortProcess -TargetPort $Port
  if ($badZip -ne $null -and (Test-Path $badZip)) {
    Remove-Item -LiteralPath $badZip -Force
  }
}
