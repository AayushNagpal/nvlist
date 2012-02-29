package nl.weeaboo.vn.impl.nvlist;

import nl.weeaboo.lua2.io.LuaSerializable;
import nl.weeaboo.vn.BlendMode;
import nl.weeaboo.vn.IGeometryShader;
import nl.weeaboo.vn.IImageDrawable;
import nl.weeaboo.vn.IPixelShader;
import nl.weeaboo.vn.IRenderer;
import nl.weeaboo.vn.ITexture;
import nl.weeaboo.vn.impl.base.BaseRenderer;
import nl.weeaboo.vn.impl.base.BaseShader;
import nl.weeaboo.vn.impl.base.LayoutUtil;
import nl.weeaboo.vn.math.Matrix;
import nl.weeaboo.vn.math.Vec2;

@LuaSerializable
public class FreeRotationGS extends BaseShader implements IGeometryShader {

	private static final long serialVersionUID = NVListImpl.serialVersionUID;

	private double rotX, rotY, rotZ;
	
	public FreeRotationGS() {
	}

	//Functions	
	@Override
	public void draw(IRenderer r, IImageDrawable image, ITexture tex,
			double alignX, double alignY, IPixelShader ps)
	{
		BaseRenderer rr = (BaseRenderer)r;
		
		short z = image.getZ();
		boolean clip = image.isClipEnabled();
		BlendMode blend = image.getBlendMode();
		int argb = image.getColor();
		Matrix trans = image.getTransform();
		double w = image.getUnscaledWidth();
		double h = image.getUnscaledHeight();
		
		Vec2 offset = LayoutUtil.getImageOffset(tex, alignX, alignY);
		rr.draw(new RotatedQuadCommand(z, clip, blend, argb, tex,
					trans, offset.x, offset.y, w, h, ps, rotX, rotY, rotZ));
	}
	
	//Getters
	
	//Setters
	public void setRotation(double rx, double ry, double rz) {
		if (rotX != rx || rotY != ry || rotZ != rz) {
			rotX = rx;
			rotY = ry;
			rotZ = rz;
			markChanged();
		}
	}
	
}
