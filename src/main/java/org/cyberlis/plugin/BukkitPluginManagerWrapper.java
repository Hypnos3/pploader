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
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPluginLoader;

public class BukkitPluginManagerWrapper implements PluginManagerWrapper {
    private static Map<Pattern, PluginLoader> fileAssociations = null;
    private static JavaPluginLoader javapluginloader = null;
    private static Map<String, ?> javaLoaders = null;

    @Override
    public Plugin loadPlugin(File file) {
        try {
            return Bukkit.getPluginManager().loadPlugin(file);
        } catch (InvalidPluginException | InvalidDescriptionException e) {
            throw new IllegalStateException("Failed to load plugin", e);
        }
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        Bukkit.getPluginManager().enablePlugin(plugin);
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        Bukkit.getPluginManager().disablePlugin(plugin);
    }

    @Override
    public Plugin getPlugin(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }

    @Override
    public Plugin[] getPlugins() {
        return Bukkit.getPluginManager().getPlugins();
    }

    @Override
    public void registerInterface(Class<? extends PluginLoader> loader) throws IllegalArgumentException {
        Bukkit.getPluginManager().registerInterface(loader);
    }

    @Override
    public void callEvent(Event event) throws IllegalStateException {
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Returns whether or not timing code should be used for event calls
     *
     * @return True if event timings are to be used
     */
    @Override
    public boolean useTimings() {
        return Bukkit.getPluginManager().useTimings();
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


    /**
     * Set a private value. This version guesses the class with getClass().
     * @param obj object to set the field in
     * @param fieldName name of field to set
     * @param newValue new value to set the field to
     * @throws IllegalArgumentException can be thrown when setting the field
     * @throws IllegalAccessException can be thrown when setting the field
     * @throws SecurityException can be thrown when retrieving the field object
     * @throws NoSuchFieldException can be thrown when retrieving the field object
     */
    public static void setPrivateValue(Object obj, String fieldName, Object newValue) throws IllegalArgumentException, SecurityException, IllegalAccessException, NoSuchFieldException {
        setPrivateValue(obj.getClass(), obj, fieldName, newValue);
    }

    /**
     * Set a private value.
     * @param containingClass class containing the field
     * @param obj object to set the field in
     * @param fieldName name of field to set
     * @param newValue new value to set the field to
     * @throws IllegalArgumentException can be thrown when setting the field
     * @throws IllegalAccessException can be thrown when setting the field
     * @throws SecurityException can be thrown when retrieving the field object
     * @throws NoSuchFieldException can be thrown when retrieving the field object
     */
    public static void setPrivateValue(Class<?> containingClass, Object obj, String fieldName, Object newValue) throws IllegalArgumentException, IllegalAccessException, SecurityException, NoSuchFieldException {
        Field field = containingClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }

    private static void printerr(String cause, String issue) {
        if (issue != null) {
            System.err.println("PythonLoader: " + cause + ", " + issue);
        }
    }
}