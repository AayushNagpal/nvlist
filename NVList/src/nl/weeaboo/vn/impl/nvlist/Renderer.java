package nl.weeaboo.vn.impl.nvlist;

import static javax.media.opengl.GL.GL_FLOAT;
import static javax.media.opengl.GL.GL_TEXTURE0;
import static javax.media.opengl.GL.GL_TRIANGLE_STRIP;
import static javax.media.opengl.fixedfunc.GLPointerFunc.GL_TEXTURE_COORD_ARRAY;
import static javax.media.opengl.fixedfunc.GLPointerFunc.GL_VERTEX_ARRAY;

import javax.media.opengl.GL2ES1;

import nl.weeaboo.common.Rect;
import nl.weeaboo.common.Rect2D;
import nl.weeaboo.gl.GLBlendMode;
import nl.weeaboo.gl.GLManager;
import nl.weeaboo.gl.SpriteBatch;
import nl.weeaboo.gl.capture.GLScreenshot;
import nl.weeaboo.gl.text.ParagraphRenderer;
import nl.weeaboo.gl.texture.GLTexture;
import nl.weeaboo.io.BufferUtil;
import nl.weeaboo.textlayout.TextLayout;
import nl.weeaboo.vn.BlendMode;
import nl.weeaboo.vn.IPixelShader;
import nl.weeaboo.vn.IScreenshot;
import nl.weeaboo.vn.ITexture;
import nl.weeaboo.vn.RenderCommand;
import nl.weeaboo.vn.impl.base.BaseRenderCommand;
import nl.weeaboo.vn.impl.base.BaseRenderer;
import nl.weeaboo.vn.impl.base.CustomRenderCommand;
import nl.weeaboo.vn.impl.base.RenderStats;
import nl.weeaboo.vn.impl.base.TriangleGrid;
import nl.weeaboo.vn.math.Matrix;

import com.jogamp.common.nio.Buffers;

public class Renderer extends BaseRenderer {

	private final GLManager glm;
	private final ParagraphRenderer pr;
	private final FadeQuadRenderer fadeQuadRenderer;	
	private final BlendQuadRenderer blendQuadRenderer;	
	
	//--- Properties only valid between renderBegin() and renderEnd() beneath this line ---
	private boolean rendering;
	
	private int buffered;
	private TextureAdapter quadTexture;
	private SpriteBatch quadBatch;
	private float[] tempFloat = new float[8]; //Temporary var
	//-------------------------------------------------------------------------------------
	
	public Renderer(GLManager glm, ParagraphRenderer pr, int w, int h,
			int rx, int ry, int rw, int rh, int sw, int sh, RenderStats stats)
	{
		super(w, h, rx, ry, rw, rh, sw, sh, stats);
		
		this.glm = glm;
		this.pr = pr;
		this.fadeQuadRenderer = new FadeQuadRenderer(this);
		this.blendQuadRenderer = new BlendQuadRenderer(this);
		
		quadBatch = new SpriteBatch(1024);
	}
	
	//Functions
	public void drawText(short z, boolean clipEnabled, BlendMode blendMode, int argb,
			TextLayout textLayout, int lineStart, int lineEnd, double visibleChars,
			double x, double y, IPixelShader ps)
	{		
		draw(new RenderTextCommand(z, clipEnabled, blendMode, argb, textLayout,
				lineStart, lineEnd, visibleChars, x, y, ps));
	}
			
	@Override
	protected void renderBegin(Rect2D bounds, Rect screenClip, Rect layerClip) {
		rendering = true;
		
		GL2ES1 gl = glm.getGL();
		
		gl.glPushMatrix();
		if (bounds != null) {
			glm.translate(bounds.x, bounds.y);
		}
		
		gl.glEnable(GL2ES1.GL_SCISSOR_TEST);
		gl.glScissor(layerClip.x, layerClip.y, layerClip.w, layerClip.h);
		
		glm.pushBlendMode();
		glm.setBlendMode(GLBlendMode.DEFAULT);
		
		glm.pushColor();
		glm.setColor(0xFFFFFFFF);	
		
		quadBatch.init(glm);
		
		buffered = 0;
		quadTexture = null;
	}
	
	@Override
	protected void renderEnd() {
		flushQuadBatch();
		
		GL2ES1 gl = glm.getGL();
		glm.setTexture(null, true);
		glm.popColor();
		glm.popBlendMode();
		gl.glPopMatrix();
		gl.glDisable(GL2ES1.GL_SCISSOR_TEST);
		
		rendering = false;
		quadTexture = null;
	}
	
