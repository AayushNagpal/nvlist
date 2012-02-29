package nl.weeaboo.nvlist;

import static nl.weeaboo.game.BaseGameConfig.HEIGHT;
import static nl.weeaboo.game.BaseGameConfig.TITLE;
import static nl.weeaboo.game.BaseGameConfig.WIDTH;
import static nl.weeaboo.vn.NovelPrefs.AUTO_READ;
import static nl.weeaboo.vn.NovelPrefs.AUTO_READ_WAIT;
import static nl.weeaboo.vn.NovelPrefs.EFFECT_SPEED;
import static nl.weeaboo.vn.NovelPrefs.ENGINE_MIN_VERSION;
import static nl.weeaboo.vn.NovelPrefs.PRELOADER_LOOK_AHEAD;
import static nl.weeaboo.vn.NovelPrefs.PRELOADER_MAX_PER_LINE;
import static nl.weeaboo.vn.NovelPrefs.TEXTLOG_PAGE_LIMIT;
import static nl.weeaboo.vn.NovelPrefs.TEXT_SPEED;
import static nl.weeaboo.vn.NovelPrefs.TIMER_IDLE_TIMEOUT;
import static nl.weeaboo.vn.vnds.VNDSUtil.VNDS;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.common.Benchmark;
import nl.weeaboo.common.Dim;
import nl.weeaboo.common.StringUtil;
import nl.weeaboo.filemanager.FileManager;
import nl.weeaboo.game.BaseGame;
import nl.weeaboo.game.DebugPanel;
import nl.weeaboo.game.GameDisplay;
import nl.weeaboo.game.GameLog;
import nl.weeaboo.game.IGameDisplay;
import nl.weeaboo.game.Notifier;
import nl.weeaboo.game.RenderMode;
import nl.weeaboo.game.input.IKeyConfig;
import nl.weeaboo.game.input.UserInput;
import nl.weeaboo.gl.GLManager;
import nl.weeaboo.gl.GLResourceCache;
import nl.weeaboo.gl.shader.ShaderCache;
import nl.weeaboo.gl.text.FontManager;
import nl.weeaboo.gl.text.GLTextRendererStore;
import nl.weeaboo.gl.text.ParagraphRenderer;
import nl.weeaboo.gl.texture.TextureCache;
import nl.weeaboo.lua2.LuaException;
import nl.weeaboo.lua2.io.LuaSerializer;
import nl.weeaboo.nvlist.debug.DebugImagePanel;
import nl.weeaboo.nvlist.debug.DebugLuaPanel;
import nl.weeaboo.nvlist.debug.DebugOutputPanel;
import nl.weeaboo.nvlist.menu.GameMenuFactory;
import nl.weeaboo.settings.IConfig;
import nl.weeaboo.sound.SoundManager;
import nl.weeaboo.vn.IAnalytics;
import nl.weeaboo.vn.IImageState;
import nl.weeaboo.vn.IInput;
import nl.weeaboo.vn.INotifier;
import nl.weeaboo.vn.INovelConfig;
import nl.weeaboo.vn.IPersistentStorage;
import nl.weeaboo.vn.ISaveHandler;
import nl.weeaboo.vn.ISeenLog;
import nl.weeaboo.vn.IStorage;
import nl.weeaboo.vn.ITextState;
import nl.weeaboo.vn.ITimer;
import nl.weeaboo.vn.IVideoState;
import nl.weeaboo.vn.impl.base.BaseLoggingAnalytics;
import nl.weeaboo.vn.impl.base.BaseNovelConfig;
import nl.weeaboo.vn.impl.base.NullAnalytics;
import nl.weeaboo.vn.impl.base.Timer;
import nl.weeaboo.vn.impl.lua.EnvLuaSerializer;
import nl.weeaboo.vn.impl.lua.LuaMediaPreloader;
import nl.weeaboo.vn.impl.nvlist.Analytics;
import nl.weeaboo.vn.impl.nvlist.Globals;
import nl.weeaboo.vn.impl.nvlist.ImageFactory;
import nl.weeaboo.vn.impl.nvlist.ImageFxLib;
import nl.weeaboo.vn.impl.nvlist.ImageState;
import nl.weeaboo.vn.impl.nvlist.InputAdapter;
import nl.weeaboo.vn.impl.nvlist.Movie;
import nl.weeaboo.vn.impl.nvlist.Novel;
import nl.weeaboo.vn.impl.nvlist.NovelNotifier;
import nl.weeaboo.vn.impl.nvlist.Renderer;
import nl.weeaboo.vn.impl.nvlist.SaveHandler;
import nl.weeaboo.vn.impl.nvlist.ScriptLib;
import nl.weeaboo.vn.impl.nvlist.SeenLog;
import nl.weeaboo.vn.impl.nvlist.SoundFactory;
import nl.weeaboo.vn.impl.nvlist.SoundState;
import nl.weeaboo.vn.impl.nvlist.SystemLib;
import nl.weeaboo.vn.impl.nvlist.SystemVars;
import nl.weeaboo.vn.impl.nvlist.TextState;
import nl.weeaboo.vn.impl.nvlist.TweenLib;
import nl.weeaboo.vn.impl.nvlist.VideoFactory;
import nl.weeaboo.vn.impl.nvlist.VideoState;

