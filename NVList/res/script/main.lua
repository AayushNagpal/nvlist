require("builtin/stdlib")
require("builtin/vn")

vn.flattenModule(_G)

function main()
	return titlescreen()
end

function titlescreen()
    globals:clear()
	setTextModeADV()
    
    while true do
        local selected = choice("Text", "Image", "Audio", "User Interface", "Special Effects", "Exit")
        if selected == 1 then
            call("text/00-index")
        elseif selected == 2 then
            call("image/00-index")
        elseif selected == 3 then
            call("audio/00-index")
        elseif selected == 4 then
            call("gui/00-index")
        elseif selected == 5 then
            call("effect/00-index")
        else
            break
        end
    end
    
    System.exit(true)
end
