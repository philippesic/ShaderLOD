use AppleScript version "2.4"
use framework "AppKit"

-- STAGED ONLY. Never invoke without the parent's existing identity/one-attempt guard.
-- This performs one activation request and sends no UI input or Apple events.
-- PID is a lookup key, not sufficient ownership proof; wrapper must check birth/argv.
on run argv
    if (count of argv) is not 2 then error "Validated owned PID and activation option required"
    set ownedPid to (item 1 of argv) as integer
    if ownedPid < 1 or ownedPid > 2147483647 then error "Invalid owned PID"
    set ownedApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:ownedPid
    if ownedApp is missing value then error "Owned PID has no tracked AppKit application"
    if ((ownedApp's processIdentifier()) as integer) is not ownedPid then error "Owned PID lookup mismatch"
    if ((ownedApp's isTerminated()) as boolean) then error "Owned application has terminated"
    if ((ownedApp's activationPolicy()) as integer) is 2 then error "Owned application activation is prohibited"
    set activationOptions to (item 2 of argv) as integer
    if activationOptions is not in {0, 1} then error "Only default or AllWindows activation is supported"
    set requestSent to (ownedApp's activateWithOptions:activationOptions) as boolean
    if requestSent is not true then error "Owned application activation request was not sent"
    return {"pid", ownedPid, "activationOptions", activationOptions, "activationRequestSent", requestSent}
end run
