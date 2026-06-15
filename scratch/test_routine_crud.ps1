# USTC LearnX - Programmatic Security and CRUD Validation for Routines
$baseUrl = "http://localhost:8080"

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "USTC LearnX: Routine Schedule CRUD Programmatic Verification" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

# Helper to log messages in colors
function Log-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Gray }
function Log-Success($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Log-Error($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

# 1. Login as CR (cr1)
Log-Info "Logging in as CR 'cr1'..."
$loginBody = @{
    username = "cr1"
    password = "password"
} | ConvertTo-Json

try {
    $loginRes = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -SessionVariable crSession -UseBasicParsing
    Log-Success "Logged in as CR successfully!"
} catch {
    Log-Error "Failed to log in as CR: $_"
    exit 1
}

# 2. Get initial routines
Log-Info "Fetching current routine classes..."
try {
    $routinesRes = Invoke-RestMethod -Uri "$baseUrl/api/schedule/routine" -Method Get -WebSession $crSession -UseBasicParsing
    Log-Success "Fetched $($routinesRes.Count) initial routine classes successfully!"
} catch {
    Log-Error "Failed to fetch routines: $_"
    exit 1
}

# 3. Create a new routine class
Log-Info "Adding a new routine class 'CSE 3105'..."
$newRoutine = @{
    courseName = "CSE 3105 - Compiler Design"
    dayOfWeek = "MONDAY"
    startTime = "10:00:00"
    endTime = "11:15:00"
    roomNo = "402"
    teacherName = "Dr. Rahim"
} | ConvertTo-Json

try {
    $createRes = Invoke-RestMethod -Uri "$baseUrl/api/schedule/routine" -Method Post -Body $newRoutine -ContentType "application/json" -WebSession $crSession -UseBasicParsing
    $routineId = $createRes.id
    if ($routineId -ne $null) {
        Log-Success "Routine class created successfully! Assigned ID: $routineId"
        if ($createRes.studentClass -ne $null) {
            Log-Success "Auto-scoping check: Routine is bound to class group '$($createRes.studentClass.className)'!"
        } else {
            Log-Error "Auto-scoping check: studentClass was null on creation!"
        }
    } else {
        Log-Error "Failed to parse routine ID from response."
        exit 1
    }
} catch {
    Log-Error "Failed to create routine: $_"
    exit 1
}

# 4. Edit the routine class (Update room from 402 to 501)
Log-Info "Editing routine class (changing Room to 501, Teacher to Dr. Rahim (Updated))..."
$updatedRoutine = @{
    courseName = "CSE 3105 - Compiler Design"
    dayOfWeek = "MONDAY"
    startTime = "10:00:00"
    endTime = "11:15:00"
    roomNo = "501"
    teacherName = "Dr. Rahim (Updated)"
} | ConvertTo-Json

try {
    $updateRes = Invoke-RestMethod -Uri "$baseUrl/api/schedule/routine/$routineId" -Method Put -Body $updatedRoutine -ContentType "application/json" -WebSession $crSession -UseBasicParsing
    if ($updateRes.roomNo -eq "501" -and $updateRes.teacherName -eq "Dr. Rahim (Updated)") {
        Log-Success "Routine class updated successfully in backend!"
    } else {
        Log-Error "Routine class update failed: Room or Teacher did not match expected values."
        exit 1
    }
} catch {
    Log-Error "Failed to update routine: $_"
    exit 1
}

# 5. Clean up: Delete the routine class
Log-Info "Deleting the created routine class..."
try {
    $deleteRes = Invoke-RestMethod -Uri "$baseUrl/api/schedule/routine/$routineId" -Method Delete -WebSession $crSession -UseBasicParsing
    Log-Success "Routine class deleted successfully! Response: $($deleteRes.message)"
} catch {
    Log-Error "Failed to delete routine class: $_"
    exit 1
}

# 6. Verify Security Check: regular student cannot add routine
Log-Info "Logging in as Student 'student1'..."
$studentLoginBody = @{
    username = "student1"
    password = "password"
} | ConvertTo-Json

try {
    $studentLoginRes = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body $studentLoginBody -ContentType "application/json" -SessionVariable studentSession -UseBasicParsing
    Log-Success "Logged in as Student successfully!"
} catch {
    Log-Error "Failed to log in as student: $_"
    exit 1
}

Log-Info "Attempting to POST routine class under Student role (should fail with 403 Forbidden)..."
try {
    $studentCreateRes = Invoke-WebRequest -Uri "$baseUrl/api/schedule/routine" -Method Post -Body $newRoutine -ContentType "application/json" -WebSession $studentSession -UseBasicParsing
    Log-Error "Security breach! Student was able to create routine class!"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    if ($statusCode -eq 403) {
        Log-Success "Security check passed: Student access was blocked with 403 Forbidden!"
    } else {
        Log-Error "Unexpected response code: $statusCode"
        exit 1
    }
}

Write-Host "=========================================================" -ForegroundColor Green
Write-Host "ALL PROGRAMMATIC VERIFICATION TESTS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
