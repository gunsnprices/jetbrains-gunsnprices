package com.gunsnprices.ide;

import com.gunsnprices.ide.core.Output;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/** Application-level settings, stored in {@code gunsnprices.xml}. */
@Service(Service.Level.APP)
@State(name = "GunsNPricesSettings", storages = @Storage("gunsnprices.xml"))
public final class GunsNPricesSettings implements PersistentStateComponent<GunsNPricesSettings.State> {

    /** Notified after the settings page applies changes. */
    public interface Listener {
        void settingsChanged();
    }

    /** Application message bus topic for {@link Listener}. */
    public static final Topic<Listener> CHANGED = Topic.create("GunsNPrices settings changed", Listener.class);

    /** The stored values. Public fields are serialized by the platform. */
    public static final class State {
        /** Link calibers to their GunsNPrices price pages and add the URLs to JSON and CSV. Off by default. */
        public boolean links = false;
        /** Write "×" as "x" in caliber names. */
        public boolean asciiCaliberNames = false;
    }

    private State state = new State();

    public static GunsNPricesSettings getInstance() {
        return ApplicationManager.getApplication().getService(GunsNPricesSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
    }

    public boolean links() {
        return state.links;
    }

    public void setLinks(boolean value) {
        state.links = value;
    }

    public boolean asciiCaliberNames() {
        return state.asciiCaliberNames;
    }

    public void setAsciiCaliberNames(boolean value) {
        state.asciiCaliberNames = value;
    }

    /** The output options for these settings. */
    public Output.Options outputOptions() {
        return new Output.Options(state.links, state.asciiCaliberNames);
    }
}