	@Override
	protected void preRenderCommand(BaseRenderCommand cmd) {
		super.preRenderCommand(cmd);
	}	
	
	@Override
	protected void postRenderCommand(BaseRenderCommand cmd) {		
		super.postRenderCommand(cmd);
	}	
		
	@Override
	protected void renderSetClip(boolean c) {
		flushQuadBatch();

		GL2ES1 gl = glm.getGL();
		if (c) {
			gl.glEnable(GL2ES1.GL_SCISSOR_TEST);
		} else {
			gl.glDisable(GL2ES1.GL_SCISSOR_TEST);
		}
	}
	
	@Override
	protected void renderSetColor(int argb) {
		glm.setColor(argb);
	}

	@Override
	protected void renderSetBlendMode(BlendMode bm) {
		flushQuadBatch();
		
		switch (bm) {
		case DEFAULT: glm.setBlendMode(GLBlendMode.DEFAULT); break;
		case ADD:     glm.setBlendMode(GLBlendMode.ADD); break;
		case OPAQUE:  glm.setBlendMode(null); break;
		}
	}	
	
	protected void renderSetTexture(ITexture tex) {
		TextureAdapter ta = (TextureAdapter)tex;		
		if (quadTexture != tex && (quadTexture == null || quadTexture.getTexId() != ta.getTexId())) {
			flushQuadBatch();
		}

		quadTexture = ta;		
		if (ta != null) {
			ta.forceLoad(glm);
			glm.setTexture(ta.getTexture());
		} else {
			glm.setTexture(null);
		}		
	}
	
	@Override
	public void renderQuad(ITexture itex, Matrix t,
			double x, double y, double w, double h,
			IPixelShader ps, double u, double v, double uw, double vh)
	{
		renderSetTexture(itex);
		if (itex != null) {
			TextureAdapter ta = (TextureAdapter)itex;
			if (ta.getTexId() != 0) {
				Rect2D uv = ta.getUV();
				u  = uv.x + u * uv.w;
				v  = uv.y + v * uv.h;
				uw = uv.w * uw;
				vh = uv.h * vh;
			}
		}

		if (ps != null) {
			flushQuadBatch();			

			ps.preDraw(this);
			renderQuad(false, t, x, y, w, h, u, v, uw, vh);
			ps.postDraw(this);
		} else {
			renderQuad(true, t, x, y, w, h, u, v, uw, vh);			
		}
	}
	
	private void renderQuad(boolean allowBuffer, Matrix t, double x, double y, double w, double h,
			double u, double v, double uw, double vh)
	{
		GL2ES1 gl = glm.getGL();		
		if (t.hasShear()) {
			if (allowBuffer) {
				quadBatch.setColor(glm.getColor());

				tempFloat[0] = tempFloat[6] = (float)(x  );
				tempFloat[2] = tempFloat[4] = (float)(x+w);
				tempFloat[1] = tempFloat[3] = (float)(y  );
				tempFloat[5] = tempFloat[7] = (float)(y+h);
				t.transform(tempFloat, 0, 8);
				quadBatch.draw(tempFloat, (float)u, (float)v, (float)uw, (float)vh);

				buffered++;				
				if (quadBatch.getRemaining() <= 0) {
					flushQuadBatch();
				}
			} else {
				gl.glPushMatrix();		
				gl.glMultMatrixf(t.toGLMatrix(), 0);
				glm.fillRect(x, y, w, h, u, v, uw, vh);
				gl.glPopMatrix();
			}
		} else {
			double sx = t.getScaleX();
			double sy = t.getScaleY();
			x = x * sx + t.getTranslationX();
			y = y * sy + t.getTranslationY();
			w = w * sx;
			h = h * sy;
			
			if (allowBuffer) {
				quadBatch.setColor(glm.getColor());
				quadBatch.draw((float)x, (float)y, (float)w, (float)h, (float)u, (float)v, (float)uw, (float)vh);

				buffered++;				
				if (quadBatch.getRemaining() <= 0) {
					flushQuadBatch();
				}
			} else {			
				glm.fillRect(x, y, w, h, u, v, uw, vh);
			}
			
			//System.out.printf("%.2f, %.2f, %.2f, %.2f\n", x, y, w, h);
		}		
	}
	
