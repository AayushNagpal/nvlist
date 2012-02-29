package nl.weeaboo.vn.impl.nvlist;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Arrays;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import nl.weeaboo.common.Dim;
import nl.weeaboo.common.ScaleUtil;
import nl.weeaboo.gl.GLManager;
import nl.weeaboo.gl.shader.GLShader;
import nl.weeaboo.gl.texture.GLGeneratedTexture;
import nl.weeaboo.gl.texture.GLTexRect;
import nl.weeaboo.gl.texture.GLTexture;
import nl.weeaboo.lua2.io.LuaSerializable;
import nl.weeaboo.vn.IImageDrawable;
import nl.weeaboo.vn.IInterpolator;
import nl.weeaboo.vn.INotifier;
import nl.weeaboo.vn.IRenderer;
import nl.weeaboo.vn.ITexture;
import nl.weeaboo.vn.impl.base.BaseBitmapTween;
import nl.weeaboo.vn.impl.base.BaseRenderer;
import nl.weeaboo.vn.impl.base.CustomRenderCommand;
import nl.weeaboo.vn.impl.base.TriangleGrid;
import nl.weeaboo.vn.math.Matrix;

@LuaSerializable
public class BitmapTween extends BaseBitmapTween {

	private static final long serialVersionUID = NVListImpl.serialVersionUID;
	
	private static final String requiredGlslVersion = "1.1";
	
	private final ImageFactory fac;
	
	//--- Initialized in prepare() ---
	private GLShader shader;
	private GLTexRect[] texs;
	private GLGeneratedTexture fadeTex;
	private GLGeneratedTexture remapTex;
	
	public BitmapTween(ImageFactory ifac, INotifier ntf, String fadeFilename, double duration,
			double range, IInterpolator i, boolean fadeTexLerp, boolean fadeTexTile)
	{	
		super(ntf, fadeFilename, duration, range, i, fadeTexLerp, fadeTexTile);
		
		this.fac = ifac;
	}
	
	//Functions
	public static boolean isAvailable(String glslVersion) {
		return requiredGlslVersion.compareTo(glslVersion) <= 0;
	}
	
	@Override
	protected void prepareShader() {
		shader = fac.getGLShader("bitmap-tween");
	}

	@Override
	protected void prepareTextures(ITexture[] itexs) {
		texs = new GLTexRect[itexs.length];
		for (int n = 0; n < itexs.length; n++) {
			if (itexs[n] instanceof TextureAdapter) {
				TextureAdapter ta = (TextureAdapter)itexs[n];
				texs[n] = ta.getTexRect();
			}
		}		
	}

	@Override
	protected ITexture prepareFadeTexture(String filename, boolean scaleSmooth,
			boolean tile, Dim targetSize) throws IOException
	{		
		BufferedImage src = fac.getBufferedImage(filename);			
		if (targetSize != null) {
			//Take the region of the image we want to use
			src = fadeImageSubRect(src, targetSize.w, targetSize.h);
		}
		
		//Get 16-bit grayscale pixels from image
		int[] argb = toGrayScale(src);

		fadeTex = fac.createGLTexture(argb, src.getWidth(), src.getHeight(),
				(scaleSmooth ? GL.GL_LINEAR : GL.GL_NEAREST),
				(scaleSmooth ? GL.GL_LINEAR : GL.GL_NEAREST),
				(tile ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE));
		
		return fac.createTexture(fadeTex, 1, 1);
	}

	protected BufferedImage fadeImageSubRect(BufferedImage img, int targetW, int targetH) {
		final int iw = img.getWidth();
		final int ih = img.getHeight();
		Dim d = ScaleUtil.scaleProp(targetW, targetH, iw, ih);
		return img.getSubimage((iw-d.w)/2, (ih-d.h)/2, d.w, d.h);
	}
	
