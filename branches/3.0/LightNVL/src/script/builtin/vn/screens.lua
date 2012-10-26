-------------------------------------------------------------------------------
-- screens.lua
-------------------------------------------------------------------------------
-- Defines the standard user interface screens.
-------------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
--  Save/Load Screen
-- ----------------------------------------------------------------------------

local KEY_SAVE_LAST = "vn.save.lastSaved" --Property value gets set in onSave()
local KEY_SAVE_PAGE = "vn.save.lastPage"

local SaveSlot = {
	slot=1,
	button=nil,
	image=nil,
	label=nil,
	isSave=false,
	empty=true,
	new=false,
	newImage=nil,
	compact=false,
	backgroundImagePath="gui/savescreen#slotButton-"
	}

function SaveSlot.new(self)
	self = extend(SaveSlot, self)
	
	local buttonImagePath = "gui/savescreen#slotButton-"
	if self.compact then
		buttonImagePath = "gui/savescreen#quicksave-"
	end
	local b = button(buttonImagePath)
	b:setToggle(true)
	b:setEnabled(self.isSave or not self.empty)
	
	local l = textimg(self.label)
	--l:setBackgroundColorARGB(0xA0000000)
	l:setZ(b:getZ() - 10)
	l:setPadding(8)
	l:setAnchor(1)
	
	local i = nil	
	local newI = nil
	if not self.compact then
		if self.screenshot ~= nil then
			i = img(self.screenshot)
		elseif not self.empty then
			i = img("gui/savescreen#noImage")
		end
		if i ~= nil then
			i:setZ(b:getZ() - b:getWidth()/2)	
		end
		
		if self.new and not self.empty then
			newI = img("gui/savescreen#newSave")
			newI:setZ(i:getZ() - 1)
		end
	end

	self.button = b
	self.image = i
	self.label = l
	self.newImage = newI
	
	return self
end

function SaveSlot:destroy()
	self.button:destroy()
	if self.image ~= nil then
		self.image:destroy()
	end
	if self.newImage ~= nil then
		self.newImage:destroy()
	end
	self.label:destroy()
end

function SaveSlot:setPos(x, y)
	local b = self.button
	local l = self.label
	local i = self.image

	b:setPos(x, y)
	l:setPos(x, y + b:getHeight() - l:getHeight())

	if i ~= nil then	
		local pad = (b:getWidth() - i:getWidth())/2
		i:setPos(x + pad, y + pad)
	end	
	
	self:layout()
end

function SaveSlot:layout()
	local i = self.image
	local b = self.button	
	local newI = self.newImage
	if newI ~= nil then
		if i ~= nil then	
			--Align with top-right of screenshot
			newI:setPos(i:getX() + i:getWidth() - newI:getWidth(), i:getY())
		else
			--Align with top-right of button
			newI:setPos(b:getX() + b:getWidth() - newI:getWidth(), b:getY())
		end
	end
end

function SaveSlot:getWidth()
	return self.button:getWidth()
end

function SaveSlot:getHeight()
	return self.button:getHeight()
end

function SaveSlot:setSize(w, h)
	local b = self.button
	local l = self.label
	local i = self.image
	
	local scale = math.min(w / b:getUnscaledWidth(), h / b:getUnscaledHeight())
	b:setScale(scale, scale)

	l:setDefaultStyle(createStyle{fontName="sans serif", anchor=5, fontSize=scale * 16, shadowDx=1, shadowDy=1})

	if i ~= nil then
		local imgScale = scale * math.min(224 / i:getUnscaledWidth(), 126 / i:getUnscaledHeight())
		i:setScale(imgScale, imgScale)
		
		l:setSize(b:getWidth(), b:getHeight()-i:getHeight())
	else	
		l:setSize(b:getWidth(), b:getHeight())
	end	

	self:layout()
end

function SaveSlot:update()
	--[[
	if self.button:isEnabled() and self.button:isRollover() and not self.button:isPressed() then
		if self.image ~= nil then
			self.image:setBlendMode(BlendMode.ADD)
		end
	else
		if self.image ~= nil then
			self.image:setBlendMode(BlendMode.DEFAULT)
		end
	end
	--]]