public class Game extends BaseGame {

	public static final int VERSION_MAJOR = 2;
	public static final int VERSION_MINOR = 1;
	public static final int VERSION = 10000 * VERSION_MAJOR + 100 * VERSION_MINOR;
	public static final String VERSION_STRING = VERSION_MAJOR + "." + VERSION_MINOR;
	
	private final ParagraphRenderer pr;
	
	private Novel novel;
	private LuaSerializer luaSerializer;
	private GameMenuFactory gmf;
	private Renderer renderer;
	private Movie movie;
	
	public Game(IConfig cfg, ExecutorService e, GameDisplay gd, FileManager fm,
			FontManager fontman, TextureCache tc, ShaderCache sc, GLResourceCache rc,
			GLTextRendererStore trs, SoundManager sm, UserInput in, IKeyConfig kc,
			String imageF, String videoF)
	{
		super(cfg, e, gd, fm, fontman, tc, sc, rc, trs, sm, in, kc, imageF, videoF);
		
		gd.setJMenuBar(GameMenuFactory.createPlaceholderJMenuBar()); //Forces GameDisplay to use a JFrame
		gd.setRenderMode(RenderMode.MANUAL);
				
		pr = trs.createParagraphRenderer();
	}

	//Functions
	@Override
	public void stop(final boolean force, final Runnable onStop) {
		super.stop(force, new Runnable() {
			public void run() {				
				if (novel != null) {
					novel.savePersistent();
					generatePreloaderData(); //Generate a preloader info from analytics					
					novel.reset();
					novel = null;
				}
				if (gmf != null) {
					gmf.dispose();
					gmf = null;
				}
				
				if (onStop != null) {
					onStop.run();
				}				
			}
		});
	}
	
	@Override
	public void start() {		
		IConfig config = getConfig();		
		if (VERSION_STRING.compareTo(config.get(ENGINE_MIN_VERSION)) < 0) {
			//Our version number is too old to run the game
			AwtUtil.showError(String.format( "NVList version number (%s) " +
				"is below the minimum acceptable version for this game (%s)",
				VERSION_STRING, config.get(ENGINE_MIN_VERSION)));			
		}
		
		if (gmf != null) {
			gmf.dispose();
		}
		gmf = new GameMenuFactory(this);		
		getDisplay().setJMenuBar(gmf.createJMenuBar());
		
		FileManager fm = getFileManager();
		TextureCache texCache = getTextureCache();
		GLResourceCache resCache = getGLResourceCache();
		ShaderCache shCache = getShaderCache();		
		GLTextRendererStore trStore = getTextRendererStore();
		SoundManager sm = getSoundManager();
		INovelConfig novelConfig = new BaseNovelConfig(config.get(TITLE), config.get(WIDTH), config.get(HEIGHT));
		Dim nvlSize = new Dim(novelConfig.getWidth(), novelConfig.getHeight());
				
		NovelNotifier notifier = new NovelNotifier(getNotifier());
		SaveHandler saveHandler = new SaveHandler(fm, notifier);
		
		IPersistentStorage sysVars = new SystemVars(fm, "sysvars.bin", notifier);
		try {
			sysVars.load();
		} catch (IOException ioe) {
			notifier.d("Error loading sysVars", ioe);
			try { sysVars.save(); } catch (IOException e) { }
		}
		
		ITimer timer = new Timer();
		try {
			timer.load(sysVars);
		} catch (IOException ioe) {
			notifier.d("Error loading timer", ioe);
			try { timer.save(sysVars); } catch (IOException e) { }
		}
		
		ISeenLog seenLog = new SeenLog(fm, "seen.bin");
		try {
			seenLog.load();
		} catch (IOException ioe) {
			notifier.d("Error loading seenLog", ioe);
			try { seenLog.save(); } catch (IOException e) { }
		}
				
		IAnalytics an;
		if (!isDebug()) {
			an = new NullAnalytics();
		} else {
			an = new Analytics(fm, "analytics.bin", notifier);
			try {
				an.load();
			} catch (IOException ioe) {
				notifier.d("Error loading analytics", ioe);
				try { an.save(); } catch (IOException e) { }
			}
		}
				
		SystemLib syslib = new SystemLib(this);
		ImageFactory imgfac = new ImageFactory(texCache, shCache, trStore,
				an, seenLog, notifier, syslib.isTouchScreen(), nvlSize.w, nvlSize.h);
		ImageFxLib fxlib = new ImageFxLib(imgfac);
		SoundFactory sndfac = new SoundFactory(sm, an, seenLog, notifier);
		VideoFactory vidfac = new VideoFactory(fm, texCache, resCache, seenLog, notifier);		
		ScriptLib scrlib = new ScriptLib(fm, notifier);
		TweenLib tweenLib = new TweenLib(imgfac, notifier);
		
		if (isDebug() && !config.get(VNDS)) {
			imgfac.setCheckFileExt(true);
			sndfac.setCheckFileExt(true);
			vidfac.setCheckFileExt(true);
		}
		
		ImageState is = new ImageState(nvlSize.w, nvlSize.h);		
		SoundState ss = new SoundState(sndfac);
		VideoState vs = new VideoState();
		TextState ts = new TextState();
		IInput in = new InputAdapter(getInput());	
		IStorage globals = new Globals();
		
		novel = new Novel(novelConfig, imgfac, is, fxlib, sndfac, ss, vidfac, vs, ts,
				notifier, in, syslib, saveHandler, scrlib, tweenLib, sysVars, globals,
				seenLog, an, timer,
				fm, getKeyConfig());
		if (config.get(VNDS)) {
			novel.setBootstrapScripts("builtin/vnds/main.lua");
		}
        luaSerializer = new EnvLuaSerializer();
        saveHandler.setNovel(novel, luaSerializer);
        
		super.start();
        
		restart("main");
		
		onConfigPropertiesChanged(); //Needs to be called again now novel is initialized		
	}
	
