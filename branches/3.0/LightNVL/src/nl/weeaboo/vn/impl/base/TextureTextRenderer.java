package nl.weeaboo.vn.impl.base;

import nl.weeaboo.lua2.io.LuaSerializable;
import nl.weeaboo.vn.BlendMode;
import nl.weeaboo.vn.IDrawBuffer;
import nl.weeaboo.vn.IDrawable;
import nl.weeaboo.vn.IImageDrawable;
import nl.weeaboo.vn.ITexture;
import nl.weeaboo.vn.math.Matrix;

@LuaSerializable
public abstract class TextureTextRenderer<L> extends AbstractTextRenderer<L> {

	private static final long serialVersionUID = BaseImpl.serialVersionUID;

	private transient ITexture texture;
	private transient int textureW, textureH;
	private transient boolean texContentDirty;
	private boolean cursorSizeDirty;
	
	public TextureTextRenderer() {
		super();
	}
	
	//Functions
	@Override
	public void destroy() {
		super.destroy();
		
		if (texture != null) {
			destroyTexture(texture);
		}
		texture = null;
	}
	
	protected abstract void destroyTexture(ITexture texture);
	
	@Override
	public boolean update() {
		if (super.update()) {
			markChanged();
		}
		
		validateCursorSize();
		return consumeChanged();
	}
	
	@Override
	public void draw(IDrawBuffer buf, short z, boolean clipEnabled, BlendMode blendMode, int argb,
			double dx, double dy)
	{		
		validateCursorSize();
		validateTexture();
		
		double w = 0;
		double h = 0;
		double uw = 1;
		double vh = 1;
		if (texture != null) {
			w = getTextWidth();
			uw = getLayoutWidth() / texture.getWidth();
			h = getTextHeight();
			vh = getLayoutHeight() / texture.getHeight();
		}
		buf.drawQuad(z, clipEnabled, blendMode, argb, texture, Matrix.identityMatrix(),
				Math.round(dx), Math.round(dy), w, h, 0, 0, uw, vh, null);
	}
	
	protected void validateCursorSize() {
		if (cursorSizeDirty) {
			cursorSizeDirty = false;
			
			IDrawable cursor = getCursor();
			if (cursor instanceof IImageDrawable) {
				//HACK: Change the cursor size automatically based on the text size
				IImageDrawable id = (IImageDrawable)cursor;
				int cl = getStartLine(); //Use first line instead of cursor line to prevent size jitter (constant relayouting)
				double scale = getTextHeight(cl, cl+1) / id.getUnscaledHeight();
				if (id.getScaleX() != scale || id.getScaleY() != scale) {
					id.setScale(scale);
					invalidateLayout();
				}
			}
		}
	}
	
	protected void invalidateCursorSize() {
		cursorSizeDirty = true;
	}
		
	protected void invalidateTexture() {
		if (texture != null) {
			destroyTexture(texture);
		}
		texture = null;
		markChanged();
	}
	
	protected void invalidateTextureContents() {
		texContentDirty = true;
		invalidateCursorSize();
		markChanged();
	}

	@Override
	protected void invalidateLayout() {
		super.invalidateLayout();				
		invalidateTextureContents();
	}

	@Override
	protected void onVisibleTextChanged() {
		super.onVisibleTextChanged();
		invalidateTextureContents();
	}
	
	@Override
	protected void onDisplayScaleChanged() {
		super.onDisplayScaleChanged();
		invalidateLayout();
	}
	
	/**
	 * Creates a new texture with pixel dimensions <code>(w, h)</code>. 
	 */
	protected abstract ITexture createTexture(int w, int h, double scaleX, double scaleY);
	
	/**
	 * Renders the text layout <code>layout</code> to <code>texture</code>. 
	 */
	protected abstract void renderLayoutToTexture(L layout, ITexture texture);
	
	protected void validateTexture() {
		int lw = Math.max(1, (int)Math.ceil(getLayoutWidth()));
		int lh = Math.max(1, (int)Math.ceil(getLayoutHeight()));
		boolean textureOK = texture != null && textureW >= lw && textureH >= lh;
				
		if (!textureOK) {
			if (texture != null) {
				destroyTexture(texture);
			}
			
			textureW = lw;
			textureH = lh;
			texture = createTexture(textureW, textureH, 1, 1);
			invalidateTextureContents();
		}
		
		if (texContentDirty) {
			texContentDirty = false; //Set flag to false here, so we don't keep re-rendering in case of an error.
			renderLayoutToTexture(getLayout(), texture);
		}		
	}
	
	//Getters
	protected ITexture getTexture() {
		validateTexture();
		return texture;
	}
		
	@Override
	protected int getLayoutMaxWidth() {
		IDrawable cursor = getCursor();
		return Math.max(0, (int)Math.ceil((getMaxWidth() - (cursor != null ? cursor.getWidth() : 0)) * getDisplayScale()));
	}
	
	@Override
	protected int getLayoutMaxHeight() {
		return Math.max(0, (int)Math.ceil(getMaxHeight() * getDisplayScale()));
	}
	
	@Override
	public double getTextWidth(int startLine, int endLine) {
		return getLayoutWidth(startLine, endLine) / getDisplayScale();
	}
	
	@Override
	public double getTextHeight(int startLine, int endLine) {
		return getLayoutHeight(startLine, endLine) / getDisplayScale();
	}
	
	//Setters	
	@Override
	public void setCursor(IDrawable c) {
		if (getCursor() != c) {
			super.setCursor(c);
			invalidateCursorSize();
		}
	}
	
}