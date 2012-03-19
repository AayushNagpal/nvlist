package nl.weeaboo.vn.impl.nvlist;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import nl.weeaboo.filemanager.FileManager;
import nl.weeaboo.game.input.IKeyConfig;
import nl.weeaboo.game.input.Keys;
import nl.weeaboo.lua2.LuaException;
import nl.weeaboo.lua2.LuaRunState;
import nl.weeaboo.lua2.LuaUtil;
import nl.weeaboo.lua2.io.LuaSerializable;
import nl.weeaboo.vn.IAnalytics;
import nl.weeaboo.vn.IImageState;
import nl.weeaboo.vn.IInput;
import nl.weeaboo.vn.INovelConfig;
import nl.weeaboo.vn.IPersistentStorage;
import nl.weeaboo.vn.ISeenLog;
import nl.weeaboo.vn.ISoundState;
import nl.weeaboo.vn.IStorage;
import nl.weeaboo.vn.ITextState;
import nl.weeaboo.vn.ITimer;
import nl.weeaboo.vn.IVideoState;
import nl.weeaboo.vn.impl.lua.AbstractKeyCodeMetaFunction;
import nl.weeaboo.vn.impl.lua.LuaMediaPreloader;
import nl.weeaboo.vn.impl.lua.LuaNovel;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class Novel extends LuaNovel {

	private transient FileManager fm;
	private transient IKeyConfig keyConfig;
	
	// !!WARNING!! Do not add properties without adding code for saving/loading
	
	public Novel(INovelConfig nc, ImageFactory imgfac, IImageState is, ImageFxLib fxlib,
			SoundFactory sndfac, ISoundState ss, VideoFactory vf, IVideoState vs,
			ITextState ts, NovelNotifier n, IInput in, SystemLib syslib, SaveHandler sh,
			ScriptLib scrlib, TweenLib tl, IPersistentStorage sysVars, IStorage globals,
			ISeenLog seenLog, IAnalytics analytics, ITimer tmr,
			FileManager fm, IKeyConfig kc)
	{
		super(nc, imgfac, is, fxlib, sndfac, ss, vf, vs, ts, n, in, syslib, sh, scrlib, tl,
				sysVars, globals, seenLog, analytics, tmr);
		
		this.fm = fm;
		this.keyConfig = kc;
	}
	
	//Functions	
	@Override
	protected void initPreloader(LuaMediaPreloader preloader) {
		preloader.clear();
		try {
			try {
				InputStream in = new BufferedInputStream(fm.getInputStream("preloader-default.bin"), 4096);
				try {
					preloader.load(in);
				} finally {
					in.close();
				}
			} catch (FileNotFoundException fnfe) {
				//Ignore
			}
			
			try {
				InputStream in = new BufferedInputStream(fm.getInputStream("preloader.bin"), 4096);
				try {
					preloader.load(in);
				} finally {
					in.close();
				}
			} catch (FileNotFoundException fnfe) {
				//Ignore
			}
		} catch (IOException ioe) {
			getNotifier().w("Error initializing preloader", ioe);
		}
	}
	
	@Override
	public void initLuaRunState() {
		super.initLuaRunState();
		
		LuaRunState lrs = getLuaRunState();
		LuaValue globals = lrs.getGlobalEnvironment();
		try {
			//Register types
			LuaUtil.registerClass(globals, FreeRotationGS.class);

			//Register libraries
			GLSLPS.install(globals, (ImageFactory)getImageFactory(), getNotifier());
			registerVNDSLib();
		} catch (LuaException e) {
			onScriptError(e);
		}		
	}

	@Override
	protected void onScriptError(Exception e) {		
		Throwable t = e;
		String msg = t.getMessage();
		
		if (t instanceof LuaException && t.getCause() != null) {
			t = t.getCause();
		}

		StringBuilder message = new StringBuilder("Script Error");
		StackTraceElement[] stack = t.getStackTrace();
		if (stack != null) {
			StackTraceElement bestEntry = null;
			for (StackTraceElement entry : stack) {
				if (entry.getClassName().equals("Lua")) {
					bestEntry = entry;
					if (!bestEntry.getFileName().startsWith("?")) {
						break; //Quit when a real filename is found
					}
				}
			}
			
			if (bestEntry != null) {
				message.append(" at ");
				message.append(bestEntry.getFileName()+":"+bestEntry.getLineNumber());
			}
		}		
		message.append(" :: ");
		message.append(msg);
		while (t instanceof LuaError && t.getCause() != null) {
			message.append(" :: ");
			message.append(t.getMessage());
			t = t.getCause();
		}
		
		getNotifier().e(message.toString(), t);		
		
		setWait(60);
	}

	@Override
	protected void addKeyCodeConstants(LuaTable table) throws LuaException {
		Keys keys = keyConfig.getKeys();
		//for (Entry<String, Integer> entry : keys) {
		//	table.put(valueOf(entry.getKey()), valueOf(entry.getValue()));
		//}
		addKeyCodeConstants(table, new KeyCodeMetaFunction(keys, table));
	}
	
	//Getters
	
	//Setters
	
	//Inner classes
	@LuaSerializable
	private static class KeyCodeMetaFunction extends AbstractKeyCodeMetaFunction {

		private static final long serialVersionUID = 1L;
		
		private static Keys keys;
		
		public KeyCodeMetaFunction(Keys k, LuaTable t) {
			super(t);
			
			keys = k;
		}

		@Override
		protected LuaValue getKeyCode(String name) {
			int retval = keys.get(name);
			return (retval == 0 ? NIL : valueOf(retval));
		}
		
	}
	
}