	public void restart() {
		restart("titlescreen");
	}
	protected void restart(final String mainFunc) {		
		novel.restart(luaSerializer, mainFunc);

		onConfigPropertiesChanged();
	}
	
	@Override
	public boolean update(UserInput input, float dt) {
		boolean changed = super.update(input, dt);

		IGameDisplay display = getDisplay();
		boolean allowMenuBarToggle = display.isEmbedded() || display.isFullscreen();
		
		if (display.isMenuBarVisible()) {
			if (allowMenuBarToggle
				&& (input.consumeMouse() || display.isFullscreenExclusive()))
			{
				display.setMenuBarVisible(false);
			}
		} else if (!allowMenuBarToggle) {
			if (display.isFullscreenExclusive()) {
				display.setFullscreen(false);
			}
			display.setMenuBarVisible(true);
		}
		
		changed |= novel.update();
		
		if (novel.getInput().consumeCancel()) {
			if (display.isMenuBarVisible() && allowMenuBarToggle) {
				display.setMenuBarVisible(false);
			} else {
				if (display.isFullscreenExclusive()) {
					display.setFullscreen(false);
				}

				GameMenuFactory gameMenu = new GameMenuFactory(this);
				display.setJMenuBar(gameMenu.createJMenuBar());
				display.setMenuBarVisible(true);
			}
		}
		
		if (isDebug()) {
			Notifier ntf = getNotifier();

			if (input.consumeKey(KeyEvent.VK_MULTIPLY)) {
				int a = 0;
				a = 0 / a; //Boom shakalaka
			}
			
			ISaveHandler sh = novel.getSaveHandler();
			if (input.consumeKey(KeyEvent.VK_ADD)) {
				try {
					Benchmark.tick();
					int slot = sh.getQuickSaveSlot(1);
					String filename = String.format("save-%03d.sav", slot);
					sh.save(slot, null, null);
					long bytes = (getFileManager().getFileExists(filename) ? getFileManager().getFileSize(filename) : 0);
					ntf.addMessage(this, String.format("Quicksave took %s (%s)",
							StringUtil.formatTime(Benchmark.tock(false), TimeUnit.NANOSECONDS),
							StringUtil.formatMemoryAmount(bytes)));					
				} catch (Exception e) {
					GameLog.w("Error quicksaving", e);
				}
			} else if (input.consumeKey(KeyEvent.VK_SUBTRACT)) {
				try {
					int slot = sh.getQuickSaveSlot(1);
					novel.getSaveHandler().load(slot, null);
				} catch (Exception e) {
					GameLog.w("Error quickloading", e);
				}
			}
			
			if (input.consumeKey(KeyEvent.VK_F2)) {
				novel.printStackTrace(System.out);
			} else if (input.consumeKey(KeyEvent.VK_F3)) {
				ntf.addMessage(this, "Generating preloader data");
				generatePreloaderData();
			} else if (input.consumeKey(KeyEvent.VK_F5)) {
				//In-place reload in some way???
			}
		}
		
		return changed;
	}
	