end

-- ----------------------------------------------------------------------------

local SaveLoadScreen = {
	isSave=false,
	page=nil,
	pages=10,
	selected=0,
	metaData=nil, --Meta data Lua table added to the save data.
	rows=2,
	cols=5,
	newSaveSlot=SaveSlot.new,
	x=0,
	y=0,
	w=screenWidth,
	h=screenHeight,
	pack=5,
	qcols=-1,
	qh=screenHeight/14,
	qpack=6,
	pad=nil,
	--GUI Components
	pageButtons=nil,
	saves=nil,
	qsaves=nil,
	okButton=nil,
	cancelButton=nil,
	topFade=nil,
	bottomFade=nil,
	pageButtonLayout=nil,
	saveLayout=nil,
	qsaveLayout=nil,
	buttonBarLayout=nil,
	}

function SaveLoadScreen.new(self)
	self = extend(SaveLoadScreen, self)
				
	self.page = self.page or getSharedGlobal(KEY_SAVE_PAGE) or 1
	self.pad = self.pad or math.min(self.w, self.h) / 100
				
	self.saves = {}
	self.qsaves = {}		
	
	self.pageButtons = {}		
	for p=1,self.pages do
		local tb = TextButton.new("gui/savescreen#pageButton-", p)
		tb.button:setToggle(true)
		self.pageButtons[p] = tb
	end
	
	local okText = "Load"
	if self.isSave then
		okText = "Save"
	end
	self.okButton = TextButton.new("gui/savescreen#button-", okText)
	
	self.cancelButton = TextButton.new("gui/savescreen#button-", "Cancel")
	
	self.topFade = img("gui/savescreen#fade-top")
	self.topFade:setZ(10)

	self.bottomFade = img("gui/savescreen#fade-bottom")
	self.bottomFade:setZ(10)
	
	local sz = self.okButton:getHeight() / 2.5	
	local buttonStyle = createStyle{fontName="sans serif", fontStyle="bold", fontSize=sz, shadowColor=0}
	self.okButton.text:setDefaultStyle(buttonStyle)
	self.cancelButton.text:setDefaultStyle(buttonStyle)
	for _,tb in pairs(self.pageButtons) do
		tb.text:setDefaultStyle(buttonStyle)
	end
		
	self:setPage(self.page, true)
	self:initQSaves()
		
	return self
end

function SaveLoadScreen:destroy()
	destroyValues(self.pageButtons)
	destroyValues(self.saves)
	destroyValues(self.qsaves)
	destroyValues{self.okButton, self.cancelButton}
end

function SaveLoadScreen:layout()
	local x = self.x
	local y = self.y
	local w = self.w
	local h = self.h
	local qh = self.qh
	
	local ipad = self.pad
	local vpad = h / 7
	local mainW = w-ipad*2
	local mainH = h - vpad*2 - qh - ipad*3

	self.pageButtonLayout = GridLayout.new{x=x, y=y, w=w, h=vpad, pad=ipad, pack=5,
		children=self.pageButtons}
	self.pageButtonLayout:layout()
	self.saveLayout = GridLayout.new{x=x+ipad, y=y+vpad+ipad, w=mainW, h=mainH, cols=self.cols, pad=ipad,
		children=self.saves, fillW=true, fillH=true, pack=self.pack}
	self.saveLayout:layout()
	self.qsaveLayout = GridLayout.new{x=x+ipad, y=y+h-vpad-qh-ipad, w=mainW, h=qh, cols=self.qcols,
		pad=ipad, children=self.qsaves, fillW=true, fillH=true, pack=self.qpack}
	self.qsaveLayout:layout()
	self.buttonBarLayout = GridLayout.new{x=x, y=y+h-vpad, w=w, h=vpad, pad=ipad, pack=5,
		children={self.okButton, self.cancelButton}}
	self.buttonBarLayout:layout()
		
	self.topFade:setBounds(x, y, w, vpad)
	self.bottomFade:setBounds(x, y+math.ceil(h-vpad), w, vpad)
end

