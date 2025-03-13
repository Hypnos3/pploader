/*
Copyright 2021 hypnos3@online.com
This file is part of PPLoader.
PPLoader is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

PPLoader is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PPLoader.  If not, see <http://www.gnu.org/licenses/>
*/

package org.cyberlis.plugin;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;

public interface PluginManagerWrapper {

    Plugin loadPlugin(File file);

    void enablePlugin(Plugin plugin);

    void disablePlugin(Plugin plugin);

    Plugin getPlugin(String name);

    Plugin[] getPlugins();

    void registerInterface(Class<? extends PluginLoader> loader) throws IllegalArgumentException;

    Map<Pattern, PluginLoader> getFileAssociations(String errorstr);

    boolean isJavaPluginLoaded(String name);

    public void callEvent(Event event) throws IllegalStateException;

    /**
     * Returns whether or not timing code should be used for event calls
     *
     * @return True if event timings are to be used
     */
    public boolean useTimings();
}