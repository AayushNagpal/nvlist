-------------------------------------------------------------------------------
-- gui.lua
-------------------------------------------------------------------------------
-- User-interface related classes and functions
-------------------------------------------------------------------------------

module("vn.gui", package.seeall)

-- ----------------------------------------------------------------------------
--  Classes
-- ----------------------------------------------------------------------------


-- ----------------------------------------------------------------------------
--  Functions
-- ----------------------------------------------------------------------------

---Returns the correct X offset for a component with width <code>inner</code>
-- given the width <code>outer</code> of its desired container.
-- @param outer Width of the container
-- @param inner Width of the component inside the container
-- @param anchor Alignment of the component within the container. These
--        correspond to numpad directions (4=left, 5=center, 6=right).
-- @return The correct X offset for the inner component
function alignAnchorX(outer, inner, anchor)
	if anchor == 2 or anchor == 5 or anchor == 8 then
		return (outer-inner) / 2
	elseif anchor == 3 or anchor == 6 or anchor == 9 then
		return (outer-inner)
	end
	return 0		
end

---Returns the correct Y offset for a component with height <code>inner</code>
-- given the height <code>outer</code> of its desired container.
-- @param outer Height of the container
-- @param inner Height of the component inside the container
-- @param anchor Alignment of the component within the container. These
--        correspond to numpad directions (8=top, 5=center, 2=bottom).
-- @return The correct Y offset for the inner component
function alignAnchorY(outer, inner, anchor)
	if anchor >= 4 and anchor <= 6 then
		return (outer-inner) / 2
	elseif anchor >= 1 and anchor <= 3 then
		return (outer-inner)
	end
	return 0		
end

---Sets the bounds of <code>c</code> to <codE>lc</code>.
-- @param lc The object containing the source bounds.
-- @param c The destination object to update the bounds of.
function transferBounds(lc, c)
	return c:setBounds(unpack(getProperty(lc, "bounds")))
end

---Creates an implementation of ILayoutComponent for use in ILayout objects
-- like the FlowLayout and GridLayout.
-- @param x The top-left x coordinate for the component.
-- @param y The top-left y coordinate for the component.
-- @param w The width of the component.
-- @param h The height of the component.
function createLayoutComponent(x, y, w, h)
	return GUI.createLayoutComponent(x, y, w, h)
end

---Creates a layout component from the given userdata or table. The initial
-- bounds for the component are the values returned from a call
-- <code>c:getBounds()</code>.
-- @param c The Lua userdata or table to initialize the layout component with.
function toLayoutComponent(c)
	return createLayoutComponent(unpack(getProperty(c, "bounds")))
end

local function createLayout(classDef, overrides)
	local ly = classDef.new()
	setProperties(ly, overrides)
	return ly
end

---Creates a new FlowLayout object.
-- @param overrides An optional table containing default values for the new
--        layout's properties.
function createFlowLayout(overrides)
	return createLayout(FlowLayout, overrides)
end

---Creates a new GridLayout object.
-- @param overrides An optional table containing default values for the new
--        layout's properties.
function createGridLayout(overrides)
	return createLayout(GridLayout, overrides)
end

---Creates a layout object and uses it to layout the given components. This
-- function can layout any object which implements getBounds/setBounds
-- functions, not just drawables.
-- @param layout The layout object, or a table containing a <code>new</code>
--        function that can be used to create a new layout object like
--        <code>FlowLayout</code> and <code>GridLayout</code>.
-- @param x The top-left x coordinate of the layout bounds.
-- @param y The top-left y coordinate of the layout bounds.
-- @param w The width of the layout bounds.
-- @param h The height of the layout bounds.
-- @param overrides An optional table containing property overrides for the
--        layout.
-- @param components The things to layout, must implement getBounds/setBounds
--        functions.
function doLayout(layout, x, y, w, h, overrides, components)
	if type(layout) == "table" then
		layout = createLayout(layout)
	end
	setProperties(layout, overrides)
	
	local lcs = {}
	local i = 1
	for _,c in pairs(components) do		
		lcs[i] = toLayoutComponent(c)		
		i = i + 1		
	end
	
	layout:layout(x or 0, y or 0, w or 0, h or 0, lcs)
	
	local i = 1
	for _,c in pairs(components) do
		local lc = lcs[i]
		transferBounds(lc, c)
		i = i + 1
	end
end

---Creates a new panel
-- @param w Optional initial width for the panel.
-- @param h Optional initial height for the panel.
-- @return The newly created panel.
function createPanel(w, h)
	w = w or 0
	h = h or 0

	local panel = GUI.createPanel(getImageLayer())	
	panel:setSize(w, h)
	return panel
end

---Creates a new viewport
-- @param w Optional initial width for the viewport.
-- @param h Optional initial height for the viewport.
-- @return The newly created viewport.
function createViewport(w, h)
	w = w or 0
	h = h or 0

	local viewport = GUI.createViewport(getImageLayer())	
	viewport:setSize(w, h)	
	return viewport
end