function SaveLoadScreen:initQSaves()
	destroyValues(self.qsaves)
	self.qsaves = {}
	for pass=1,2 do
		local defaultLabel = "autosave"
		local startSlot = Save.getAutoSaveSlot(1)
		local endSlot = startSlot + getAutoSaveSlots()
		if pass == 2 then
			startSlot = Save.getQuickSaveSlot(1)
			endSlot = startSlot + 1
			defaultLabel = "quicksave"
		end

		local saved = Save.getSaves(startSlot, endSlot)
		local sorted = {}
		for _,si in pairs(saved) do
			table.insert(sorted, si)
		end		
        table.sort(sorted, function(x, y)
        	--print(x:getTimestamp(), y:getTimestamp())
        	return x:getTimestamp() > y:getTimestamp()
        end)
        
		for i=1,endSlot-startSlot do
			local slot = i
			local label = "Empty\n" .. defaultLabel .. " " .. slot
			local empty = true
			
			local si = sorted[i]
			if si ~= nil then
				slot = si:getSlot()
				label = defaultLabel .. " " .. i .. "\n" .. si:getDateString()
				empty = false
			end
			
			local ss = self.newSaveSlot{slot=slot, label=label, empty=empty,
				isSave=self.isSave, new=false, compact=true}
			table.insert(self.qsaves, ss)
		end
	end
end

function SaveLoadScreen:getPage()
	return self.page
end

function SaveLoadScreen:setPage(p, force)
	for i,pb in ipairs(self.pageButtons) do
		pb.button:setSelected(i == p) 
	end

	if self.page ~= p or force then
		self.page = p				
		
		--Destroy old slots
		destroyValues(self.saves);
		self.saves = {}				
		
		--Create new slots
		local slotsPerPage = self.rows * self.cols
		local pageStart = 1 + (p - 1) * slotsPerPage
		local pageEnd   = 1 + (p    ) * slotsPerPage
		local saved = Save.getSaves(pageStart, pageEnd)	
		
		for i=pageStart,pageEnd-1 do
			local slot = i
			local screenshot = nil
			local label = "Empty " .. slot
			local empty = true
			local new = false
			
			local si = saved[i]
			if si ~= nil then
				slot = si:getSlot()
				screenshot = si:getScreenshot()
				label = si:getLabel()
				empty = false
				new = (getSharedGlobal(KEY_SAVE_LAST) == i)
			end
			
			local ss = self.newSaveSlot{slot=slot, label=label, empty=empty, screenshot=screenshot,
				isSave=self.isSave, new=new}
			table.insert(self.saves, ss)
		end
		
		if self.selected < pageStart or self.selected >= pageEnd then
			self.selected = 0
		end
		self:setSelected(self.selected)
		
		self:layout()
	end
end

function SaveLoadScreen:setSelected(s)
	self.selected = s
	for _,save in ipairs(self.saves) do
		save.button:setSelected(save.slot == s)
	end
	for _,save in ipairs(self.qsaves) do
		save.button:setSelected(save.slot == s)
	end
end

function SaveLoadScreen:run()
	self:layout()

	while not input:consumeCancel() do
		for i,pb in ipairs(self.pageButtons) do
			if pb.button:consumePress() then
				self:setPage(i)
			end
		end
		for _,save in ipairs(self.saves) do
			save:update()
			if save.button:consumePress() then
				self:setSelected(save.slot)
				break
			end
		end
		for _,save in ipairs(self.qsaves) do
			save:update()
			if save.button:consumePress() then
				self:setSelected(save.slot)
				break
			end
		end
		
		self.okButton.button:setEnabled(self.selected ~= 0)
		if self.okButton.button:consumePress() then
			break
		end
		
		if self.cancelButton.button:consumePress() then
			self.selected = 0
			break
		end
		
		yield()
	end
	
	setSharedGlobal(KEY_SAVE_PAGE, self:getPage())
	
	return self.selected, self.metaData
end

-- ----------------------------------------------------------------------------
--  Text Log
-- ----------------------------------------------------------------------------

local TextLogScreen = {
	viewport=nil,
}

function TextLogScreen.new(self)
	self = extend(TextLogScreen, self)
	
	return self