	void renderText(GLManager glm, TextLayout layout, double x, double y,
			int lineStart, int lineEnd, double visibleChars, IPixelShader ps)
	{
		flushQuadBatch();

		if (ps != null) ps.preDraw(this);
		
		//GL2ES1 gl = glm.getGL();		
		//gl.glPushMatrix();
		glm.translate(x, y);
		
		pr.setLineOffset(lineStart);
		pr.setVisibleLines(lineEnd - lineStart);
		pr.setVisibleChars(visibleChars);
		pr.drawLayout(glm, layout);

		glm.translate(-x, -y);
		//gl.glPopMatrix();
		
		if (ps != null) ps.postDraw(this);		
	}
	
	@Override
	public void renderScreenshot(IScreenshot out, Rect glScreenRect) {
		flushQuadBatch();
		
		Screenshot ss = (Screenshot)out;
		
		GLScreenshot gss = new GLScreenshot();
		gss.set(glm, glScreenRect);
		
		int[] argb = BufferUtil.toArray(gss.getARGB());
		ss.set(argb, gss.getWidth(), gss.getHeight(), getScreenWidth(), getScreenHeight());
	}
	
	@Override
	public void renderBlendQuad(ITexture tex0, double alignX0, double alignY0, ITexture tex1, double alignX1,
			double alignY1, double frac, Matrix transform, IPixelShader ps)
	{
		flushQuadBatch();
		
		blendQuadRenderer.renderBlendQuad(tex0, alignX0, alignY0, tex1, alignX1, alignY1, frac, transform, ps);
	}

	@Override
	public void renderFadeQuad(ITexture tex, Matrix transform, int color0, int color1,
			double x, double y, double w, double h, IPixelShader ps,
			int dir, boolean fadeIn, double span, double frac)
	{
		flushQuadBatch();

		fadeQuadRenderer.renderFadeQuad(tex, transform, color0, color1, x, y, w, h, ps, dir, fadeIn, span, frac);
	}
	
	@Override
	public void renderTriangleGrid(TriangleGrid grid) {
		flushQuadBatch();

		GL2ES1 gl = glm.getGL();
		gl.glEnableClientState(GL_VERTEX_ARRAY);
		for (int n = 0; n < grid.getTextures(); n++) {
			gl.glClientActiveTexture(GL_TEXTURE0 + n);
			gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
		}
		for (int row = 0; row < grid.getRows(); row++) {
			gl.glVertexPointer(2, GL_FLOAT, 0, Buffers.copyFloatBuffer(grid.getPos(row)));
			for (int n = 0; n < grid.getTextures(); n++) {
				gl.glClientActiveTexture(GL_TEXTURE0 + n);
			    gl.glTexCoordPointer(2, GL_FLOAT, 0, Buffers.copyFloatBuffer(grid.getTex(n, row)));
			}
		    gl.glDrawArrays(GL_TRIANGLE_STRIP, 0, grid.getVertexCount(row));
		}
		for (int n = grid.getTextures()-1; n >= 0; n--) {
			gl.glClientActiveTexture(GL_TEXTURE0 + n);
			gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
		}
	    gl.glDisableClientState(GL_VERTEX_ARRAY);		
	}
	
	@Override
	protected void renderCustom(CustomRenderCommand cmd) {		
		flushQuadBatch();
		
		super.renderCustom(cmd);
	}
	
	@Override
	protected boolean renderUnknownCommand(RenderCommand cmd) {
		flushQuadBatch();

		if (cmd.id == RenderTextCommand.id) {
			RenderTextCommand rtc = (RenderTextCommand)cmd;
			renderText(glm, rtc.textLayout, rtc.x, rtc.y - rtc.textLayout.getLineTop(rtc.lineStart),
					rtc.lineStart, rtc.lineEnd, rtc.visibleChars, rtc.ps);
			return true;
		}
		return false;
	}
	
	private void flushQuadBatch() {
		if (buffered > 0) {
			GLTexture qtex = (quadTexture != null ? ((TextureAdapter)quadTexture).getTexture() : null);
			GLTexture cur = glm.getTexture();
			if (qtex != cur) {
				glm.setTexture(qtex);
				quadBatch.flush(glm.getGL());
				glm.setTexture(cur);			
			} else {
				quadBatch.flush(glm.getGL());
			}
			
			if (renderStats != null) {
				renderStats.onRenderQuadBatch(buffered);
			}
			buffered = 0;
		}		
		quadTexture = null;
	}
	
	//Getters	
	public GLManager getGLManager() {
		if (!rendering) return null;
		return glm;
	}

	//Setters
	
}