	@Override
	public void draw(GLManager glm) {
		IImageState is = novel.getImageState();
		IVideoState vs = novel.getVideoState();
		
		if (vs.isBlocking()) {
			Movie movie = (Movie)vs.getBlocking();
			movie.draw(glm, getWidth(), getHeight());
		} else {
			if (renderer == null) {
				renderer = new Renderer(glm, pr, getWidth(), getHeight(),
						getRealX(), getRealY(), getRealW(), getRealH(),
						getScreenW(), getScreenH());
			}
			is.draw(renderer);
	        renderer.render(null);
			renderer.reset();
			renderer.onFrameRenderDone();
		}
		
		super.draw(glm);
	}
	
	@Override
	public void onConfigPropertiesChanged() {
		super.onConfigPropertiesChanged();

		IConfig config = getConfig();
		
		if (novel != null) {
			INotifier ntf = novel.getNotifier();
			
			double effectSpeed = config.get(EFFECT_SPEED);
			novel.setEffectSpeed(effectSpeed, 8 * effectSpeed);
			
			ITextState ts = novel.getTextState();
			if (ts != null) {
				ts.setBaseTextSpeed(config.get(TEXT_SPEED));
				ts.getTextLog().setPageLimit(config.get(TEXTLOG_PAGE_LIMIT));
			}
			
			ITimer timer = novel.getTimer();
			if (timer != null) {
				timer.setIdleTimeout(config.get(TIMER_IDLE_TIMEOUT));
			}
			
			LuaMediaPreloader preloader = novel.getPreloader();
			if (preloader != null) {
				preloader.setLookAhead(config.get(PRELOADER_LOOK_AHEAD));
				preloader.setMaxItemsPerLine(config.get(PRELOADER_MAX_PER_LINE));
			}
			
			novel.setScriptDebug(isDebug());
			try {
				novel.setAutoRead(config.get(AUTO_READ),
						60 * config.get(AUTO_READ_WAIT) / 1000);
			} catch (LuaException e) {
				ntf.w("Error occurred when changing auto read", e);
			}
			
			/*
			 * LightNVL volume settings aren't used, we instead use the ones in game-core.
			 * Because of this, we don't need to pass these settings to Novel.
			 */
		}
		
		getDisplay().repaint();
	}
	
	@Override
	protected DebugPanel createDebugPanel() {
		DebugPanel debugPanel = super.createDebugPanel();
		debugPanel.addTab("Lua", new DebugLuaPanel(this, getNovel()));
		debugPanel.addTab("Image", new DebugImagePanel(this, getNovel()));
		debugPanel.addTab("Log", new DebugOutputPanel(this, getNovel()));
		return debugPanel;
	}	
	
	protected void generatePreloaderData() {
		IAnalytics an = novel.getAnalytics();
		if (an instanceof BaseLoggingAnalytics) {			
			BaseLoggingAnalytics ba = (BaseLoggingAnalytics)an;
			try {
				ba.optimizeLog(true);
			} catch (IOException ioe) {
				GameLog.w("Error dumping analytics", ioe);
			}
		}		
	}
	
	//Getters
	public Novel getNovel() { return novel; }
	
	//Setters
	@Override
	public void setScreenBounds(int rx, int ry, int rw, int rh, int sw, int sh) {
		if (rx != getRealX()   || ry != getRealY() || rw != getRealW()   || rh != getRealH()
				|| sw != getScreenW() || sh != getScreenH())
		{
			super.setScreenBounds(rx, ry, rw, rh, sw, sh);
						
			renderer = null;
		}
	}
	
	@Override
	public void setImageFolder(String folder, int w, int h) throws IOException {
		super.setImageFolder(folder, w, h);
		
		if (novel != null) {
			ImageFactory imgfac = (ImageFactory)novel.getImageFactory();
			imgfac.setImageSize(w, h);
		}		
	}
	
	@Override
	public void setVideoFolder(String folder, int w, int h) throws IOException {
		super.setVideoFolder(folder, w, h);
		
		if (novel != null) {
			VideoFactory vfac = (VideoFactory)novel.getVideoFactory();
			vfac.setVideoFolder(folder, w, h);
		}
	}
	
	public void setMovie(Movie m) {
		if (movie != m) {
			if (movie != null) {
				movie.stop();
			}
			movie = m;
		}
	}
	
}
