package nl.weeaboo.vn;

import nl.weeaboo.common.Checks;
import nl.weeaboo.lua2.LuaRunState;
import nl.weeaboo.vn.BasicPartRegistry;
import nl.weeaboo.vn.core.impl.ContextManager;
import nl.weeaboo.vn.core.impl.DefaultEnvironment;
import nl.weeaboo.vn.core.impl.DefaultNotifier;
import nl.weeaboo.vn.core.impl.EnvironmentBuilder;
import nl.weeaboo.vn.script.IScriptLoader;
import nl.weeaboo.vn.script.lua.LuaScriptEnv;
import nl.weeaboo.vn.script.lua.LuaScriptLoader;
import nl.weeaboo.vn.script.lua.LuaTestUtil;

public class TestEnvironment extends DefaultEnvironment {

    public final LuaScriptEnv scriptEnv;
    public final IScriptLoader scriptLoader;

    private TestEnvironment(EnvironmentBuilder b, LuaScriptEnv scriptEnv) {
        super(b);

        Checks.checkArgument(b.contextManager instanceof ContextManager,
                "ContextManager must be an instance of " + ContextManager.class.getName());

        this.scriptEnv = scriptEnv;
        this.scriptLoader = scriptEnv.getScriptLoader();
    }

    public static TestEnvironment newInstance() {
        EnvironmentBuilder b = new EnvironmentBuilder();

        b.partRegistry = new BasicPartRegistry();
        b.notifier = new DefaultNotifier();
        b.fileSystem = TestFileSystem.newInstance();
        b.renderEnv = TestUtil.BASIC_ENV;
        b.systemEventHandler = new TestSystemEventHandler();

        LuaRunState runState = LuaTestUtil.newRunState();
        LuaScriptLoader scriptLoader = LuaTestUtil.newScriptLoader(b.fileSystem);

        LuaScriptEnv scriptEnv = new LuaScriptEnv(runState, scriptLoader);

        TestContextBuilder contextBuilder = new TestContextBuilder(scriptEnv);
        b.contextManager = new ContextManager(contextBuilder);
        b.scriptLoader = scriptLoader;

        return new TestEnvironment(b, scriptEnv);
    }

    @Override
    public void destroy() {
        if (!isDestroyed()) {
            super.destroy();

            scriptEnv.getRunState().destroy();
        }
    }

    @Override
    public ContextManager getContextManager() {
        return (ContextManager)super.getContextManager();
    }

}