	protected int[] toGrayScale(BufferedImage img) {
		int iw = img.getWidth();
		int ih = img.getHeight();
		BufferedImage temp = new BufferedImage(iw, ih, BufferedImage.TYPE_USHORT_GRAY);
		Graphics2D g = (Graphics2D)temp.getGraphics();
		g.setComposite(AlphaComposite.Src);
		g.drawImage(img, 0, 0, null);
		g.dispose();

		DataBuffer dataBuffer = temp.getRaster().getDataBuffer();
		
		int data[] = new int[iw * ih];
		for (int n = 0; n < data.length; n++) {
			data[n] = dataBuffer.getElem(n);
		}
		return data;
	}
	
	@Override
	protected ITexture prepareDefaultFadeTexture(int colorARGB) {
		int w = 16, h = 16;
		int[] argb = new int[w * h];
		Arrays.fill(argb, colorARGB);
		
		fadeTex = fac.createGLTexture(argb, w, h, GL.GL_NEAREST, GL.GL_NEAREST, GL.GL_CLAMP_TO_EDGE);
		
		return fac.createTexture(fadeTex, 1, 1);
	}

	@Override
	protected void prepareRemapTexture(int w, int h) {
		remapTex = fac.createGLTexture(null, w, h, GL.GL_NEAREST, GL.GL_NEAREST, GL.GL_CLAMP_TO_EDGE);
	}

	@Override
	protected void updateRemapTex(int[] argb) {
		remapTex.setARGB(IntBuffer.wrap(argb));
	}

	@Override
	protected void draw(BaseRenderer rr, IImageDrawable img, TriangleGrid grid) {
		rr.draw(new RenderCommand(img, texs, fadeTex, remapTex, shader, grid));
	}
	
	//Getters
	
	//Setters
	
	//Inner Classes
	protected static final class RenderCommand extends CustomRenderCommand {

		private final Matrix transform;
		private GLTexRect[] texs;
		private GLTexture fadeTex;
		private GLTexture remapTex;
		private final GLShader shader;
		private final TriangleGrid grid;
		
		public RenderCommand(IImageDrawable id, GLTexRect[] texs, GLTexture fadeTex,
				GLTexture remapTex, GLShader shader, TriangleGrid grid)
		{
			super(id.getZ(), id.isClipEnabled(), id.getBlendMode(), id.getColor(),
					id.getPixelShader(), (byte)0);
			
			this.transform = id.getTransform();
			this.texs = texs.clone();
			this.fadeTex = fadeTex;
			this.remapTex = remapTex;
			this.shader = shader;
			this.grid = grid;
		}

		@Override
		protected void renderGeometry(IRenderer r) {
			Renderer rr = (Renderer)r;			
			GLManager glm = rr.getGLManager();
			GL2 gl2 = GLManager.getGL2(glm.getGL());
						
			GLTexture oldTexture = glm.getTexture();
			glm.setTexture(null);
			
			//Force load textures
			for (int n = 0; n < texs.length; n++) {
				GLTexRect tr = texs[n];
				if (tr != null) {
					texs[n] = tr.forceLoad(glm);
				}
			}
			fadeTex = fadeTex.forceLoad(glm);
			remapTex = remapTex.forceLoad(glm);
			
			//Force load shader
			shader.forceLoad(glm);
			GLShader oldShader = glm.getShader();
			glm.setShader(shader);
			
			//Initialize shader
			shader.setTextureParam(gl2, "src0",  0, texId(texs[0]));
			shader.setTextureParam(gl2, "src1",  1, texId(texs[1]));
			shader.setTextureParam(gl2, "fade",  2, texId(fadeTex));
			shader.setTextureParam(gl2, "remap", 3, texId(remapTex));
			
			//Render geometry
			gl2.glPushMatrix();
			gl2.glMultMatrixf(transform.toGLMatrix(), 0);
			rr.renderTriangleGrid(grid);
			gl2.glPopMatrix();

			//Disable shader
			glm.setShader(oldShader);
						
			//Restore previous texture
			glm.setTexture(oldTexture);
		}
		
		private static final int texId(GLTexRect tr) {
			return texId(tr != null ? tr.getTexture() : null);
		}
		private static final int texId(GLTexture tex) {
			return (tex != null ? tex.getTexId() : 0);
		}
		
	}
}