end

function TextLogScreen:destroy()
	if self.viewport ~= nil then
		self.viewport:destroy()
		self.viewport = nil
	end
end

function TextLogScreen:run()	
    local pathPrefix = "gui/textlog#"
    if android then
        pathPrefix = "android/textlog#"
    end
    
    local function timg(filename, ...)
        return img(pathPrefix .. filename, ...)
    end
    local function tbutton(filename, ...)
        return button(pathPrefix .. filename, ...)
    end
    
    local w = screenWidth
    local h = screenHeight
	local sz = math.min(w, h)
	local vpad  = 0.03 * sz
	local bh    = 0.15 * sz
	local tl = textState:getTextLog()
	local pages = tl:getPageCount()
	if System.isLowEnd() then
		pages = math.min(pages, 25) --Limit number of pages
	end
	local page = pages-1
	local lw = w-vpad
	local lh = h-bh-vpad*2
    
    --Create edge images
    if not android then
		local topEdge = timg("edge-top", {bounds={0, 0, w, vpad}, z=10})
	end
	local bottomEdge = timg("edge-bottom", {bounds={0, h-bh-vpad+1, w, bh+vpad}, z=10})
    
	--Create controls
	local returnButton = tbutton("return-")
    local returnButtonScale = math.min(1, (.90 * bh) / returnButton:getHeight())
    returnButton:setScale(returnButtonScale, returnButtonScale)
	if System.isTouchScreen() then
		returnButton:setPadding(bh/2)
	end
	returnButton:addActivationKeys(Keys.RIGHT, Keys.DOWN)
    
	local buttonBarLayout = GridLayout.new{y=h-bh-vpad, w=w, h=bh+vpad, pad=bh/4, pack=5, children={returnButton}}
	buttonBarLayout:layout()    

	--Create viewport and fill with text pages
	viewport = Viewport.new{pad=vpad, scrollBarPad=vpad}
	viewport:setBounds(0, vpad, lw, lh)
	viewport:setZ(1000)
	
	local pd = {}
	local x = 0
	local y = 0
    local defaultStyle = prefs.textLogStyle or createStyle{color=0xFFFFFF80}
    local iw = viewport:getInnerWidth()
    
    viewport:openLayer()
	for p=pages,1,-1 do
		local t = textimg()
		t:setPos(x, y)
		t:setSize(iw, 999999)
		t:setDefaultStyle(defaultStyle)
		t:setText(tl:getPage(-p))
		t:setSize(iw, t:getTextHeight())
		
		if System.isLowEnd() then
			t:setBlendMode(BlendMode.OPAQUE)
		end
		
		pd[p] = t
		y = y + t:getTextHeight()
		if p > 1 then
			--Not the final page, add spacing equal to
			local endLine = t:getEndLine()
			if endLine > 0 then
				y = y + t:getTextHeight(endLine-1, endLine)
			end
		end
	end
	viewport:closeLayer(pd)
	viewport:scrollTo(0, 1)
		
	--User interaction loop
	while not input:consumeCancel() and not input:consumeDown() do
		if returnButton:consumePress() then
			break
		end
		viewport:update()		
		yield()
	end	
end

-- ----------------------------------------------------------------------------
--  Choice
-- ----------------------------------------------------------------------------

local ChoiceScreen = {
	cancelled=false,
	selected=-1,
	options=nil,
	buttons=nil,
	choiceStyle=nil,
	selectedChoiceStyle=nil,
	offsetY=screenHeight*.05,
	maxHeight=screenHeight*.70
}

function ChoiceScreen.new(choiceId, ...)
	local self = extend(ChoiceScreen, {options=getTableOrVarArg(...) or {}})
	
	self.choiceStyle = self.choiceStyle or prefs.choiceStyle or createStyle()
	self.selectedChoiceStyle = self.selectedChoiceStyle
		or prefs.selectedChoiceStyle
		or extendStyle(self.choiceStyle, {color=0xFF808080})
	
	self.buttons = {}
	for i,opt in ipairs(self.options) do
		local b = TextButton.new("gui/choice-button", opt or "???")		
		b:setAlpha(0)
		b:setZ(-2000)
		
		local buttonScale = 1
		if b.button:getUnscaledWidth() > screenWidth * .8 then
			buttonScale = (screenWidth * .8) / b.button:getUnscaledWidth()
		end
		b.button:setScale(buttonScale, buttonScale)
		
		if seenLog:isChoiceSelected(choiceId, i) then
			b.text:setDefaultStyle(self.selectedChoiceStyle)
		else
			b.text:setDefaultStyle(self.choiceStyle)
		end
		table.insert(self.buttons, b)
	end
	
	return self
