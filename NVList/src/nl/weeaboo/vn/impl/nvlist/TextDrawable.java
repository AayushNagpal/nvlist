package nl.weeaboo.vn.impl.nvlist;

import nl.weeaboo.gl.text.GLTextRendererStore;
import nl.weeaboo.gl.text.ParagraphRenderer;
import nl.weeaboo.lua.io.LuaSerializable;
import nl.weeaboo.styledtext.MutableTextStyle;
import nl.weeaboo.styledtext.StyledText;
import nl.weeaboo.textlayout.TextLayout;
import nl.weeaboo.vn.IRenderer;
import nl.weeaboo.vn.impl.base.BaseTextDrawable;
import nl.weeaboo.vn.math.Matrix;

@LuaSerializable
public class TextDrawable extends BaseTextDrawable {

	private static final long serialVersionUID = NVListImpl.serialVersionUID;
	
	private final GLTextRendererStore trStore;

	private transient TextLayout textLayout;
	private transient double texScale;
	
	public TextDrawable(GLTextRendererStore trStore) {
		super(new StyledText(""));
		
		this.trStore = trStore;
	}
	
	//Functions	
	@Override
	public void draw(IRenderer r) {
		Renderer rr = (Renderer)r;
		
		setTexScale(1.0 / r.getScale());
		
		int bgColor = getBackgroundColor();
		int bgAlpha = ((bgColor>>24)&0xFF);
		if (bgAlpha > 0) {
			if (getAlpha() < 1) {
				bgAlpha = Math.max(0, Math.min(255, (int)Math.round(bgAlpha * getAlpha())));
			}
			if (bgAlpha > 0) {
				int c = (bgAlpha<<24)|(bgColor&0xFFFFFF);
				rr.drawQuad((short)(getZ()+1), isClipEnabled(), getBlendMode(), c, null,
						Matrix.identityMatrix(), getX(), getY(), getWidth(), getHeight(),
						getPixelShader());
			}
		}
		
		double u = getLayoutWidth() / (double)getTextureWidth();
		double v = getLayoutHeight(getStartLine(), getEndLine()) / (double)getTextureHeight();
		u = Math.max(0, Math.min(1.0, u));
		v = Math.max(0, Math.min(1.0, v));
		
		double w = getInnerWidth();
		double h = getInnerHeight();
		double tw = getTextWidth();
		double th = getTextHeight();
		
		double tx = 0;
		double ty = 0;
		
		int anchor = getAnchor();
		if (anchor == 2 || anchor == 5 || anchor == 8) {
			tx += (w-tw)/2;
		} else if (anchor == 3 || anchor == 6 || anchor == 9) {
			tx += (w-tw);
		}
		if (anchor >= 4 && anchor <= 6) {
			ty += (h-th)/2;
		} else if (anchor >= 1 && anchor <= 3) {
			ty += (h-th);
		}
				
		double pad = getPadding();
		rr.drawText(getZ(), isClipEnabled(), getBlendMode(), getColor(),
				getLayout(), getStartLine(), getEndLine(), getVisibleChars(),
				getX() + pad + tx, getY() + pad + ty, getPixelShader());

		setTexDirty(false);
	}

	@Override
	protected void onSizeChanged() {
		textLayout = null;
	}
	
	@Override
	protected void onTextChanged() {
		textLayout = null;
	}
	
	//Getters
	protected TextLayout getLayout() {
		if (textLayout == null) {
			double scale = getTexScale();
			if (scale <= 0) {
				scale = 1.0;
			}
			
			ParagraphRenderer pr = trStore.createParagraphRenderer();
			MutableTextStyle mts = pr.getDefaultStyle().extend(getDefaultStyle()).mutableCopy();
			mts.setFontSize(mts.getFontSize(12) / scale);
			pr.setDefaultStyle(mts.immutableCopy());
			textLayout = pr.getLayout(getText(), getInnerWidth());
		}
		return textLayout;
	}
	
	@Override
	public double getTextWidth() {
		return getTexScale() * getLayoutWidth();
	}

	@Override
	public double getTextHeight(int start, int end) {
		return getTexScale() * getLayoutHeight(start, end);
	}
	
	protected double getTexScale() {
		return 1.0;
	}
	
	@Override
	public int getLayoutWidth() {
		TextLayout layout = getLayout();
		return (int)Math.ceil(layout.getWidth());
	}
		
	@Override
	public int getLayoutHeight(int start, int end) {
		TextLayout layout = getLayout();
		return (int)Math.ceil(layout.getHeight(start, end));
	}
	
	@Override
	public int getEndLine() {
		TextLayout layout = getLayout();
		int startLine = Math.max(0, Math.min(layout.getNumLines(), getStartLine()));
		int iheight = (int)Math.round(getInnerHeight() / getTexScale());
		if (iheight <= 0) {
			return layout.getNumLines();
		}
		
		double startTop = layout.getLineTop(startLine);
		
		int endLine = startLine;
		while (endLine < layout.getNumLines()
				&& layout.getLineBottom(endLine) - startTop <= iheight)
		{
			endLine++;
		}
		return endLine; 
	}

	@Override
	public int getLineCount() {
		TextLayout layout = getLayout();
		return layout.getNumLines();
	}

	@Override
	public int getCharOffset(int line) {
		TextLayout layout = getLayout();
		return layout.getCharOffset(Math.max(0, Math.min(layout.getNumLines(), line)));
	}
	
	@Override
	protected double getCursorX() {
		TextLayout layout = getLayout();
		int el = getEndLine();
		if (el <= 0 || el > layout.getNumLines()) {
			return 0;
		} else {
			return layout.getLine(el-1).getWidth();
		}
	}

	@Override
	protected double getCursorY() {
		TextLayout layout = getLayout();
		int el = getEndLine();
		if (el <= 0) {
			return 0;
		} else {
			return layout.getLineTop(el-1);
		}
	}
	
	//Setters
	protected void setTexScale(double ts) {
		if (texScale != ts) {
			textLayout = null;			
			texScale = ts;
		}
	}
	
}
