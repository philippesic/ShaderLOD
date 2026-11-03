-- Retained development helper. Operates only the PID verified by dev_client_ui.py.
-- No preferences, permissions, files, or application installations are changed.
on run args
    set ownedPid to (item 1 of args) as integer
    set requestedAction to item 2 of args
    tell application "System Events"
        set matches to application processes whose unix id is ownedPid
        if (count of matches) is not 1 then error "Owned UI process is unavailable"
        set ownedProcess to item 1 of matches
        if requestedAction is "focus" then
            set frontmost of ownedProcess to true
            return {name of ownedProcess, frontmost of ownedProcess}
        end if
        if frontmost of ownedProcess is not true then error "Owned client is not frontmost; refusing input"
        if requestedAction is "inspect" then
            return {name of ownedProcess, frontmost of ownedProcess, name of windows of ownedProcess}
        else if requestedAction is "key" then
            set keyNumber to (item 3 of args) as integer
            if keyNumber is not in {31, 53, 120} then error "Only Iris options (O), Escape and F2 are supported"
            key code keyNumber
        else if requestedAction is "click" then
            set clickX to (item 3 of args) as integer
            set clickY to (item 4 of args) as integer
            if clickX < 0 or clickY < 0 then error "Invalid coordinates"
            click at {clickX, clickY}
        else
            error "Unsupported owned-client UI action"
        end if
        return {name of ownedProcess, frontmost of ownedProcess}
    end tell
end run