end

function ChoiceScreen:destroy()
	self:cancel()
end

function ChoiceScreen:layout()
	local lineSpacing = screenHeight / 32
	
	local td = textState:getTextDrawable()
	local startY = self.offsetY
	local height = 0
	for pass=1,3 do
		if pass == 2 then
			lineSpacing = lineSpacing / 2
		elseif pass == 3 then
			lineSpacing = lineSpacing / 2
			startY = lineSpacing * 4
		end
	
		height = 0	
		for _,b in ipairs(self.buttons) do
			height = height + b:getHeight() + lineSpacing
		end
		height = height - lineSpacing --Remove redundant spacing after last button
		
		if startY + height <= self.maxHeight then
			break
		end
	end
	
	local y = startY + math.max(0, (self.maxHeight-height) / 2)
	for _,b in ipairs(self.buttons) do
		b:setAlpha(1)		
		b:setPos((screenWidth-b:getWidth())/2, y)
		y = y + b:getHeight() + lineSpacing
	end	
end

function ChoiceScreen:fadeButtons(visible, speed)
	local targetAlpha = 1
	if not visible then
		targetAlpha = 0
	end
	
	local threads = {}
	for _,b in ipairs(self.buttons) do
		table.insert(threads, newThread(fadeTo, b, targetAlpha, speed))
	end
	update1join(threads)
end

function ChoiceScreen:run()
	self.selected = -1
	self:layout()

	self:fadeButtons(false, 1)
	self:fadeButtons(true)

	local focusIndex = 0
	local selected = -1
	local len = #self.options
	while selected < 0 and len > 0 do
		local oldb = self.buttons[focusIndex]
		
		if input:consumeUp() then
			focusIndex = math.max(1, focusIndex - 1)
		end
		if input:consumeDown() then
			focusIndex = math.min(#self.buttons, focusIndex + 1)
		end

		local newb = self.buttons[focusIndex]
		if oldb ~= newb then
			if oldb ~= nil then oldb.button:setKeyboardFocus(false) end
			if newb ~= nil then newb.button:setKeyboardFocus(true) end
		end

		if input:consumeCancel() then
			self:fadeButtons(false)
			self:cancel()
			return --We must return immediately after calling cancel
		end

		for i,b in ipairs(self.buttons) do
			if focusIndex == 0 or i == focusIndex then
				b.button:setColor(1, 1, 1)
			else
				b.button:setColor(.5, .5, .5)
			end
			
			if b:consumePress() then
				self:onButtonPressed(i)
				selected = i - 1
				break
			end
		end

		yield()
	end
	
	self.selected = selected
end

function ChoiceScreen:cancel()
	self.cancelled = true
	destroyValues(self.buttons)
	self.buttons = {}
end

function ChoiceScreen:getOptions()
	return self.options
end

--Zero-based index
function ChoiceScreen:getSelected()
	return self.selected
end

function ChoiceScreen:isCancelled()
	return self.cancelled
end

--Gets called when a button is pressed, changing self.selected happens elsewhere
function ChoiceScreen:onButtonPressed(index)	
	self:fadeButtons(false)
end

--Zero-based index
function ChoiceScreen:setSelected(s)
	if s < 0 or s >= #self.options then
		s = -1
	end
	self.selected = s
end

-- ----------------------------------------------------------------------------
-- ----------------------------------------------------------------------------
-- ----------------------------------------------------------------------------

Screens.save = SaveLoadScreen
Screens.load = SaveLoadScreen
Screens.textLog = TextLogScreen
Screens.choice = ChoiceScreen