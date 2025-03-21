/*
Copyright 2014 Lisovik Denisckyberlis@gmail.com
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
package org.cyberlis.pyloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.cyberlis.dataloaders.PluginDataFile;
import org.python.util.PythonInterpreter;


/**
*/
public class PythonPlugin extends PluginBase {
private boolean isEnabled = false;
    private boolean initialized = false;
    private PluginLoader loader = null;
    private Server server = null;
    private File file = null;
    private PluginDescriptionFile description = null;
    private File dataFolder = null;
    //private ClassLoader classLoader = null;
    private Configuration config = null;
    private boolean naggable = true;
    private FileConfiguration newConfig = null;
    private File configFile = null;
    private PluginLogger logger = null;
    private PluginDataFile dataFile = null; //data file used for retrieving resources

    /**
     * interpreter that was used to load this plugin.
     */
    PythonInterpreter interp;

    /**
     * Returns the folder that the plugin data's files are located in. The
     * folder might not yet exist.
     *
     * @return data folder
     */
	@Override
    public File getDataFolder() {
        return dataFolder;
    }

    /**
     * Gets the associated PluginLoader responsible for this plugin
     *
     * @return PluginLoader that controls this plugin
     */
    public final PluginLoader getPluginLoader() {
        return loader;
    }

    /**
     * Returns the Server instance currently running this plugin
     *
     * @return Server running this plugin
     */
    public final Server getServer() {
        return server;
    }

    /**
     * Returns a value indicating whether or not this plugin is currently enabled
     *
     * @return true if this plugin is enabled, otherwise false
     */
    public final boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Returns the file which contains this plugin
     *
     * @return File containing this plugin
     */
    protected File getFile() {
        return file;
    }

    /**
     * Returns the plugin.yaml file containing the details for this plugin
     *
     * @return Contents of the plugin.yaml file
     */
    public PluginDescriptionFile getDescription() {
        return description;
    }

    /**
     * Returns the main configuration located at
     * <plugin name>/config.yml and loads the file. If the configuration file
     * does not exist and it cannot be loaded, no error will be emitted and
     * the configuration file will have no values.
     *
     * @return The configuration.
     * @deprecated See the new
     */
    @Deprecated
    public Configuration getConfiguration() {
        if (config == null) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
        return config;
    }

    public FileConfiguration getConfig() {
        if (newConfig == null) {
            reloadConfig();
        }
        return newConfig;
    }

    /**
     * Sets the enabled state of this plugin
     *
     * @param enabled true if enabled, otherwise false
     */
    protected void setEnabled(final boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;

            if (isEnabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    /**
     * Initializes this plugin with the given variables.
     *
     * This method should never be called manually.
     *
     * @param loader PluginLoader that is responsible for this plugin
     * @param server Server instance that is running this plugin
     * @param description PluginDescriptionFile containing metadata on this plugin
     * @param dataFolder Folder containing the plugin's data
     * @param file File containing this plugin
     */
    protected final void initialize(PluginLoader loader, Server server,
            PluginDescriptionFile description, File dataFolder, File file ) { //,
            //ClassLoader classLoader) {
        if (!initialized) {
            this.initialized = true;
            this.loader = loader;
            this.server = server;
            this.file = file;
            this.description = description;
            this.dataFolder = dataFolder;
            //this.classLoader = classLoader;
            this.config = YamlConfiguration.loadConfiguration(new File(dataFolder, "config.yml"));
            getServer().getLogger().info("Plugin " + getDescription().getFullName() + " will be initialized");
        }
    }

    /**
     * Provides a list of all classes that should be persisted in the database
     *
     * @return List of Classes that are Ebeans
     */
    public List<Class<?>> getDatabaseClasses() {
        return new ArrayList<Class<?>>();
    }


    /**
     * Gets the initialization status of this plugin
     *
     * @return true if this plugin is initialized, otherwise false
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    /**
     * Gets the command with the given name, specific to this plugin
     *
     * @param name Name or alias of the command
     * @return PluginCommand if found, otherwise null
     */
    public PluginCommand getCommand(String name) {
        String alias = name.toLowerCase();
        PluginCommand command = getServer().getPluginCommand(alias);

        if ((command != null) && (command.getPlugin() != this)) {
            command = getServer().getPluginCommand(getDescription().getName().toLowerCase() + ":" + alias);
        }

        if ((command != null) && (command.getPlugin() == this)) {
            return command;
        } else {
            return null;
        }
    }

    /**
     * Overridable, is called after all plugins have been instantiated.
     */
    public void onLoad() {}

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        getServer().getLogger().severe("Plugin " + getDescription().getFullName() + " does not contain any generators that may be used in the default world!");
        return null;
    }

    public final boolean isNaggable() {
        return naggable;
    }

    public final void setNaggable(boolean canNag) {
        this.naggable = canNag;
    }

    public Logger getLogger() {
        if (logger == null) {
            logger = new PluginLogger(this);
        }
        return logger;
    }

    @Override
    public String toString() {
        return getDescription().getFullName();
    }

    public void onEnable() {
        getServer().getLogger().info("Plugin " + getDescription().getFullName() + " enabled");
     }

    public void onDisable() { }

    @Override
    public InputStream getResource(String filename) {
        if(filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        try {
            dataFile.reload();
            return dataFile.getStream(filename);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        // TODO is this necessary for use with PluginDataFile?
        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + getFile());
        }

        File outFile = new File(getDataFolder(), resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(getDataFolder(), resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                OutputStream out = new FileOutputStream(outFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(JavaPlugin.class.getName()).log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    public void reloadConfig() {
        if (configFile != null) {
            newConfig = YamlConfiguration.loadConfiguration(configFile);

            InputStream defConfigStream = getResource("config.yml");
            if (defConfigStream != null) {
            	Reader targetReader = new InputStreamReader(defConfigStream);
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(targetReader);

                newConfig.setDefaults(defConfig);
            }
        } else {
            InputStream defConfigStream = getResource("config.yml");
            if (defConfigStream != null) {
            	Reader targetReader = new InputStreamReader(defConfigStream);
                newConfig = YamlConfiguration.loadConfiguration(targetReader);
            }
        }
    }

    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            Logger.getLogger(PythonPlugin.class.getName()).log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        saveResource("config.yml", false);
    }

    protected void setDataFile(PluginDataFile file) {
        this.dataFile = file;
    }

    /* @Override
    public String getName() {
        return "PythonPlugin";
    } */

    /*
	@Override
    public final io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> getLifecycleManager() {
        Object lifecycleEventManager = null;
        // private final io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> lifecycleEventManager = org.bukkit.Bukkit
        //        .getUnsafe().createPluginLifecycleEventManager(this, () -> this.allowsLifecycleRegistration);

        return lifecycleEventManager;
    } */

    public final PluginDescriptionFile getPluginMeta() {
        return this.description;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd,
            String alias, String[] args) {
        return null;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        // Iris.debug("Biome Provider Called for " + worldName + " using ID: " + id);
        return null; //super.getDefaultBiomeProvider(worldName, id);
    }
}
