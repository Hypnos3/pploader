package org.cyberlis.plugin;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
 
public class PaperPluginManagerWrapper implements PluginManagerWrapper {
    private final Object instance;

    public PaperPluginManagerWrapper() {
        try {
            Class<?> pluginManagerClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Object paperPluginManagerImpl = pluginManagerClass.getMethod("getInstance").invoke(null);
            Field instanceManagerF = paperPluginManagerImpl.getClass().getDeclaredField("instanceManager");
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
}