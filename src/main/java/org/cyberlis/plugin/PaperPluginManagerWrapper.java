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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPluginLoader;

public class PaperPluginManagerWrapper implements PluginManagerWrapper {
    private final Object instance;
    private final Object manager;
    private static Map<Pattern, PluginLoader> fileAssociations = null;
    private static JavaPluginLoader javapluginloader = null;
    private static Map<String, ?> javaLoaders = null;

    public PaperPluginManagerWrapper() {
        try {
            Class<?> pluginManagerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            this.manager = pluginManagerClass.getMethod("getInstance").invoke(null);
            Field instanceManagerF = this.manager.getClass().getDeclaredField("instanceManager");
            instanceManagerF.setAccessible(true);
            this.instance = instanceManagerF.get(instanceManagerF);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to find PaperPluginManagerImpl class", e);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to get PaperPluginManagerImpl instance", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to get instanceManager field", e);
        }
    }

    @Override
    public Plugin loadPlugin(File file) {
        try {
            Method loadMethod = this.instance.getClass().getMethod("loadPlugin", Path.class);
            loadMethod.setAccessible(true);
            return (Plugin) loadMethod.invoke(this.instance, file.toPath());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        try {
            Method enableMethod = this.instance.getClass().getMethod("enablePlugin", Plugin.class);
            enableMethod.setAccessible(true);
            enableMethod.invoke(this.instance, plugin);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
     }

    @Override
    public void disablePlugin(Plugin plugin) {
        try {
            Method disableMethod = this.instance.getClass().getMethod("disablePlugin", Plugin.class);
            disableMethod.setAccessible(true);
            disableMethod.invoke(this.instance, plugin);

            Field lookupNamesField = this.instance.getClass().getField("lookupNames");
            Map<String, Plugin> map = (Map<String, Plugin>)lookupNamesField.get(this.instance);
            Field pluginsField = this.instance.getClass().getField("plugins");
            List<Plugin> list  = (List<Plugin>)pluginsField.get(this.instance);

            map.remove(plugin.getName().toLowerCase());
            list.remove(plugin);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException |  NoSuchFieldException e) {
            e.printStackTrace();
        }

        ClassLoader classLoader = plugin.getClass().getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            try {
                ((URLClassLoader) classLoader).close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close class loader", e);
            }
        }

        System.gc();
        System.runFinalization();
    }

    @Override
    public Plugin getPlugin(String name) {
        try {
            Method getPluginMethod = this.instance.getClass().getMethod("getPlugin", String.class);
            getPluginMethod.setAccessible(true);
            return (Plugin) getPluginMethod.invoke(this.instance, name);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Plugin[] getPlugins() {
        try {
            Method getPluginMethod = this.instance.getClass().getMethod("getPlugins");
            getPluginMethod.setAccessible(true);
            return (Plugin[]) getPluginMethod.invoke(this.instance);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void registerInterface(Class<? extends PluginLoader> loader) throws IllegalArgumentException {
        try {
            Method registerInterfaceMethod = this.instance.getClass().getMethod("registerInterface", Class.class);
            registerInterfaceMethod.setAccessible(true);
            registerInterfaceMethod.invoke(this.instance, loader);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void callEvent(Event event) throws IllegalStateException {
        try {
            Method callEventMethod = this.manager.getClass().getMethod("callEvent", Event.class);
            callEventMethod.setAccessible(true);
            callEventMethod.invoke(this.instance, event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean useTimings() {
        try {
            Method useTimingsMethod = this.instance.getClass().getMethod("useTimings");
            useTimingsMethod.setAccessible(true);
            return (boolean)useTimingsMethod.invoke(this.manager);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * Retrieve SimplePluginManager.fileAssociations. inform the user if they're too cool for us
     * (ie, they're using a different plugin manager)
     * @param pm PluginManager to attempt to retrieve fileAssociations from
     * @param errorstr string to print if we fail when we print reason we failed
     * @return fileAssociations map
     */
    @SuppressWarnings("unchecked")
    public Map<Pattern, PluginLoader> getFileAssociations(String errorstr) {
        if (fileAssociations != null) {
            return fileAssociations;
        }
        PluginManager pm = Bukkit.getPluginManager();
        Class<?> pmclass = null;
        try {
            pmclass = Class.forName("org.bukkit.plugin.SimplePluginManager");
        } catch (ClassNotFoundException e) {
            printerr("Did not find SimplePluginManager", errorstr);
        } catch (Throwable t) {
            printerr("Error while checking for SimplePluginManager", errorstr);
            t.printStackTrace();
        }

        Field fieldFileAssociations = null;
        if (pmclass != null) {
            try {
                fieldFileAssociations = pmclass.getDeclaredField("fileAssociations");
            } catch (SecurityException e) {
                printerr("SecurityException while checking for fileAssociations field in SimplePluginManager", errorstr);
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                printerr("SimplePluginManager does not have fileAssociations field", errorstr);
            } catch (Throwable t) {
                printerr("Error while checking for fileAssociations field in SimplePluginManager", errorstr);
                t.printStackTrace();
            }
        }

        if (fieldFileAssociations != null) {
            try {
                fieldFileAssociations.setAccessible(true);
                fileAssociations = (Map<Pattern, PluginLoader>) fieldFileAssociations.get(pm);
            } catch (ClassCastException e) {
                printerr("fileAssociations is not of type Map<Pattern, PluginLoader>", errorstr);

            } catch (Throwable t) {
                printerr("Error while getting fileAssociations from PluginManager", errorstr);
                t.printStackTrace();
            }
        }
        return fileAssociations;
    }

    /**
     * Retrieve JavaPluginLoader from SimplePluginManager file associations
     * @param pm plugin manager
     * @return java plugin loader if found
     */
    public JavaPluginLoader getJavaPluginLoader() {
        if (javapluginloader != null) {
            return javapluginloader;
        }

        getFileAssociations(null);

        for (Entry<Pattern, PluginLoader> entry : fileAssociations.entrySet()) {
            if (entry.getKey().pattern().equals("\\.jar$")) {
                javapluginloader = (JavaPluginLoader) entry.getValue();
            }
        }

        return javapluginloader;
    }

    /**
     * Retrieve loaders field from JavaPluginLoader instance
     * @param pm plugin manager to search for JavaPluginLoader in (if necessary)
     * @return loaders field retrieved
     */
    @SuppressWarnings("unchecked")
    public  Map<String, ?> getJavaLoaders() {
        if (javaLoaders != null) {
            return javaLoaders;
        }

        getJavaPluginLoader();
        if (javapluginloader == null) {
            return null;
        }

        try {
            Field fieldLoaders = JavaPluginLoader.class.getDeclaredField("loaders");
            fieldLoaders.setAccessible(true);

            javaLoaders = (Map<String, ?>) fieldLoaders.get(javapluginloader);
            return javaLoaders;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /**
     * Use JavaPluginLoader.loaders field to determine if JavaPluginLoader has loaded a plugin (false if unable to determine)
     * @param pm plugin manager to retrieve JavaPluginLoader instance from, if necessary
     * @param name name of plugin to search for
     * @return whether plugin is loaded
     */
    public boolean isJavaPluginLoaded(String name) {
        getJavaLoaders();
        if (javaLoaders == null) {
            return false;
        }
        return javaLoaders.containsKey(name);
    }

    private static void printerr(String cause, String issue) {
        if (issue != null) {
            System.err.println("PythonLoader: " + cause + ", " + issue);
        }
    }
}